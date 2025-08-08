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
import uk.gov.hmrc.performance.conf.ServicesConfiguration
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.example.EmcsTfeCrdlRequests._

import scala.util.Random

class EmcsTfeCrdlSimulation extends PerformanceTestRunner with ServicesConfiguration {
  val internalAuthUrl = s"${baseUrlFor("internal-auth")}"
  val crdlCacheUrl    = s"${baseUrlFor("crdl-cache")}/crdl-cache"
  val emcsRefDataUrl  = s"${baseUrlFor("emcs-tfe-crdl-reference-data")}/emcs-tfe-reference-data"
  val internalAuthToken = if (runLocal) "crdl-cache-token" else sys.env("INTERNAL_AUTH_TOKEN")

  val randomNumbers = LazyList.continually {
    "0123456789".charAt(Random.nextInt(10))
  }

  val randomIdentifiers = Iterator.continually {
    Map("exciseNumber" -> s"GBWK${randomNumbers.take(9).mkString}")
  }

  val packagingTypes =
    csv("data/packaging-types.csv").readRecords
      .map(_("packagingType").toString)

  val randomPackagingTypes = Iterator.continually {
    Map("packagingTypes" -> Random.shuffle(packagingTypes).take(1 + Random.nextInt(6)))
  }

  val wineOperations =
    csv("data/wine-operations.csv").readRecords
      .map(_("wineOperation").toString)

  val randomWineOperations = Iterator.continually {
    Map("wineOperations" -> Random.shuffle(wineOperations).take(1 + Random.nextInt(4)))
  }

  val cnInfo = csv("data/cn-info-requests.csv").readRecords
    .map(r => Map("productCode" -> r("productCode").toString, "cnCode" -> r("cnCode").toString))

  val randomCnInfo = Iterator.continually {
    Map("cnInfo" -> Random.shuffle(cnInfo).take(1 + Random.nextInt(4)))
  }

  // Random excise numbers for the EMCS enrolment
  def randomIdFeeder = feed(randomIdentifiers)

  // Random excise products for the CN codes endpoint
  def exciseProductsFeeder = feed(csv("data/excise-products.csv").random())

  // Random lists of packaging types for the packaging types endpoint
  def packagingTypesFeeder = feed(randomPackagingTypes)

  // Random lists of wine operations for the wine operations endpoint
  def wineOperationsFeeder = feed(randomWineOperations)

  // Random lists of CN information requests for the CN code information endpoint
  def cnInfoFeeder = feed(randomCnInfo)

  def internalAuthTokenExists() = {
    requests
      .get(
        s"$internalAuthUrl/test-only/token",
        headers = Map("Authorization" -> internalAuthToken),
        check = false
      )
      .statusCode == 200
  }

  def createInternalAuthToken() = {
    requests.post(
      s"$internalAuthUrl/test-only/token",
      headers = Map("Content-Type" -> "application/json"),
      data = ujson.Obj(
        "token"     -> internalAuthToken,
        "principal" -> "performance-jenkins",
        "permissions" -> ujson.Arr(
          ujson.Obj(
            "resourceType"     -> "crdl-cache",
            "resourceLocation" -> "*",
            "actions"          -> ujson.Arr("READ")
          )
        )
      )
    )
  }

  def deleteCrdlData(entity: String) =
    requests.delete(s"$crdlCacheUrl/test-only/$entity")

  def deleteTfeData(entity: String) =
    requests.delete(s"$emcsRefDataUrl/test-only/$entity")

  def importCrdlData(entity: String) =
    requests.post(s"$crdlCacheUrl/test-only/$entity")

  def importTfeData() =
    requests.post(s"$emcsRefDataUrl/test-only/reference-data")

  def getCrdlImportStatus(entity: String) = {
    val response = requests.get(s"$crdlCacheUrl/test-only/$entity")
    val json     = ujson.read(response)
    json("status").str
  }

  def getTfeImportStatus() = {
    val response = requests.get(s"$emcsRefDataUrl/test-only/reference-data")
    val json     = ujson.read(response)
    json("status").str
  }

  if (runLocal) {
    before {
      // Clear down existing CRDL data
      deleteCrdlData("last-updated")
      deleteCrdlData("codelists")
      deleteCrdlData("correspondence-lists")
      // Clear down existing TFE data
      deleteTfeData("codelists")
      deleteTfeData("cn-codes")
      deleteTfeData("excise-products")
      // Import everything into CRDL again
      importCrdlData("codelists")
      importCrdlData("correspondence-lists")
      // Wait for CRDL imports to complete
      while (getCrdlImportStatus("codelists") != "IDLE") {
        Thread.sleep(200)
      }
      while (getCrdlImportStatus("correspondence-lists") != "IDLE") {
        Thread.sleep(200)
      }
      // Import ref data from CRDL into TFE
      importTfeData()
      // Wait for TFE imports to complete
      while (getTfeImportStatus() != "IDLE") {
        Thread.sleep(200)
      }
      if (!internalAuthTokenExists()) {
        createInternalAuthToken()
      }
    }
  }

  setup("fetch-cn-info", "Fetch CN Code Information")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withActions(cnInfoFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchCnCodeInformation)

  setup("fetch-excise-products", "Fetch Excise Products")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchAllEPCCodes)

  setup("fetch-packaging-types", "Fetch Packaging Types")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchAllPackagingTypes)

  setup("fetch-member-states", "Fetch Member States")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchMemberStates)

  setup("fetch-countable-packaging-types", "Fetch Countable Packaging Types")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchCountablePackagingTypes)

  setup("fetch-document-types", "Fetch Document Types")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchDocumentTypes)

  setup("fetch-cn-codes", "Fetch CN Codes For Excise Product")
    .withActions(exciseProductsFeeder.actionBuilders: _*)
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchCnCodes)

  setup("fetch-specified-packaging-types", "Fetch Specified Packaging Types")
    .withActions(packagingTypesFeeder.actionBuilders: _*)
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchSpecifiedPackagingTypes)

  setup("fetch-specified-wine-operations", "Fetch Specified Wine Operations")
    .withActions(wineOperationsFeeder.actionBuilders: _*)
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchSpecifiedWineOperations)

  setup("fetch-member-states-and-countries", "Fetch Member States And Countries")
    .withActions(randomIdFeeder.actionBuilders: _*)
    .withRequests(fetchAuthToken, fetchMemberStatesAndCountries)

  runSimulation()
}
