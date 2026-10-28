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

package uk.gov.hmrc.sdecfileserviceapialpha.controllers

import play.api.{Logger, Logging}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, ControllerComponents, MultipartFormData, Result}
import play.twirl.api.Html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.sdecfileserviceapialpha.connectors.MftUploadConnector
import uk.gov.hmrc.sdecfileserviceapialpha.models.{MftUploadError, MftUploadSuccess}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MftUploadController @Inject() (
  cc:        ControllerComponents,
  connector: MftUploadConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  private val log = Logger(getClass)

  def upload(threadReference: String): Action[MultipartFormData[TemporaryFile]] =
    Action.async(parse.multipartFormData) { request =>
      request.body.file("file") match {
        case None =>
          Future.successful(
            BadRequest(errorJson("MISSING_FILE", "Attach a file in the form field named 'file'."))
          )
        case Some(filePart) =>
          connector
            .upload(threadReference, filePart.filename, UUID.randomUUID().toString, filePart.ref.path)(HeaderCarrier())
            .map { result =>
              result.left.foreach(error => log.warn(s"MFT upload for $threadReference failed: $error"))
              toResult(result)
            }
      }
    }

  private def toResult(result: Either[MftUploadError, MftUploadSuccess]): Result =
    result match {
      case Right(success) =>
        logger.info("File uploaded to Boomi successfully")
        Created(
          Html(
            s"""
             |<h1>File Uploaded Successfully</h1>
             |<dl>
             |  <dt><b>File ID:</b> ${success.fileId}</dt>
             |  <dt><b>File Name:</b> ${success.fileName}</dt>
             |  <dt><b>Path:</b> ${success.path}</dt>
             |  <dt><b>Size:</b> ${success.sizeBytes} bytes</dt>
             |  <dt><b>Status:</b> ${success.status}</dt>
             |</dl>
             |""".stripMargin
          )
        )
      case Left(MftUploadError.Rejected(code, message))    => BadRequest(errorJson(code, message))
      case Left(MftUploadError.DeliveryFailed(_, message)) => BadGateway(errorJson("MFT_DELIVERY_FAILED", message))
      case Left(MftUploadError.TokenFailed(status))        =>
        BadGateway(errorJson("BOOMI_TOKEN_FAILED", s"Boomi did not give us a token (status $status)."))
      case Left(MftUploadError.Unexpected(status, _)) =>
        BadGateway(errorJson("BOOMI_UNEXPECTED_RESPONSE", s"Boomi answered with status $status."))
    }

  private def errorJson(code: String, message: String): JsObject =
    Json.obj("errorCode" -> code, "message" -> message)

}
