package com.ecommerce.order.controller;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.exception.GlobalExceptionHandler;
import com.ecommerce.order.exception.OrderProcessingException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllOrdersReturnsList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(new Order(1L, "ORD-1", 1L, 2)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-1"));
    }

    @Test
    void getOrderByIdReturns200WhenFound() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(new Order(1L, "ORD-1", 1L, 2)));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void getOrderByIdReturns404WhenMissing() throws Exception {
        when(orderService.getOrderById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrderReturns201() throws Exception {
        when(orderService.createOrder(any(Order.class))).thenReturn(new Order(1L, "ORD-1", 1L, 2));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", "ORD-1", "productId", 1, "quantity", 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createOrderRejectsZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", "ORD-1", "productId", 1, "quantity", 0))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void createOrderRejectsBlankOrderNumber() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", " ", "productId", 1, "quantity", 2))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void createOrderPropagatesConflictFromService() throws Exception {
        when(orderService.createOrder(any(Order.class)))
                .thenThrow(new OrderProcessingException(HttpStatus.CONFLICT, "Insufficient stock for product 1"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", "ORD-1", "productId", 1, "quantity", 99))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for product 1"));
    }

    @Test
    void updateOrderReturns200() throws Exception {
        when(orderService.updateOrder(eq(1L), any(Order.class))).thenReturn(new Order(1L, "ORD-2", 2L, 5));

        mockMvc.perform(put("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", "ORD-2", "productId", 2, "quantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-2"));
    }

    @Test
    void updateOrderReturns404WhenMissing() throws Exception {
        when(orderService.updateOrder(eq(42L), any(Order.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 42"));

        mockMvc.perform(put("/api/orders/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderNumber", "ORD-2", "productId", 2, "quantity", 5))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOrderReturns204() throws Exception {
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());

        verify(orderService).deleteOrder(1L);
    }
}
