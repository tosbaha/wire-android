/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api.impl

import com.waz.ZLog._
import com.waz.api.impl.conversation.BaseConversation
import com.waz.model.{ConvId, ConversationData}
import com.waz.service.tracking.TrackingEventsService
import com.waz.threading.Threading
import com.waz.ui._
import com.waz.utils.RichFutureOpt

class Conversation(override val id: ConvId, val initData: ConversationData = ConversationData.Empty)(implicit ui: UiModule) extends BaseConversation {
  import Threading.Implicits.Background

  def this(data: ConversationData)(implicit ui: UiModule) = this(data.id, data)
  private implicit val logTag: LogTag = logTagFor[Conversation]
  private var convIsOtto = false

  set(initData)
  reload()

  def reload() = {
    verbose(s"load $id")
    ui.zms
      .flatMapFuture { zms =>
        zms.convsContent.convById(id).flatMapSome { conv =>
          TrackingEventsService.isOtto(conv, zms.usersStorage).map(isOtto => (conv, isOtto))
        }
      }.mapSome { case (conv, otto) =>
        set(conv)
        convIsOtto = otto
      }(Threading.Ui)
  }

  if (initData.displayName == "") {
    // force conversation name update
    // XXX: this is a hack for some random errors, sometimes conv has empty name which is never updated
    ui.zms { _.conversations.forceNameUpdate(id) }
  }

  override def isOtto: Boolean = convIsOtto
}

object Conversation {
  implicit object Cached extends UiCached[Conversation, ConvId, ConversationData] {
    override def reload(item: Conversation): Unit = item.reload()
    override def update(item: Conversation, d: ConversationData): Unit = item.set(d)
    override def toUpdateMap(values: Seq[ConversationData]) = values.map(c => c.id -> c)(collection.breakOut)
  }
}
