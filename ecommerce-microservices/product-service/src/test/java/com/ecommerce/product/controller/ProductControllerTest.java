package com.ecommerce.product.controller;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private Product product(Long id, String name, String price, int stock) {
        return new Product(id, name, new BigDecimal(price), stock);
    }

    @Test
    void getAllProductsReturnsList() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(product(1L, "Keyboard", "99.99", 10)));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[0].stock").value(10));
    }

    @Test
    void getProductByIdReturns200WhenFound() throws Exception {
        when(productService.getProductById(1L)).thenReturn(Optional.of(product(1L, "Keyboard", "99.99", 10)));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void getProductByIdReturns404WhenMissing() throws Exception {
        when(productService.getProductById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProductReturns201() throws Exception {
        when(productService.createProduct(any(Product.class)))
                .thenReturn(product(1L, "Keyboard", "99.99", 10));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Keyboard", "price", 99.99, "stock", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createProductRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "  ", "price", 99.99, "stock", 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductRejectsNegativePrice() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Keyboard", "price", -5, "stock", 10))))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductRejectsNegativeStock() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Keyboard", "price", 99.99, "stock", -1))))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any());
    }

    @Test
    void updateProductReturns200() throws Exception {
        when(productService.updateProduct(eq(1L), any(Product.class)))
                .thenReturn(product(1L, "New", "20.00", 7));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "New", "price", 20.00, "stock", 7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void updateProductReturns404WhenMissing() throws Exception {
        when(productService.updateProduct(eq(42L), any(Product.class)))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 42"));

        mockMvc.perform(put("/api/products/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "New", "price", 20.00, "stock", 7))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 42"));
    }

    @Test
    void deleteProductReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }

    @Test
    void decreaseStockReturns200() throws Exception {
        when(productService.decreaseStock(1L, 3)).thenReturn(product(1L, "Keyboard", "99.99", 7));

        mockMvc.perform(put("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void decreaseStockReturns409WhenInsufficient() throws Exception {
        when(productService.decreaseStock(1L, 99))
                .thenThrow(new InsufficientStockException("Insufficient stock for product 1"));

        mockMvc.perform(put("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 99))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for product 1"));
    }

    @Test
    void decreaseStockRejectsNonPositiveQuantity() throws Exception {
        mockMvc.perform(put("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest());

        verify(productService, never()).decreaseStock(anyLong(), anyInt());
    }

    @Test
    void unexpectedErrorReturnsSanitized500() throws Exception {
        when(productService.getAllProducts()).thenThrow(new RuntimeException("secret internal detail"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
