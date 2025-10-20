package com.orderservice.orderservice.presentation.v1;

import com.orderservice.orderservice.application.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@GetMapping("/{orderId}/product/{productId}")
	public String getOrderProduct(
		@PathVariable(name = "orderId") Long orderId,
		@PathVariable(name = "productId") Long productId
	) {
		return orderService.getOrderWithProduct(orderId, productId);
	}

}
