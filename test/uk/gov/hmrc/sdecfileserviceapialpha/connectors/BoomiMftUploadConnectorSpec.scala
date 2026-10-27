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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.sdecfileserviceapialpha.fixtures.MftFixtures.*
import uk.gov.hmrc.sdecfileserviceapialpha.models.MftUploadError

class BoomiMftUploadConnectorSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.boomi.protocol" -> "http",
        "microservice.services.boomi.host"     -> wireMockHost,
        "microservice.services.boomi.port"     -> wireMockPort,
        "boomi.client-id"                      -> clientId,
        "boomi.client-secret"                  -> clientSecret
      )
      .build()

  private given HeaderCarrier = HeaderCarrier()

  private lazy val connector = app.injector.instanceOf[BoomiMftUploadConnector]

  private val uploadPath = "/ws/rest/files/v1/upload"

  private val tokenIssued =
    post(urlPathEqualTo("/token"))
      .willReturn(
        okJson(Json.obj("access_token" -> accessToken, "token_type" -> "Bearer", "expires_in" -> 3600).toString)
      )

  "BoomiMftUploadConnector.upload" should {
    "send the raw file with the thread, file name and token, and return what MFT stored" in {
      wireMockServer.stubFor(tokenIssued)
      wireMockServer.stubFor(
        post(urlPathEqualTo(uploadPath))
          .willReturn(aResponse().withStatus(201).withBody(Json.toJson(mftUploadSuccess).toString))
      )

      val result = connector.upload(threadReference, fileName, correlationId, fileToUpload).futureValue

      result mustBe Right(mftUploadSuccess)
      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/token"))
          .withRequestBody(containing("grant_type=client_credentials"))
          .withRequestBody(containing(s"client_id=$clientId"))
      )
      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(uploadPath))
          .withQueryParam("thread", equalTo(threadReference))
          .withHeader("Authorization", equalTo(s"Bearer $accessToken"))
          .withHeader("X-File-Name", equalTo(fileName))
          .withHeader("X-Correlation-Id", equalTo(correlationId))
          .withHeader("X-Source-System", equalTo("SDEC"))
          .withRequestBody(equalTo(fileContent))
      )
    }

    "return Rejected with Boomi's code and message when the upload is rejected" in {
      wireMockServer.stubFor(tokenIssued)
      wireMockServer.stubFor(
        post(urlPathEqualTo(uploadPath)).willReturn(
          aResponse()
            .withStatus(400)
            .withBody(
              Json
                .obj(
                  "fileId"    -> correlationId,
                  "status"    -> "REJECTED",
                  "errorCode" -> "INVALID_EXTENSION",
                  "message"   -> rejectedMessage
                )
                .toString
            )
        )
      )

      val result = connector.upload(threadReference, fileName, correlationId, fileToUpload).futureValue

      result mustBe Left(MftUploadError.Rejected("INVALID_EXTENSION", rejectedMessage))
    }

    "return DeliveryFailed when Boomi cannot deliver the file to MFT" in {
      wireMockServer.stubFor(tokenIssued)
      wireMockServer.stubFor(
        post(urlPathEqualTo(uploadPath)).willReturn(
          aResponse()
            .withStatus(502)
            .withBody(
              Json
                .obj(
                  "fileId"    -> correlationId,
                  "status"    -> "FAILED",
                  "errorCode" -> "MFT_DELIVERY_FAILED",
                  "message"   -> deliveryFailedMessage
                )
                .toString
            )
        )
      )

      val result = connector.upload(threadReference, fileName, correlationId, fileToUpload).futureValue

      result mustBe Left(MftUploadError.DeliveryFailed(correlationId, deliveryFailedMessage))
    }

    "return Unexpected for any other status, such as 413 for a file that is too large" in {
      val tooLargeBody = Json
        .obj("errorCode" -> "PAYLOAD_TOO_LARGE", "message" -> "The uploaded file exceeds the maximum permitted size.")
        .toString
      wireMockServer.stubFor(tokenIssued)
      wireMockServer.stubFor(
        post(urlPathEqualTo(uploadPath)).willReturn(aResponse().withStatus(413).withBody(tooLargeBody))
      )

      val result = connector.upload(threadReference, fileName, correlationId, fileToUpload).futureValue

      result mustBe Left(MftUploadError.Unexpected(413, tooLargeBody))
    }

    "return TokenFailed and never call the upload API when the token request fails" in {
      wireMockServer.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withStatus(401)))

      val result = connector.upload(threadReference, fileName, correlationId, fileToUpload).futureValue

      result mustBe Left(MftUploadError.TokenFailed(401))
      wireMockServer.verify(0, postRequestedFor(urlPathEqualTo(uploadPath)))
    }
  }

}
