import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "sdec-fileservice-api-alpha"

ThisBuild / majorVersion := 0

lazy val compilerSettings = Seq(
  scalaVersion := "3.3.7",
  scalacOptions += "-Wconf:src=routes/.*:s",
  scalacOptions += "-Wconf:msg=unused import&src=html/.*:s",
  scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    PlayKeys.playDefaultPort := 4510,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "play.twirl.api.HtmlFormat._",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "uk.gov.hmrc.hmrcfrontend.views.config._"
    ),
    compilerSettings
  )
  .settings(
    pipelineStages := Seq(digest),
    Assets / pipelineStages := Seq(concat),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / unmanagedSourceDirectories := (Test / baseDirectory)(base => Seq(base / "test", base / "test-common")).value,
    Test / unmanagedResourceDirectories := Seq(
      baseDirectory.value / "test-resources"
    )
  )
  .settings(CodeCoverageSettings.settings: _*)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    DefaultBuildSettings.itSettings(),
    compilerSettings
  )
  .settings(libraryDependencies ++= AppDependencies.it)

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

// @formatter:off
addCommandAlias("prePrChecks", "; scalafmtCheckAll; it/scalafmtCheckAll; scalafmtSbtCheck; scalafixAll --check; it/scalafixAll --check")
addCommandAlias("checkCodeCoverage", "; clean; coverage; test; it/test; coverageReport")
addCommandAlias("lint", "; scalafmtAll; it/scalafmtAll; scalafmtSbt; it/scalafixAll; scalafixAll")
addCommandAlias("prePush", "; reload; clean; compile; test; it/test; lint;")
// @formatter:on
