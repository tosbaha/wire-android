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
package com.waz.service.call

import com.sun.jna.Pointer
import com.waz.model.{ConvId, GenericMessage, UserId}
import com.waz.service.ZMessaging.clock
import com.waz.service.call.Avs.AvsClosedReason
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.CallInfo.CallState.{SelfConnected, SelfJoining}
import com.waz.service.call.CallInfo.{CallState, EndedReason}
import com.waz.utils.events.{ClockSignal, Signal}
import org.threeten.bp.Duration.between
import org.threeten.bp.{Duration, Instant}
import scala.concurrent.duration._

case class CallInfo(convId:             ConvId,
                    account:            UserId,
                    isGroup:            Boolean,
                    caller:             UserId,
                    state:              Option[CallState]                 = None,
                    prevState:          Option[CallState]                 = None,
                    others:             Set[UserId]                       = Set.empty,
                    maxParticipants:    Int                               = 0, //maintains the largest number of users that were ever in the call (for tracking)
                    muted:              Boolean                           = false,
                    isCbrEnabled:       Boolean                           = false,
                    startedAsVideoCall: Boolean                           = false,
                    videoSendState:     VideoState                        = VideoState.Stopped,
                    videoReceiveState:  Map[UserId, VideoState]           = Map.empty,
                    startTime:          Instant                           = clock.instant(), //the time we start/receive a call - always the time at which the call info object was created
                    joinedTime:         Option[Instant]                   = None, //the time the call was joined, if any
                    estabTime:          Option[Instant]                   = None, //the time that a joined call was established, if any
                    endTime:            Option[Instant]                   = None,
                    endReason:          Option[EndedReason]               = None,
                    outstandingMsg:     Option[(GenericMessage, Pointer)] = None) { //Any messages we were unable to send due to conv degradation

  override def toString: String =
    s"""
       |CallInfo:
       | convId:             $convId
       | account:            $account
       | isGroup:            $isGroup
       | caller:             $caller
       | state:              $state
       | prevState:          $prevState
       | others:             $others
       | maxParticipants:    $maxParticipants
       | muted:              $muted
       | isCbrEnabled:       $isCbrEnabled
       | startedAsVideoCall: $startedAsVideoCall
       | videoSendState:     $videoSendState
       | videoReceiveState:  $videoReceiveState
       | startTime:          $startTime
       | estabTime:          $estabTime
       | endTime:            $endTime
       | endedReason:        $endReason
       | hasOutstandingMsg:  ${outstandingMsg.isDefined}
    """.stripMargin

  def updateState(callState: CallState): CallInfo = {
    val withState = copy(state = Some(callState), prevState = this.state)
    callState match {
      case SelfJoining => withState.copy(joinedTime = Some(clock.instant()))
      case SelfConnected => withState.copy(estabTime = Some(clock.instant()))
      case _ => withState
    }
  }

  val duration = estabTime match {
    case Some(est) => ClockSignal(1.second).map(_ => Option(between(est, clock.instant())))
    case None      => Signal.const(Option.empty[Duration])
  }

  val durationFormatted = duration.map {
    case Some(d) =>
      val seconds = ((d.toMillis / 1000) % 60).toInt
      val minutes = ((d.toMillis / 1000) / 60).toInt
      f"$minutes%02d:$seconds%02d"
    case None => ""
  }

}

object CallInfo {

  sealed trait CallState

  object CallState {

    case object SelfCalling    extends CallState
    case object OtherCalling   extends CallState
    case object SelfJoining    extends CallState
    case object SelfConnected  extends CallState
    case object Ongoing        extends CallState
  }

  sealed trait EndedReason
  object EndedReason {
    case object SelfEnded extends EndedReason
    case object OtherEnded extends EndedReason
    case object GSMInterrupted extends EndedReason
    case class Dropped(avsReason: AvsClosedReason) extends EndedReason
  }
}