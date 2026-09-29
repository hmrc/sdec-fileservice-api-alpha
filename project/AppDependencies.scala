import sbt.Keys.libraryDependencies
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.8.0"
  private val hmrcPlayFrontend  = "12.32.1"
  private val awsSDKVersion = "2.55.6"

  val compile = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30" % hmrcPlayFrontend,
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30" % bootstrapVersion,
    "software.amazon.awssdk" % "s3" % awsSDKVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  val it = Seq.empty
}
