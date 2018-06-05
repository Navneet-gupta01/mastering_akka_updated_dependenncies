name := "initial-app"

lazy val commonSettings = Seq(
  organization := "com.navneetgupta",
  version := "0.1.0",
  scalaVersion := "2.12.6"
)

lazy val root = (project in file(".")).
  aggregate(common,inventoryMngmt,userMngmt,creditProcessor,salesOrderProcessor)

lazy val common = (project in file("common_ch06")).
  settings(commonSettings: _*)

lazy val inventoryMngmt = (project in file("inventory-management")).
   settings(commonSettings: _*).
   dependsOn(common)
 
lazy val userMngmt = (project in file("user-management")).
   settings(commonSettings: _*).
   dependsOn(common)
   
lazy val creditProcessor = (project in file("credit-processing")).
   settings(commonSettings: _*).
   dependsOn(common)

lazy val salesOrderProcessor = (project in file("sales-order-processing")).
   settings(commonSettings: _*).
   dependsOn(common, inventoryMngmt, userMngmt, creditProcessor)
 
lazy val server = (project in file("server_ch06")).
   settings(commonSettings: _*).
   dependsOn(common,inventoryMngmt,userMngmt,creditProcessor,salesOrderProcessor)
// # bookServices, userServices, creditServices, orderServices
