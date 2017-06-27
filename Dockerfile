FROM websphere-liberty:microProfile
COPY server.xml /config/server.xml
COPY build/libs/portfolio.war /config/apps/Portfolio.war
