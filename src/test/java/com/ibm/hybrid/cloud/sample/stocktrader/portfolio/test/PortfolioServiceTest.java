package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;

import org.eclipse.microprofile.system.test.SharedContainerConfig;
import org.eclipse.microprofile.system.test.jupiter.MicroProfileTest;
import org.eclipse.microprofile.system.test.jwt.JwtConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockserver.client.MockServerClient;

import static com.ibm.hybrid.cloud.sample.stocktrader.portfolio.test.AppConfig.mockStockQuoteClient;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.time.Instant;

import com.google.common.net.MediaType;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.PortfolioService;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Portfolio;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Quote;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Stock;

@MicroProfileTest
@SharedContainerConfig(AppConfig.class)
public class PortfolioServiceTest {
    
    private Jsonb jsonb = JsonbBuilder.create();

    @Inject
    @JwtConfig(claims = "groups=StockTrader")
    public static PortfolioService traderPortfolio;
    
    @Inject
    @JwtConfig(claims = "groups=StockViewer")
    public static PortfolioService viewerPortfolio;
    
    @Inject
    public static PortfolioService noAuthPortfolio;
    
    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println(">>> BEGIN: " + info.getDisplayName());
    }
    
    @Test
    public void createPortfolio() throws Exception {
        Portfolio andyPortfolio = traderPortfolio.createPortfolio("Andy", null);
        assertEquals(andyPortfolio, new Portfolio("Andy", 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99));
    }
    
    @Test
    public void createPortfolioAsViewer() {
        assertThrows(ForbiddenException.class, () -> viewerPortfolio.createPortfolio("viewer", null));
    }
    
    @Test
    public void createPortfolioAlreadyExists() throws Exception {
        Portfolio p = traderPortfolio.createPortfolio("alreadyExists", null);
        assertEquals(p, new Portfolio("alreadyExists", 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99));
        assertThrows(WebApplicationException.class, () -> traderPortfolio.createPortfolio("alreadyExists", null));
    }
    
    @Test
    public void getPortfolio() throws Exception {
        String owner = "getportfolio";
        Portfolio initial = new Portfolio(owner, 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99);
        Portfolio created = traderPortfolio.createPortfolio(owner, null);
        assertEquals(created, initial);
        
        Portfolio read = traderPortfolio.getPortfolio(owner, null);
        assertEquals(read, initial);
        assertEquals(created, read);
     
        Portfolio readAsViewer = viewerPortfolio.getPortfolio(owner, null);
        assertEquals(readAsViewer, initial);
        assertEquals(created, readAsViewer);
    }
    
    @Test
    public void getPortfolioDoesNotExist() {
        assertThrows(NotFoundException.class, () -> viewerPortfolio.getPortfolio("doesNotExist", null));
    }
    
    @Test
    public void getPortfolioUnauthorized() throws Exception {
        assertThrows(NotAuthorizedException.class, () -> noAuthPortfolio.getPortfolio("noAuth", null));
    }
    
    @Test
    public void updatePortfolio() throws Exception {
        String owner = "updatePortfolio";
        Portfolio p = traderPortfolio.createPortfolio(owner, null);
        assertEquals(p, new Portfolio(owner, 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99));
        
        Quote ibmQuote = new Quote("IBM", 100.0, Instant.now().toString());
        mockStockQuoteClient.when(request().withPath("/stock-quote/IBM"))
            .respond(response().withBody(jsonb.toJson(ibmQuote), MediaType.JSON_UTF_8));
        Portfolio updated = traderPortfolio.updatePortfolio(owner, "IBM", 1, null);
        assertEquals(owner, updated.getOwner());
        assertEquals(40.01, updated.getBalance(), 0.00001);
        
        updated = traderPortfolio.updatePortfolio(owner, "IBM", 3, null);
        assertEquals(owner, updated.getOwner());
        assertEquals(30.02, updated.getBalance(), 0.00001);
    }
    
    @Test
    public void updatePortfolioAsViewer() {
        assertThrows(ForbiddenException.class, () -> viewerPortfolio.updatePortfolio("viewer", "IBM", 1, null));
    }
    
    @Test
    public void deletePortfolio() throws Exception {
        String owner = "deletePortfolio";
        Portfolio expected = new Portfolio(owner, 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99);
        Portfolio created = traderPortfolio.createPortfolio(owner, null);
        assertEquals(created, expected);
        
        Portfolio read = viewerPortfolio.getPortfolio(owner, null);
        assertEquals(created, expected);
        assertEquals(created, read);
        
        Portfolio deleted = traderPortfolio.deletePortfolio(owner);
        assertEquals(created, expected);
        assertEquals(created, deleted);
        
        assertThrows(NotFoundException.class, () -> viewerPortfolio.getPortfolio("doesNotExist", null));
    }
    
    @Test
    public void deletePortfolioDoesNotExist() {
        assertThrows(NotFoundException.class, () -> viewerPortfolio.getPortfolio("deletePortfolioDoesNotExist", null));
    }
    
}
