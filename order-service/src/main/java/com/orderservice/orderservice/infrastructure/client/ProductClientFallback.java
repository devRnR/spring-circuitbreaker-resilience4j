package com.orderservice.orderservice.infrastructure.client;

import com.orderservice.orderservice.infrastructure.client.dto.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductClientFallback implements ProductClient {

	@Override
	public ProductResponse getProduct(final Long id) {
		log.warn("⚠️ [Feign-Fallback] Product-service 응답 실패, fallback 실행 (productId={})", id);
		return new ProductResponse(
				id,
				"상품 정보를 가져올 수 없습니다",
				0,
				false
		);
	}
}
