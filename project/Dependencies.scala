import sbt._

object Dependencies {

  val test = Seq(
    "uk.gov.hmrc" %% "performance-test-runner" % "6.1.0",
    "com.lihaoyi" %% "requests"                % "0.9.0",
    "com.lihaoyi" %% "ujson"                   % "0.9.6"
  ).map(_ % Test)

}
