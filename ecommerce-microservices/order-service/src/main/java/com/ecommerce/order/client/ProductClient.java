package com.ecommerce.order.client;

import com.ecommerce.order.dto.StockUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client for the product-service, resolved via Eureka service discovery.
 * The optional product-service.url property overrides discovery with a fixed
 * URL (used by tests; leave unset in normal deployments).
 */
@FeignClient(name = "product-service", url = "${product-service.url:}")
public interface ProductClient {

    @PutMapping("/api/products/{id}/decrease-stock")
    void decreaseStock(@PathVariable("id") Long id, @RequestBody StockUpdateRequest request);
}
