package com.productservice.product.service;

import com.productservice.product.controller.v1.dto.ProductResponse;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductService {
	private final Random random = new Random();

	public ProductResponse getProductById(Long id) {
		log.info("📦 [Product Service] 상품 조회 요청 - ProductId: {}", id);

		simulateRandomFailure();

		ProductResponse response = new ProductResponse(
				id,
				"상품 이름 " + id,
				10000,
				true
		);

		log.info("✅ [Product Service] 상품 조회 성공 - ProductId: {}, Name: {}, Price: {}",
				id, response.name(), response.price());
		return response;
	}

	private void simulateRandomFailure() {
		if(random.nextInt(2) == 0) {  // 50% 확률로 실패 (2번 중 1번)
			log.error("💥 [Product Service] 랜덤 장애 발생! - 50% 확률로 실패");
			throw new RuntimeException("랜덤 장애 발생: product-service 애플리케이션 다운");
		}
	}
}
