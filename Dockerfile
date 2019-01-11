#       Copyright 2017 IBM Corp All Rights Reserved

#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at

#       http://www.apache.org/licenses/LICENSE-2.0

#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

# FROM openliberty/open-liberty:microProfile2
FROM websphere-liberty:microProfile2

COPY src/main/liberty/config /config/
COPY target/portfolio-1.0-SNAPSHOT.war /config/apps/Portfolio.war

#apt-get needs root access
USER root
RUN chmod g+w /config/apps
RUN chmod g+w /config/jvm.options
RUN apt-get update
RUN apt-get install curl -y
USER 1001

COPY lwdc/javametrics.liberty.icam-1.2.1.esa /opt/
RUN installUtility install --acceptLicense defaultServer && installUtility install --acceptLicense /opt/javametrics.liberty.icam-1.2.1.esa
RUN /opt/ibm/wlp/usr/extension/liberty_dc/bin/config_unified_dc.sh -silent
