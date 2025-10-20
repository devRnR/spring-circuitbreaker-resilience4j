package com.orderservice.orderservice.infrastructure.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CircuitBreakerLogger {
	private final CircuitBreakerRegistry circuitBreakerRegistry;

	public CircuitBreakerLogger(CircuitBreakerRegistry circuitBreakerRegistry) {
		this.circuitBreakerRegistry = circuitBreakerRegistry;
	}

	@PostConstruct
	public void init() {
		circuitBreakerRegistry.circuitBreaker("product-service_getProduct")
				.getEventPublisher()
				.onEvent(this::logEvent);
	}

	private void logEvent(CircuitBreakerEvent event) {
		switch (event.getEventType()) {
			case STATE_TRANSITION:
				log.info("Circuit breaker state transition: {}", event);
				break;
			case FAILURE_RATE_EXCEEDED:
				log.info("Circuit breaker failure rate exceeded: {}", event);
				break;
			case ERROR:
				log.info("Circuit breaker error: {}", event);
				break;
			case SUCCESS:
				log.info("Circuit breaker success: {}", event);
				break;
			default:
				log.info("Other: {}", event);
		}
	}
}
