package com.ecommerce.order.client;

import com.ecommerce.order.dto.StockUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client for the product-service, resolved via Eureka service discovery.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @PutMapping("/api/products/{id}/decrease-stock")
    void decreaseStock(@PathVariable("id") Long id, @RequestBody StockUpdateRequest request);
}
