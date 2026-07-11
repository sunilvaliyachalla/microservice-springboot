package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.dto.StockUpdateRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.exception.OrderProcessingException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderService orderService;

    private Request feignRequest() {
        return Request.create(Request.HttpMethod.PUT, "/api/products/1/decrease-stock",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
    }

    private Order order(Long id, String number, Long productId, int quantity) {
        return new Order(id, number, productId, quantity);
    }

    @Test
    void getAllOrdersReturnsAll() {
        when(orderRepository.findAll()).thenReturn(List.of(order(1L, "ORD-1", 1L, 2)));

        assertEquals(1, orderService.getAllOrders().size());
    }

    @Test
    void getOrderByIdReturnsOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order(1L, "ORD-1", 1L, 2)));

        assertTrue(orderService.getOrderById(1L).isPresent());
    }

    @Test
    void createOrderReservesStockBeforeSaving() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = orderService.createOrder(order(null, "ORD-1", 1L, 3));

        InOrder inOrder = inOrder(productClient, orderRepository);
        inOrder.verify(productClient).decreaseStock(eq(1L), any(StockUpdateRequest.class));
        inOrder.verify(orderRepository).save(any(Order.class));
        assertEquals("ORD-1", created.getOrderNumber());
    }

    @Test
    void createOrderForcesServerAssignedId() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = orderService.createOrder(order(999L, "ORD-1", 1L, 3));

        assertNull(created.getId(), "client-supplied id must be discarded (mass-assignment protection)");
    }

    @Test
    void createOrderSendsRequestedQuantityToProductService() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(order(null, "ORD-1", 7L, 4));

        verify(productClient).decreaseStock(eq(7L),
                argThat((StockUpdateRequest r) -> r.getQuantity() == 4));
    }

    @Test
    void createOrderMapsProductNotFoundTo404AndDoesNotSave() {
        doThrow(new FeignException.NotFound("not found", feignRequest(), null, Collections.emptyMap()))
                .when(productClient).decreaseStock(eq(1L), any(StockUpdateRequest.class));

        OrderProcessingException ex = assertThrows(OrderProcessingException.class,
                () -> orderService.createOrder(order(null, "ORD-1", 1L, 3)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderMapsInsufficientStockTo409AndDoesNotSave() {
        doThrow(new FeignException.Conflict("conflict", feignRequest(), null, Collections.emptyMap()))
                .when(productClient).decreaseStock(eq(1L), any(StockUpdateRequest.class));

        OrderProcessingException ex = assertThrows(OrderProcessingException.class,
                () -> orderService.createOrder(order(null, "ORD-1", 1L, 3)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderMapsUpstreamFailureTo502AndDoesNotSave() {
        doThrow(new FeignException.ServiceUnavailable("down", feignRequest(), null, Collections.emptyMap()))
                .when(productClient).decreaseStock(eq(1L), any(StockUpdateRequest.class));

        OrderProcessingException ex = assertThrows(OrderProcessingException.class,
                () -> orderService.createOrder(order(null, "ORD-1", 1L, 3)));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderUpdatesFields() {
        Order existing = order(1L, "ORD-1", 1L, 2);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order updated = orderService.updateOrder(1L, order(null, "ORD-2", 2L, 5));

        assertEquals("ORD-2", updated.getOrderNumber());
        assertEquals(2L, updated.getProductId());
        assertEquals(5, updated.getQuantity());
    }

    @Test
    void updateOrderThrowsWhenMissing() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrder(42L, order(null, "ORD-2", 2L, 5)));
    }

    @Test
    void deleteOrderDeletesExisting() {
        Order existing = order(1L, "ORD-1", 1L, 2);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(existing));

        orderService.deleteOrder(1L);

        verify(orderRepository).delete(existing);
    }

    @Test
    void deleteOrderThrowsWhenMissing() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.deleteOrder(42L));
        verify(orderRepository, never()).delete(any());
    }
}
