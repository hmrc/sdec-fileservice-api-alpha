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

import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.sdecfileserviceapialpha.models.S3PostData
import uk.gov.hmrc.sdecfileserviceapialpha.views.html.{FileUploadCompletePage, FileUploadPage}

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.Base64
import java.util.concurrent.CompletionException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploader @Inject() (
  val controllerComponents: MessagesControllerComponents,
  fileUploadPage:           FileUploadPage,
  fileUploadCompletePage:   FileUploadCompletePage
)(implicit ec: ExecutionContext)
    extends FrontendBaseController {

  private val s3Endpoint = "http://localhost:8333"
  private val bucket     = "sdec-files"
  private val region     = "us-east-1"

  private val accessKey = "admin"
  private val secretKey = "secret"

  private val s3Client =
    S3Client
      .builder()
      .endpointOverride(java.net.URI.create(s3Endpoint))
      .region(Region.of(region))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(accessKey, secretKey)
        )
      )
      .forcePathStyle(true)
      .build()

  private val dateFormatter =
    DateTimeFormatter
      .ofPattern("yyyyMMdd")
      .withZone(ZoneOffset.UTC)

  private val policyExpirationFormatter =
    DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      .withZone(ZoneOffset.UTC)

  private val signingTimestampFormatter =
    DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC)

  def onPageLoad: Action[AnyContent] =
    Action { implicit request =>
      implicit val messages: Messages =
        controllerComponents.messagesApi.preferred(request)
      Ok(fileUploadPage(createPostData()))
    }

  def uploadComplete: Action[AnyContent] =
    Action.async { implicit request =>
      implicit val messages: Messages =
        controllerComponents.messagesApi.preferred(request)

      val bucketName =
        request.getQueryString("bucket").getOrElse("")

      val key =
        request.getQueryString("key").getOrElse("")

      val etag =
        request.getQueryString("etag").getOrElse("")

      if bucketName != bucket || key.isEmpty then {
        Future.successful(
          BadRequest("Invalid upload details")
        )
      } else {
        Future {
          val headObject =
            s3Client.headObject(
              HeadObjectRequest
                .builder()
                .bucket(bucketName)
                .key(key)
                .build()
            )

          val fileName =
            key.substring(key.lastIndexOf('/') + 1)

          val fileSize =
            headObject.contentLength()

          val contentType =
            Option(headObject.contentType())
              .getOrElse("unknown")

          Ok(
            fileUploadCompletePage(
              fileName = fileName,
              fileSize = fileSize,
              contentType = contentType,
              key = key,
              etag = etag
            )
          )
        }.recover {
          case _: CompletionException =>
            InternalServerError("Unable to retrieve uploaded file details")

          case _: Exception =>
            InternalServerError("Unable to retrieve uploaded file details")
        }
      }
    }

  private def createPostData(): S3PostData = {

    val now        = Instant.now()
    val expiration = now.plusSeconds(15 * 60)

    val date =
      dateFormatter.format(now)

    val expirationTimestamp =
      policyExpirationFormatter.format(expiration)

    val timestamp =
      signingTimestampFormatter.format(now)

    val credential =
      s"$accessKey/$date/$region/s3/aws4_request"

    /*
     * The browser replaces ${filename} with the name of the
     * file selected by the user.
     *
     * Example:
     *
     *   uploads/test.pdf
     *
     * The policy therefore uses starts-with rather than an
     * exact key condition.
     */
    val key =
      "uploads/${filename}"

    val redirect =
      "http://localhost:4510/sdec-fileservice-api/file-upload-complete"

    val policy =
      s"""
         |{
         |  "expiration": "$expirationTimestamp",
         |  "conditions": [
         |    ["eq", "$$bucket", "$bucket"],
         |    ["starts-with", "$$key", "uploads/"],
         |    ["eq", "$$x-amz-algorithm", "AWS4-HMAC-SHA256"],
         |    ["eq", "$$x-amz-credential", "$credential"],
         |    ["eq", "$$x-amz-date", "$timestamp"],
         |    ["content-length-range", 1, 524288000]
         |  ]
         |}
         |""".stripMargin

    val encodedPolicy =
      Base64.getEncoder.encodeToString(
        policy.getBytes(StandardCharsets.UTF_8)
      )

    val signingKey =
      hmac(
        hmac(
          hmac(
            hmac(
              s"AWS4$secretKey".getBytes(StandardCharsets.UTF_8),
              date
            ),
            region
          ),
          "s3"
        ),
        "aws4_request"
      )

    val signature =
      hmac(signingKey, encodedPolicy)
        .map("%02x".format(_))
        .mkString

    S3PostData(
      url = s"$s3Endpoint/$bucket",
      fields = Map(
        "key"                     -> key,
        "bucket"                  -> bucket,
        "policy"                  -> encodedPolicy,
        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
        "x-amz-credential"        -> credential,
        "x-amz-date"              -> timestamp,
        "x-amz-signature"         -> signature,
        "success_action_redirect" -> redirect
      )
    )
  }

  private def hmac(
    key:  Array[Byte],
    data: String
  ): Array[Byte] = {

    val mac =
      Mac.getInstance("HmacSHA256")

    mac.init(
      new SecretKeySpec(key, "HmacSHA256")
    )

    mac.doFinal(
      data.getBytes(StandardCharsets.UTF_8)
    )
  }
}
