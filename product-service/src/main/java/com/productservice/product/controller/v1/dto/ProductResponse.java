package com.productservice.product.controller.v1.dto;

public record ProductResponse(
		Long id,
		String name,
		int price,
		boolean available
) {
}
