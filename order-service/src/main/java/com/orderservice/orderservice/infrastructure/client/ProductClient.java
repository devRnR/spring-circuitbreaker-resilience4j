package com.orderservice.orderservice.infrastructure.client;

import com.orderservice.orderservice.infrastructure.client.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
		name = "product-service",
		url = "${product.service.url}",
		fallback = ProductClientFallback.class
)
public interface ProductClient {

	@GetMapping("/api/v1/products/{id}")
	ProductResponse getProduct(
			@PathVariable(name = "id") Long id
	);
}
