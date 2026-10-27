/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.sdecfileserviceapialpha.connectors

import play.api.libs.ws.WSBodyWritables.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.sdecfileserviceapialpha.config.AppConfig
import uk.gov.hmrc.sdecfileserviceapialpha.models.{BoomiToken, MftUploadError}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class BoomiConnector @Inject() (
  http:   HttpClientV2,
  config: AppConfig
)(implicit ec: ExecutionContext) {

  def fetchToken()(implicit hc: HeaderCarrier): Future[Either[MftUploadError, String]] =
    http
      .post(url"${config.boomiBaseUrl}/token")
      .withBody(
        Map(
          "grant_type"    -> Seq("client_credentials"),
          "client_id"     -> Seq(config.boomiClientId),
          "client_secret" -> Seq(config.boomiClientSecret)
        )
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 =>
            Try(response.json).toOption
              .flatMap(_.validate[BoomiToken].asOpt)
              .map(_.accessToken)
              .toRight(MftUploadError.TokenFailed(response.status))
          case other =>
            Left(MftUploadError.TokenFailed(other))
        }
      }

}
