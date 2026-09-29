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

package uk.gov.hmrc.sdecfileserviceapialpha.config

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.net.URI
import javax.inject.{Inject, Provider}

class S3PresignerProvider @Inject() (
  configuration: play.api.Configuration
) extends Provider[S3Presigner] {

  override def get(): S3Presigner = {

    val credentials =
      AwsBasicCredentials.create(
        configuration.get[String]("s3.accessKey"),
        configuration.get[String]("s3.secretKey")
      )

    val s3Configuration =
      S3Configuration
        .builder()
        .pathStyleAccessEnabled(true)
        .build()

    S3Presigner
      .builder()
      .endpointOverride(
        URI.create(
          configuration.get[String]("s3.endpoint")
        )
      )
      .region(
        Region.of(
          configuration.get[String]("s3.region")
        )
      )
      .credentialsProvider(
        StaticCredentialsProvider.create(
          credentials
        )
      )
      .serviceConfiguration(
        s3Configuration
      )
      .build()
  }
}
