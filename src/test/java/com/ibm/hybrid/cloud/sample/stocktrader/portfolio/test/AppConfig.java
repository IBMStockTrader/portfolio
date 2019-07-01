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
            //.withEnv("STOCK_QUOTE_URL", "http://mockserver:" + MockServerContainer.PORT + "/stockquote")
            .withEnv("JDBC_HOST", "postgre")
            .withEnv("JDBC_PORT", "5432")
            .withEnv("JDBC_DB", "Sample")
            .withEnv("JDBC_ID", "dbuser")
            .withEnv("JDBC_PASSWORD", "dbpass");
    
    @Container
    public static PostgreSQLContainer db = new PostgreSQLContainer<>()
        .withDatabaseName("Sample")
        .withUsername("dbuser")
        .withPassword("dbpass")
        .withNetworkAliases("postgre")
        .withInitScript("createTables.ddl");
    
    @Container
    public static MockServerContainer mockServer = new MockServerContainer()
        .withNetworkAliases("mockserver");
    
    public static MockServerClient mockStockQuoteClient;
    
    @Override
    public void startContainers() {
        Arrays.asList(db, mockServer).parallelStream().forEach(GenericContainer::start);
        app.start();
        mockStockQuoteClient = new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort());
    }

}
