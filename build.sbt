name := "PlayExamples"

version := "1.0"

lazy val `playexamples` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"
val akkaV = "2.4.2"
libraryDependencies ++= Seq(
  jdbc , cache , ws , filters,
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "com.typesafe.akka" %%  "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-remote" % akkaV,
  "com.typesafe.akka" %% "akka-cluster" % akkaV,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaV,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaV,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
  "com.typesafe.akka" %% "akka-persistence" % akkaV,
  "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
  "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.0",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.okumin" %% "akka-persistence-sql-async" % "0.3.1",
  "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
  "com.github.mauricio" %% "mysql-async" % "0.2.16",
  "mysql" % "mysql-connector-java" % "5.1.37"
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )
