@echo off

REM Licensed to the Apache Software Foundation (ASF) under one
REM or more contributor license agreements.  See the NOTICE file
REM distributed with this work for additional information
REM regarding copyright ownership.  The ASF licenses this file
REM to you under the Apache License, Version 2.0 (the
REM "License"); you may not use this file except in compliance
REM with the License.  You may obtain a copy of the License at
REM 
REM   http://www.apache.org/licenses/LICENSE-2.0
REM 
REM Unless required by applicable law or agreed to in writing,
REM software distributed under the License is distributed on an
REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
REM KIND, either express or implied.  See the License for the
REM specific language governing permissions and limitations
REM under the License. 

REM this script starts ApacheDS (as non-windows-service)


IF NOT exist target/apacheds-noarch-installer-1.5.6-SNAPSHOT-app.jar GOTO :MVN
   echo uber jar exists
   GOTO :JAVA

:MVN
   echo uber jar not found need to build it
   call mvn clean install
   GOTO :JAVA

:JAVA
   java -Dlog4j.configuration="file:///%cd%/log4j.properties" -jar target/apacheds-noarch-installer-1.5.6-SNAPSHOT-app.jar target/plan/server.xml
