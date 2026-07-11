package com.ecommerce.order;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional tests running the full application against a real in-memory
 * database, with the product-service simulated by a live WireMock HTTP
 * server — the Feign client makes real HTTP calls, nothing is bean-mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void reset() {
        orderRepository.deleteAll();
        WireMock.reset();
    }

    private String orderJson(String number, long productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("orderNumber", number, "productId", productId, "quantity", quantity));
    }

    private void stubDecreaseStock(String path, int status) {
        stubFor(WireMock.put(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));
    }

    @Test
    void createOrderReservesStockOverHttpAndPersists() throws Exception {
        stubFor(WireMock.put(urlEqualTo("/api/products/1/decrease-stock"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Keyboard\",\"price\":99.99,\"stock\":7}")));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-1001", 1L, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORD-1001"));

        // Order persisted
        assertEquals(1, orderRepository.count());

        // The real HTTP call carried the right quantity
        WireMock.verify(putRequestedFor(urlEqualTo("/api/products/1/decrease-stock"))
                .withRequestBody(matchingJsonPath("$.quantity", equalTo("3"))));
    }

    @Test
    void insufficientStockReturns409AndNothingIsPersisted() throws Exception {
        stubDecreaseStock("/api/products/1/decrease-stock", 409);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-1002", 1L, 99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for product 1"));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void unknownProductReturns404AndNothingIsPersisted() throws Exception {
        stubDecreaseStock("/api/products/42/decrease-stock", 404);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-1003", 42L, 1)))
                .andExpect(status().isNotFound());

        assertEquals(0, orderRepository.count());
    }

    @Test
    void upstreamFailureReturns502AndNothingIsPersisted() throws Exception {
        stubDecreaseStock("/api/products/1/decrease-stock", 500);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-1004", 1L, 1)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Unable to reserve product stock at this time"));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void invalidOrderIsRejectedBeforeAnyStockCall() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-1005", 1L, 0)))
                .andExpect(status().isBadRequest());

        WireMock.verify(0, putRequestedFor(urlMatching("/api/products/.*")));
        assertEquals(0, orderRepository.count());
    }

    @Test
    void fullOrderLifecycleWorks() throws Exception {
        stubDecreaseStock("/api/products/1/decrease-stock", 200);

        // Create
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-2001", 1L, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        // Read
        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-2001"));

        // List
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Update
        mockMvc.perform(put("/api/orders/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("ORD-2001-B", 1L, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        // Delete
        mockMvc.perform(delete("/api/orders/" + id))
                .andExpect(status().isNoContent());
        assertEquals(0, orderRepository.count());
    }

    @Test
    void createWithClientSuppliedIdGetsServerAssignedId() throws Exception {
        stubDecreaseStock("/api/products/1/decrease-stock", 200);

        Order existing = orderRepository.save(new Order(null, "ORD-EXISTING", 1L, 1));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", existing.getId(),
                                "orderNumber", "ORD-HIJACK",
                                "productId", 1,
                                "quantity", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(existing.getId().intValue())));

        assertEquals("ORD-EXISTING",
                orderRepository.findById(existing.getId()).orElseThrow().getOrderNumber());
        assertEquals(2, orderRepository.count());
    }
}
