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

package uk.gov.hmrc.sdecfileserviceapialpha.fixtures

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.sdecfileserviceapialpha.connectors.MftUploadConnector
import uk.gov.hmrc.sdecfileserviceapialpha.models.{MftUploadError, MftUploadSuccess}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

object MftFixtures {
  val threadReference:       String = "ABCDEFGH2345"
  val fileName:              String = "evidence.pdf"
  val correlationId:         String = "batch-001"
  val clientId:              String = "test-client"
  val clientSecret:          String = "test-secret"
  val accessToken:           String = "test-token"
  val fileContent:           String = "hello world"
  val rejectedMessage:       String = "File extension not allowed. Allowed: .csv, .xml, .pdf, .zip, .txt"
  val deliveryFailedMessage: String =
    "The file could not be delivered to the MFT server. Retry later or contact support with the fileId."

  lazy val fileToUpload: Path = Files.writeString(Files.createTempFile("mft-upload", ".pdf"), fileContent)

  val mftUploadSuccess: MftUploadSuccess = MftUploadSuccess(
    fileId = correlationId,
    fileName = s"SDEC_20261027100000_${correlationId}_$fileName",
    path = s"/staging/$threadReference/",
    sizeBytes = fileContent.length.toLong,
    status = "DELIVERED_TO_MFT"
  )
}

final class RecordingMftUploadConnector(result: Either[MftUploadError, MftUploadSuccess]) extends MftUploadConnector {

  val received: AtomicReference[Option[(String, String)]] = new AtomicReference(None)

  override def upload(
    threadReference: String,
    fileName:        String,
    correlationId:   String,
    file:            Path
  )(implicit hc: HeaderCarrier): Future[Either[MftUploadError, MftUploadSuccess]] = {
    received.set(Some((threadReference, fileName)))
    Future.successful(result)
  }

}
