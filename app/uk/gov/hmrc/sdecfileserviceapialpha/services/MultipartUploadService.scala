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

package uk.gov.hmrc.sdecfileserviceapialpha.services

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import uk.gov.hmrc.sdecfileserviceapialpha.controllers.fileupload.{InitiateMultipartUploadResponse, MultipartUploadPart, PartUploadUrl}

import javax.inject.Inject

class MultipartUploadService @Inject() (
  s3Client:      S3Client,
  s3Presigner:   S3Presigner,
  configuration: play.api.Configuration
) {

  private val bucket = configuration.get[String]("s3.bucket")

  /*
   * 8 MB parts.
   *
   * S3 requires every part except the final part to be
   * at least 5 MB.
   */
  private val partSize: Long =
    8L * 1024L * 1024L

  def initiate(
    filename:    String,
    contentType: String,
    fileSize:    Long
  ): InitiateMultipartUploadResponse = {

    val key =
      s"uploads/$filename"

    val request =
      CreateMultipartUploadRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .build()

    val response =
      s3Client.createMultipartUpload(request)

    val uploadId =
      response.uploadId()

    val numberOfParts =
      Math
        .ceil(
          fileSize.toDouble / partSize.toDouble
        )
        .toInt

    val urls =
      (1 to numberOfParts).map { partNumber =>
        val uploadPartRequest =
          UploadPartRequest
            .builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build()

        val presignRequest =
          UploadPartPresignRequest
            .builder()
            .signatureDuration(
              java.time.Duration.ofMinutes(30)
            )
            .uploadPartRequest(uploadPartRequest)
            .build()

        val presigned =
          s3Presigner.presignUploadPart(
            presignRequest
          )

        PartUploadUrl(
          partNumber = partNumber,
          url = presigned.url().toString
        )
      }

    InitiateMultipartUploadResponse(
      key = key,
      uploadId = uploadId,
      partSize = partSize,
      urls = urls
    )
  }

  def complete(
    key:      String,
    uploadId: String,
    parts:    Seq[MultipartUploadPart]
  ): String = {

    val completedParts =
      parts
        .sortBy(_.partNumber)
        .map { part =>
          CompletedPart
            .builder()
            .partNumber(part.partNumber)
            .eTag(part.etag)
            .build()
        }

    val completedUpload =
      CompletedMultipartUpload
        .builder()
        .parts(completedParts*)
        .build()

    val request =
      CompleteMultipartUploadRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .uploadId(uploadId)
        .multipartUpload(completedUpload)
        .build()

    s3Client.completeMultipartUpload(request)

    key
  }
}
