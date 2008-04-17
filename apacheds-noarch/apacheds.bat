@echo off

REM this script starts ApacheDS (as non-windows-service)


IF NOT exist target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar GOTO :MVN
   echo uber jar exists
   GOTO :JAVA

:MVN
   echo uber jar not found need to build it
   call mvn clean install
   GOTO :JAVA

:JAVA
   java -Dlog4j.configuration="file:///%cd%/log4j.properties" -jar target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar target/plan/server.xml
