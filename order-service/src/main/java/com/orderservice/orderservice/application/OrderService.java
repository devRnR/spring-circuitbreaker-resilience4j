package com.orderservice.orderservice.application;

import com.orderservice.orderservice.infrastructure.client.ProductClient;
import com.orderservice.orderservice.infrastructure.client.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	private final ProductClient productClient;

	public String getOrderWithProduct(Long orderId, Long productId) {
		log.info("🔄 [OrderService] Product 조회 시도 - OrderId={}, ProductId={}", orderId, productId);

		ProductResponse product = productClient.getProduct(productId);

		log.info("✅ [OrderService] Product 조회 성공 - {}", product.toString());

		return product.toString();
	}

}
