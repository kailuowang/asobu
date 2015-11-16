import org.scoverage.coveralls.Imports.CoverallsKeys._

organization in ThisBuild := "com.iheart"

name := "play-akka"

resolvers +=  Resolver.bintrayRepo("scalaz", "releases")

scalaVersion in ThisBuild := "2.11.7"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Dependencies.playJson ++ Dependencies.test ++ Dependencies.yaml :+
  "com.chuusai" %% "shapeless" % "2.2.5"

Publish.settings

lazy val playAkka = project in file(".")


coverallsToken := Some("8IGVvRI0URTzmzUd5Ts7CtsMecw6wbkme")

Format.settings
