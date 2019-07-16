package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.test;

import java.util.Arrays;

import org.eclipse.microprofile.system.test.SharedContainerConfiguration;
import org.mockserver.client.MockServerClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.microprofile.MicroProfileApplication;
import org.testcontainers.junit.jupiter.Container;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client.StockQuoteClient;

@SuppressWarnings("rawtypes")
public class AppConfig implements SharedContainerConfiguration {
    
    @Container
    public static MicroProfileApplication app = new MicroProfileApplication<>()//
            .withAppContextRoot("/portfolio")//
            .withReadinessPath("/portfolio/health/readiness", 60) // eventually defaulted to MP 2.0 health check
            .withEnv("WATSON_PWD", "unused")
            .withEnv("JWT_AUDIENCE", "unused")
            .withEnv("JWT_ISSUER", "unused")
            .withEnv("MQ_PORT", "1234")
            .withMpRestClient(StockQuoteClient.class, "http://mockserver:" + MockServerContainer.PORT + "/stock-quote")
            .withEnv("JDBC_HOST", "db2")
            .withEnv("JDBC_PORT", "50000")
            .withEnv("JDBC_DB", "Sample")
            .withEnv("JDBC_ID", "dbuser")
            .withEnv("JDBC_PASSWORD", "dbpass");
    
    @Container
    public static Db2Container db = new Db2Container()
        .withDatabaseName("Sample")
        .withUsername("dbuser")
        .withPassword("dbpass")
        .withNetworkAliases("db2")
        .acceptLicense()
        .withInitScript("createTables.ddl");
    
    @Container
    public static MockServerContainer mockServer = new MockServerContainer()
        .withNetworkAliases("mockserver");
    
    public static MockServerClient mockStockQuoteClient;
    
    @Override
    public void startContainers() {
        // All services can be started in parallel as long as their readiness checks do not depend on each other
        Arrays.asList(db, mockServer, app).parallelStream().forEach(GenericContainer::start);
        mockStockQuoteClient = new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort());
    }

}
