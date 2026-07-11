package com.ecommerce.product;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional tests running the full application (controller -> service ->
 * repository) against a real in-memory database. Nothing is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
    }

    private Product persist(String name, String price, int stock) {
        return productRepository.save(new Product(null, name, new BigDecimal(price), stock));
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        // Create
        MvcResult created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Keyboard", "price", 99.99, "stock", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.stock").value(10))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Read
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"));

        // Update
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Mechanical Keyboard", "price", 149.99, "stock", 8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.stock").value(8));

        // List
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Delete
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
        assertEquals(0, productRepository.count());
    }

    @Test
    void createWithClientSuppliedIdDoesNotOverwriteExistingRow() throws Exception {
        Product existing = persist("Original", "10.00", 5);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", existing.getId(),
                                "name", "Hijacked",
                                "price", 0.01,
                                "stock", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(existing.getId().intValue())));

        Product untouched = productRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Original", untouched.getName());
        assertEquals(2, productRepository.count());
    }

    @Test
    void validationRejectsInvalidPayloads() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "", "price", 5, "stock", 1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X", "price", -1, "stock", 1))))
                .andExpect(status().isBadRequest());

        assertEquals(0, productRepository.count());
    }

    @Test
    void decreaseStockEndpointReducesPersistedStock() throws Exception {
        Product product = persist("Keyboard", "99.99", 10);

        mockMvc.perform(put("/api/products/" + product.getId() + "/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(6));

        assertEquals(6, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void decreaseStockReturns409AndLeavesStockUntouchedWhenInsufficient() throws Exception {
        Product product = persist("Keyboard", "99.99", 2);

        mockMvc.perform(put("/api/products/" + product.getId() + "/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 3))))
                .andExpect(status().isConflict());

        assertEquals(2, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void decreaseStockReturns404ForUnknownProduct() throws Exception {
        mockMvc.perform(put("/api/products/99999/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void decreaseStockRejectsZeroQuantity() throws Exception {
        Product product = persist("Keyboard", "99.99", 5);

        mockMvc.perform(put("/api/products/" + product.getId() + "/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest());

        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    /**
     * Live concurrency test of the pessimistic lock: many threads decrement
     * the same product at once; exactly `stock` decrements may succeed and
     * the final stock must be zero — never negative (no overselling).
     */
    @Test
    void concurrentDecrementsNeverOversell() throws Exception {
        int initialStock = 5;
        int attempts = 10;
        Product product = persist("Limited Edition", "199.99", initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    productService.decreaseStock(product.getId(), 1);
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        executor.shutdown();

        assertEquals(initialStock, successes.get(), "exactly `stock` decrements may succeed");
        assertEquals(attempts - initialStock, conflicts.get());
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock(),
                "stock must end at exactly zero, never negative");
    }
}
