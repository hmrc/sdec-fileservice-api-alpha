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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.sdecfileserviceapialpha.fixtures.MftFixtures.*
import uk.gov.hmrc.sdecfileserviceapialpha.fixtures.RecordingMftUploadConnector
import uk.gov.hmrc.sdecfileserviceapialpha.models.MftUploadError

import scala.concurrent.ExecutionContext

class MftUploadControllerSpec extends AnyWordSpec with Matchers {

  private given ec: ExecutionContext = ExecutionContext.global

  private val cc      = stubControllerComponents()
  private val request = FakeRequest(POST, s"/sdec-fileservice-api/thread/$threadReference/file")

  private val formWithFile: MultipartFormData[TemporaryFile] = MultipartFormData(
    dataParts = Map.empty,
    files = Seq(
      FilePart("file", fileName, Some("application/pdf"), SingletonTemporaryFileCreator.create(fileToUpload))
    ),
    badParts = Seq.empty
  )

  private val formWithoutFile: MultipartFormData[TemporaryFile] =
    MultipartFormData(dataParts = Map.empty, files = Seq.empty, badParts = Seq.empty)

  "MftUploadController.upload" should {
    "return 201 with MFT's details and pass the thread reference and file name on" in {
      val connector  = new RecordingMftUploadConnector(Right(mftUploadSuccess))
      val controller = new MftUploadController(cc, connector)

      val result = controller.upload(threadReference)(request.withBody(formWithFile))

      status(result) mustBe CREATED
      contentAsJson(result) mustBe Json.toJson(mftUploadSuccess)
      connector.received.get mustBe Some((threadReference, fileName))
    }

    "return 400 with Boomi's code and message when the upload is rejected" in {
      val connector =
        new RecordingMftUploadConnector(Left(MftUploadError.Rejected("INVALID_EXTENSION", rejectedMessage)))
      val controller = new MftUploadController(cc, connector)

      val result = controller.upload(threadReference)(request.withBody(formWithFile))

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj("errorCode" -> "INVALID_EXTENSION", "message" -> rejectedMessage)
    }

    "return 502 when Boomi cannot deliver the file to MFT" in {
      val connector =
        new RecordingMftUploadConnector(Left(MftUploadError.DeliveryFailed(correlationId, deliveryFailedMessage)))
      val controller = new MftUploadController(cc, connector)

      val result = controller.upload(threadReference)(request.withBody(formWithFile))

      status(result) mustBe BAD_GATEWAY
    }

    "return 502 when the Boomi token cannot be fetched" in {
      val connector  = new RecordingMftUploadConnector(Left(MftUploadError.TokenFailed(401)))
      val controller = new MftUploadController(cc, connector)

      val result = controller.upload(threadReference)(request.withBody(formWithFile))

      status(result) mustBe BAD_GATEWAY
    }

    "return 400 and call nothing when no file is attached" in {
      val connector  = new RecordingMftUploadConnector(Right(mftUploadSuccess))
      val controller = new MftUploadController(cc, connector)

      val result = controller.upload(threadReference)(request.withBody(formWithoutFile))

      status(result) mustBe BAD_REQUEST
      connector.received.get mustBe None
    }
  }

}
