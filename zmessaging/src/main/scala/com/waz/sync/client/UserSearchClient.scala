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
package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.QualifiedId
import com.waz.service.SearchQuery
import com.waz.sync.client.UserSearchClient.{DefaultLimit, UserSearchResponse}
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.waz.zms.BuildConfig

trait UserSearchClient {
  def search(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[UserSearchResponse]
}

class UserSearchClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends UserSearchClient with CirceJSONSupport {
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import UserSearchClient._
  private implicit val errorResponseDeserializer: RawBodyDeserializer[ErrorResponse] =
    objectFromCirceJsonRawBodyDeserializer[ErrorResponse]

  override def search(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[UserSearchResponse] = {
    verbose(l"search($query, $limit)")
    val params =
      if (BuildConfig.FEDERATION_USER_DISCOVERY && query.hasDomain)
        List("q" -> query.query, "domain" -> query.domain, "size" -> limit.toString)
      else
        List("q" -> query.query, "size" -> limit.toString)
    Request
      .Get(relativePath = SearchPath, queryParameters = params)
      .withResultType[UserSearchResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object UserSearchClient extends DerivedLogTag {
  val SearchPath = "/search/contacts"

  val DefaultLimit = 15

  // Response types

  final case class UserSearchResponse(
    took:      Int,
    found:     Int,
    returned:  Int,
    documents: Seq[UserSearchResponse.User]
  )

  object UserSearchResponse {
    final case class User(
      qualified_id: QualifiedId,
      name:         String,
      handle:       Option[String],
      accent_id:    Option[Int],
      team:         Option[String],
      assets:       Option[Seq[Asset]]
    )

    final case class Asset(key: String, size: String, `type`: String)
  }
}
