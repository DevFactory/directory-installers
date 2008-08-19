#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

if [ -e target/apacheds-noarch-installer-1.5.4-SNAPSHOT-app.jar ] ; then
  echo uber jar exists
else
  echo uber jar not found need to build it
  mvn clean install
fi

java -Dlog4j.debug -Dlog4j.configuration=file:./log4j.properties -jar target/apacheds-noarch-installer-1.5.4-SNAPSHOT-app.jar target/plan/server.xml 
