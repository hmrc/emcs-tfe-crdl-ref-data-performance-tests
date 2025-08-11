/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.perftests.example

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import uk.gov.hmrc.performance.conf.ServicesConfiguration

import java.util.UUID

object EmcsTfeCrdlRequests extends ServicesConfiguration {
  val authLoginApiUrl = s"${baseUrlFor("auth-login-api")}"
  val baseUrl         = s"${baseUrlFor("emcs-tfe-crdl-reference-data")}/emcs-tfe-reference-data"

  def arrayFromSession(session: Session, sessionKey: String): String =
    session(sessionKey)
      .as[Seq[String]]
      .map(str => s"\"$str\"")
      .mkString("[", ", ", "]")

  def objectsFromSession(session: Session, sessionKey: String)(props: String*): String =
    session(sessionKey)
      .as[Seq[Map[String, String]]]
      .map(obj =>
        props
          .map { prop =>
            s""""$prop": "${obj(prop)}""""
          }
          .mkString("{", ", ", "}")
      )
      .mkString("[", ", ", "]")

  val fetchAuthToken: HttpRequestBuilder =
    http("Fetch Authentication Token")
      .post(s"$authLoginApiUrl/government-gateway/session/login")
      .header("Content-Type", "application/json")
      .body(StringBody(session => s"""{
      |  "credId": "${UUID.randomUUID}",
      |  "affinityGroup": "Organisation",
      |  "credentialStrength": "strong",
      |  "enrolments": [
      |    {
      |      "key": "HMRC-EMCS-ORG",
      |      "state": "Activated",
      |      "identifiers": [{
      |        "key": "ExciseNumber",
      |        "value": "${session("exciseNumber").as[String]}"
      |      }]
      |    }
      |  ]
      |}""".stripMargin))
      .check(status.is(201))
      .check(header("Authorization").saveAs("authToken"))

  val fetchCnCodeInformation: HttpRequestBuilder =
    http("Fetch CN Code Information")
      .post(s"$baseUrl/oracle/cn-code-information")
      .header("Authorization", _("authToken").as[String])
      .header("Content-Type", "application/json")
      .body(
        StringBody(session =>
          s"""{"items": ${objectsFromSession(session, "cnInfo")(
              "productCode",
              "cnCode"
            )}}""".stripMargin
        )
      )
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchCnCodes: HttpRequestBuilder =
    http("Fetch CN Codes For Excise Product")
      .get(session => s"$baseUrl/oracle/cn-codes/${session("exciseProduct").as[String]}")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchAllPackagingTypes: HttpRequestBuilder =
    http("Fetch All Packaging Types")
      .get(s"$baseUrl/oracle/packaging-types")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchCountablePackagingTypes: HttpRequestBuilder =
    http("Fetch Countable Packaging Types")
      .get(s"$baseUrl/oracle/packaging-types")
      .queryParam("isCountable", "true")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchUncountablePackagingTypes: HttpRequestBuilder =
    http("Fetch Uncountable Packaging Types")
      .get(s"$baseUrl/oracle/packaging-types")
      .queryParam("isCountable", "false")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchSpecifiedPackagingTypes: HttpRequestBuilder =
    http("Fetch Specified Packaging Types")
      .post(s"$baseUrl/oracle/packaging-types")
      .header("Authorization", _("authToken").as[String])
      .header("Content-Type", "application/json")
      .body(StringBody(arrayFromSession(_, "packagingTypes")))
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchAllWineOperations: HttpRequestBuilder =
    http("Fetch All Wine Operations")
      .get(s"$baseUrl/oracle/wine-operations")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchSpecifiedWineOperations: HttpRequestBuilder =
    http("Fetch Specified Wine Operations")
      .post(s"$baseUrl/oracle/wine-operations")
      .header("Authorization", _("authToken").as[String])
      .header("Content-Type", "application/json")
      .body(StringBody(arrayFromSession(_, "wineOperations")))
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchMemberStates: HttpRequestBuilder =
    http("Fetch Member States")
      .get(s"$baseUrl/oracle/member-states")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchMemberStatesAndCountries: HttpRequestBuilder =
    http("Fetch Member States And Countries")
      .get(s"$baseUrl/oracle/member-states-and-countries")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchTransportUnits: HttpRequestBuilder =
    http("Fetch Transport Units")
      .get(s"$baseUrl/oracle/transport-units")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchDocumentTypes: HttpRequestBuilder =
    http("Fetch Document Types")
      .get(s"$baseUrl/oracle/type-of-document")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))

  val fetchAllEPCCodes: HttpRequestBuilder =
    http("Fetch All EPC Codes")
      .get(s"$baseUrl/oracle/epc-codes")
      .header("Authorization", _("authToken").as[String])
      .check(status.is(200))
      .check(jsonPath("$").count.gt(0))
}
