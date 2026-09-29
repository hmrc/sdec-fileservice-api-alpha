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

package uk.gov.hmrc.sdecfileserviceapialpha.controllers.fileupload

import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.sdecfileserviceapialpha.services.MultipartUploadService
import uk.gov.hmrc.sdecfileserviceapialpha.views.html.{MultipartFileUploadCompletePage, MultipartFileUploadPage}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

final case class InitiateMultipartUploadRequest(
  filename:    String,
  contentType: String,
  fileSize:    Long
)

object InitiateMultipartUploadRequest {

  given Reads[InitiateMultipartUploadRequest] =
    Json.reads[InitiateMultipartUploadRequest]
}

final case class MultipartUploadPart(
  partNumber: Int,
  etag:       String
)

object MultipartUploadPart {

  given Reads[MultipartUploadPart] =
    Json.reads[MultipartUploadPart]
}

final case class CompleteMultipartUploadRequest(
  key:      String,
  uploadId: String,
  parts:    Seq[MultipartUploadPart]
)

object CompleteMultipartUploadRequest {

  given Reads[CompleteMultipartUploadRequest] =
    Json.reads[CompleteMultipartUploadRequest]
}

final case class InitiateMultipartUploadResponse(
  key:      String,
  uploadId: String,
  partSize: Long,
  urls:     Seq[PartUploadUrl]
)

object InitiateMultipartUploadResponse {

  given Writes[InitiateMultipartUploadResponse] =
    Json.writes[InitiateMultipartUploadResponse]
}

final case class PartUploadUrl(
  partNumber: Int,
  url:        String
)

object PartUploadUrl {

  given Writes[PartUploadUrl] =
    Json.writes[PartUploadUrl]
}

class MultipartFileUploader @Inject() (
  mcc:                    MessagesControllerComponents,
  multipartUploadService: MultipartUploadService,
  multipartFileUploadPage: MultipartFileUploadPage,
  multipartFileUploadCompletePage: MultipartFileUploadCompletePage
)(using ExecutionContext)
    extends FrontendController(mcc) {

  /** Display the multipart upload page.
    */
  def showUploadPage: Action[AnyContent] =
    Action { implicit request =>
      Ok(multipartFileUploadPage())
    }

  /** Initiate an S3 multipart upload.
    *
    * The browser sends the filename, content type and file size.
    *
    * Play creates the multipart upload and returns:
    *   - S3 object key
    *   - upload ID
    *   - part size
    *   - presigned URL for each part
    */
  def initiateMultipartUpload: Action[JsValue] =
    Action(parse.json) { implicit request =>
      request.body.validate[InitiateMultipartUploadRequest] match {

        case JsSuccess(uploadRequest, _) =>

          if uploadRequest.filename.trim.isEmpty then {

            BadRequest(
              Json.obj(
                "error" -> "Filename must not be empty"
              )
            )

          } else if uploadRequest.fileSize <= 0 then {

            BadRequest(
              Json.obj(
                "error" -> "File size must be greater than zero"
              )
            )

          } else {

            val result =
              multipartUploadService.initiate(
                filename = uploadRequest.filename,
                contentType = uploadRequest.contentType,
                fileSize = uploadRequest.fileSize
              )

            Ok(Json.toJson(result))
          }

        case JsError(errors) =>

          BadRequest(
            Json.obj(
              "error" -> JsError.toJson(errors)
            )
          )
      }
    }

  /** Complete an S3 multipart upload.
    *
    * The browser sends the object key, upload ID and the ETag returned by S3 for every uploaded part.
    */
  def completeMultipartUpload: Action[JsValue] =
    Action(parse.json) { implicit request =>
      request.body.validate[CompleteMultipartUploadRequest] match {

        case JsSuccess(completeRequest, _) =>

          val result =
            multipartUploadService.complete(
              key = completeRequest.key,
              uploadId = completeRequest.uploadId,
              parts = completeRequest.parts
            )

          Ok(
            Json.obj(
              "key" -> result
            )
          )

        case JsError(errors) =>

          BadRequest(
            Json.obj(
              "error" -> JsError.toJson(errors)
            )
          )
      }
    }

  /** Display the completion page.
    *
    * The filename is passed as a query parameter:
    *
    * /multipart-upload/complete?filename=my-file.txt
    */
  def uploadComplete: Action[AnyContent] =
    Action { implicit request =>
      request.getQueryString("filename") match {

        case Some(filename) =>
          Ok(
            multipartFileUploadCompletePage(filename)
          )

        case None =>
          BadRequest("Missing filename")
      }
    }
}
