#!/bin/sh
if [ -e target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar ] ; then
  echo uber jar exists
else
  echo uber jar not found need to build it
  mvn clean assembly:assembly
  jar -xvf target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar META-INF/MANIFEST.MF
  echo "Class-path: myinterceptor.jar" >> META-INF/MANIFEST.MF 
  jar -uf target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar  META-INF/MANIFEST.MF
fi

java -Dlog4j.debug -Dlog4j.configuration=file:./log4j.properties -jar target/apacheds-noarch-installer-1.5.2-SNAPSHOT-app.jar server.xml 
