FROM websphere-liberty:microProfile
COPY server.xml /config/server.xml
COPY Portfolio.war /config/apps/Portfolio.war
