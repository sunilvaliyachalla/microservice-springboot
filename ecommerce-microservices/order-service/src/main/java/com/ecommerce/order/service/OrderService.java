package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.dto.StockUpdateRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.exception.OrderProcessingException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Order createOrder(Order order) {
        // Prevent mass assignment: the server always assigns the id.
        order.setId(null);

        // Reserve stock in the product-service before persisting the order.
        // If stock cannot be reserved, no order is created.
        decreaseProductStock(order.getProductId(), order.getQuantity());

        return orderRepository.save(order);
    }

    public Order updateOrder(Long id, Order orderDetails) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        order.setOrderNumber(orderDetails.getOrderNumber());
        order.setProductId(orderDetails.getProductId());
        order.setQuantity(orderDetails.getQuantity());

        return orderRepository.save(order);
    }

    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        orderRepository.delete(order);
    }

    private void decreaseProductStock(Long productId, Integer quantity) {
        try {
            productClient.decreaseStock(productId, new StockUpdateRequest(quantity));
        } catch (FeignException.NotFound ex) {
            throw new OrderProcessingException(HttpStatus.NOT_FOUND,
                    "Product not found with id: " + productId);
        } catch (FeignException.Conflict ex) {
            throw new OrderProcessingException(HttpStatus.CONFLICT,
                    "Insufficient stock for product " + productId);
        } catch (FeignException ex) {
            // Upstream unavailable or unexpected response: do not create the order.
            throw new OrderProcessingException(HttpStatus.BAD_GATEWAY,
                    "Unable to reserve product stock at this time");
        }
    }
}
