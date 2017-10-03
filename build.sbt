import sbt.Resolver
import sbtrelease._
import sbtrelease.ReleaseStateTransformations._

val commonsVersion = "0.4.3"
val scalaTestVersion = "3.0.4"
val akkaHttpVersion = "10.0.9"

val baseUri = "http://localhost/v0"

lazy val shaclValidator = nexusDep("shacl-validator", commonsVersion)

lazy val workbench = project.in(file("modules/workbench"))
  .settings(common, noPublish)
  .settings(
    name := "bbp-schemas-workbench",
    moduleName := "bbp-schemas-workbench",
    libraryDependencies ++= Seq(
      shaclValidator,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion
    ))


lazy val bbpcore = project.in(file("modules/bbp-core"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(workbench % Test)
  .settings(common, publishSettings, buildInfoSettings)
  .settings(
    name := "bbp-core-schemas",
    moduleName := "bbp-core-schemas",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test))

lazy val docs = project.in(file("docs"))
  .enablePlugins(ParadoxPlugin)
  .settings(common, noPublish)
  .settings(
    name := "bbp-domains-docs",
    moduleName := "bbp-domains-docs",
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    target in(Compile, paradox) := (resourceManaged in Compile).value / "docs",
    resourceGenerators in Compile += {
      (paradox in Compile).map { parent =>
        (parent ** "*").get
      }.taskValue
    })

lazy val bbpexperiment = project.in(file("modules/bbp-experiment"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(bbpcore)
  .dependsOn(workbench % Test)
  .settings(common, publishSettings, buildInfoSettings)
  .settings(
    name := "bbp-experiment-schemas",
    moduleName := "bbp-experiment-schemas",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test))

lazy val bbpatlas = project.in(file("modules/bbp-atlas"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(bbpcore)
  .dependsOn(workbench % Test)
  .settings(common, publishSettings, buildInfoSettings)
  .settings(
    name := "bbp-atlas-schemas",
    moduleName := "bbp-atlas-schemas",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test))


lazy val root = project.in(file("."))
  .settings(
    name := "bbp-schemas",
    moduleName := "bbp-schemas")
  .settings(common, noPublish)
  .aggregate(workbench, bbpcore, bbpexperiment, bbpatlas, docs)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    BuildInfoKey("base" -> baseUri),
    BuildInfoKey.map(resources.in(Compile)) { case (_, v) =>
      val resourceBase = resourceDirectory.in(Compile).value.getAbsolutePath
      val dirsWithJson = (v * "schemas" ** "*.json").get
      val schemas = dirsWithJson.map(_.getAbsolutePath.substring(resourceBase.length))
      "schemas" -> schemas
    },
    BuildInfoKey.map(resources.in(Compile)) { case (_, v) =>
      val resourceBase = resourceDirectory.in(Compile).value.getAbsolutePath
      val dirsWithJson = (v * "contexts" ** "*.json").get
      val contexts = dirsWithJson.map(_.getAbsolutePath.substring(resourceBase.length))
      "contexts" -> contexts
    },
    BuildInfoKey.map(resources.in(Test)) { case (_, v) =>
      val resourceBase = resourceDirectory.in(Test).value.getAbsolutePath
      val dirsWithJson = (v * "data" ** "*.json").get
      val data = dirsWithJson.map(_.getAbsolutePath.substring(resourceBase.length))
      "data" -> data
    },
    BuildInfoKey.map(resources.in(Test)) { case (_, v) =>
      val resourceBase = resourceDirectory.in(Test).value.getAbsolutePath
      val dirsWithJson = (v * "invalid" ** "*.json").get
      val invalid = dirsWithJson.map(_.getAbsolutePath.substring(resourceBase.length))
      "invalid" -> invalid
    }
  ),
  buildInfoPackage := "ch.epfl.bluebrain.nexus.schema")

lazy val common = Seq(
  scalacOptions in(Compile, console) ~= (_ filterNot (_ == "-Xfatal-warnings")),
  resolvers += Resolver.bintrayRepo("bogdanromanx", "maven"))

lazy val noPublish = Seq(
  publishLocal := {},
  publish := {})



lazy val publishSettings = Seq(
  overrideBuildResolvers := true,
  publishMavenStyle := true,
  pomIncludeRepository := Function.const(false),
  publishTo := {
    if (isSnapshot.value) Some("Snapshots" at "https://bbpteam.epfl.ch/repository/nexus/content/repositories/snapshots")
    else Some("Releases" at "https://bbpteam.epfl.ch/repository/nexus/content/repositories/releases")
  },
  releaseVersionBump := Version.Bump.Bugfix,
  releaseVersion := { ver =>
    sys.env.get("RELEASE_VERSION") // fetch the optional system env var
      .map(_.trim)
      .filterNot(_.isEmpty)
      .map(v => Version(v).getOrElse(versionFormatError)) // parse it into a version or throw
      .orElse(Version(ver).map(_.withoutQualifier)) // fallback on the current version without a qualifier
      .map(_.string) // map it to its string representation
      .getOrElse(versionFormatError) // throw if we couldn't compute the version
  },
  releaseNextVersion := { ver =>
    sys.env.get("NEXT_VERSION") // fetch the optional system env var
      .map(_.trim)
      .filterNot(_.isEmpty)
      .map(v => Version(v).getOrElse(versionFormatError)) // parse it into a version or throw
      .orElse(Version(ver).map(_.bump(releaseVersionBump.value))) // fallback on the current version bumped accordingly
      .map(_.asSnapshot.string) // map it to its snapshot version as string
      .getOrElse(versionFormatError) // throw if we couldn't compute the version
  },
  releaseCrossBuild := false,
  releaseTagName := s"${name.value}-${(version in ThisBuild).value}",
  releaseTagComment := s"Releasing version ${(version in ThisBuild).value}",
  releaseCommitMessage := s"Setting new version to ${(version in ThisBuild).value}",
  releaseProcess := Seq(
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    pushChanges)
)

def nexusDep(name: String, version: String): ModuleID =
  "ch.epfl.bluebrain.nexus" %% name % version

addCommandAlias("review", ";clean;test")
addCommandAlias("rel", ";release with-defaults skip-tests")
