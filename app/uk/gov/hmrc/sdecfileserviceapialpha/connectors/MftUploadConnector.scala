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

import com.google.inject.ImplementedBy
import org.apache.pekko.util.ByteString
import play.api.libs.json.Reads
import play.api.libs.ws.WSBodyWritables.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.sdecfileserviceapialpha.config.AppConfig
import uk.gov.hmrc.sdecfileserviceapialpha.models.{BoomiErrorBody, MftUploadError, MftUploadSuccess}

import java.nio.file.{Files, Path}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[BoomiMftUploadConnector])
trait MftUploadConnector {

  def upload(
    threadReference: String,
    fileName:        String,
    correlationId:   String,
    file:            Path
  )(implicit hc: HeaderCarrier): Future[Either[MftUploadError, MftUploadSuccess]]

}

@Singleton
class BoomiMftUploadConnector @Inject() (
  http:           HttpClientV2,
  config:         AppConfig,
  tokenConnector: BoomiConnector
)(implicit ec: ExecutionContext)
    extends MftUploadConnector {

  override def upload(
    threadReference: String,
    fileName:        String,
    correlationId:   String,
    file:            Path
  )(implicit hc: HeaderCarrier): Future[Either[MftUploadError, MftUploadSuccess]] =
    tokenConnector.fetchToken().flatMap {
      case Left(error)  => Future.successful(Left(error))
      case Right(token) => send(token, threadReference, fileName, correlationId, file)
    }

  private def send(
    token:           String,
    threadReference: String,
    fileName:        String,
    correlationId:   String,
    file:            Path
  )(implicit hc: HeaderCarrier): Future[Either[MftUploadError, MftUploadSuccess]] =
    for {
      bytes    <- Future(ByteString(Files.readAllBytes(file))) // TODO: Streaming as source?
      response <- http
                    .post(url"${config.boomiBaseUrl}/ws/rest/files/v1/upload?thread=$threadReference")
                    .setHeader(
                      "Authorization"    -> s"Bearer $token",
                      "X-File-Name"      -> fileName,
                      "X-Correlation-Id" -> correlationId,
                      "X-Source-System"  -> config.boomiSourceSystem
                    )
                    .withBody(bytes)
                    .execute[HttpResponse]
    } yield toResult(response, correlationId)

  private def toResult(response: HttpResponse, correlationId: String): Either[MftUploadError, MftUploadSuccess] =
    response.status match {
      case 201 =>
        parse[MftUploadSuccess](response).toRight(unexpected(response))
      case 400 =>
        Left(rejected(response))
      case 502 =>
        Left(deliveryFailed(response, correlationId))
      case _ =>
        Left(unexpected(response))
    }

  private def rejected(response: HttpResponse): MftUploadError =
    (for {
      body    <- parse[BoomiErrorBody](response)
      code    <- body.errorCode
      message <- body.message
    } yield MftUploadError.Rejected(code, message)).getOrElse(unexpected(response))

  private def deliveryFailed(response: HttpResponse, correlationId: String): MftUploadError =
    parse[BoomiErrorBody](response)
      .map(body =>
        MftUploadError.DeliveryFailed(
          body.fileId.getOrElse(correlationId),
          body.message.getOrElse("The file could not be delivered to the MFT server.")
        )
      )
      .getOrElse(unexpected(response))

  private def unexpected(response: HttpResponse): MftUploadError =
    MftUploadError.Unexpected(response.status, response.body)

  private def parse[A: Reads](response: HttpResponse): Option[A] =
    Try(response.json).toOption.flatMap(_.validate[A].asOpt)

}
