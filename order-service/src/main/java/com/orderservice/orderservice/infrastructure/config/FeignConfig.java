package com.orderservice.orderservice.infrastructure.config;

import feign.Target;
import feign.codec.ErrorDecoder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FeignConfig {

	@Bean
	Logger.Level feignLoggerLevel() {
		return Level.ALL;
	}

	@Bean
	public ErrorDecoder errorDecoder() {
		return (methodKey, response) -> {
			if (response.status() >= 500) {
				log.warn("🚨 FeignErrorDecoder: {} - status {}", methodKey, response.status());
				return new RuntimeException("Remote service error: " + response.status());
			}
			return new ErrorDecoder.Default().decode(methodKey, response);
		};
	}

	@Bean
	public CircuitBreakerNameResolver circuitBreakerNameResolver() {
		return (String feignClientName, Target<?> target, Method method) -> feignClientName + "_" + method.getName();
	}

}
