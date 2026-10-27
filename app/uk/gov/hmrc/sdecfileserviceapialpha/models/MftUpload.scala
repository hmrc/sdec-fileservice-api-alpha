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

package uk.gov.hmrc.sdecfileserviceapialpha.models

import play.api.libs.json.{Json, OFormat, Reads, __}

final case class MftUploadSuccess(
  fileId:    String,
  fileName:  String,
  path:      String,
  sizeBytes: Long,
  status:    String
)

object MftUploadSuccess {
  given format: OFormat[MftUploadSuccess] = Json.format[MftUploadSuccess]
}

final case class BoomiErrorBody(
  fileId:    Option[String],
  status:    Option[String],
  errorCode: Option[String],
  message:   Option[String]
)

object BoomiErrorBody {
  given format: OFormat[BoomiErrorBody] = Json.format[BoomiErrorBody]
}

final case class BoomiToken(accessToken: String)

object BoomiToken {
  given reads: Reads[BoomiToken] = (__ \ "access_token").read[String].map(BoomiToken.apply)
}

enum MftUploadError {
  case Rejected(errorCode: String, message: String)
  case DeliveryFailed(fileId: String, message: String)
  case TokenFailed(status: Int)
  case Unexpected(status: Int, body: String)
}
