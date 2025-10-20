# MSA 환경에서 장애 전파를 막는 Circuit Breaker 패턴 (Spring Cloud OpenFeign + Resilience4j)

2025.10.20 / devo

목차

- 들어가며
- 프로젝트 구조
- 프로젝트 실행
- Circuit Breaker란?
- Resilience4j와 FeignClient 연동하기
- 예제: 장애 전파 차단 실습
- 나의 인사이트
- 참고 자료

## 들어가며

MSA 구조에서 하나의 서비스 장애가 다른 서비스로 전파되면 전체 시스템이 불안정해질 수 있다. 이럴때 Circuit Breaker 패턴을 사용하면 장애 전파를 제어할 수 있다.

이번 포스트에서는 Spring Cloud OpenFeign과 Resilience4j를 이용해 **“한 서비스 장애가 전체 장애로 번지지 않도록 하는 방법”**을 직접 예제 코드로 실습한다.

예제 코드에서는 Spring Boot 애플리케이션 2개를 Docker로 띄워서 통신하게 한 뒤, 한쪽에서 장애를 발생시켜 Circuit Breaker가 어떻게 작동하는지 살펴본다.

중점적으로 보면 좋은 내용은 다음과 같다.

- Circuit Breaker 패턴의 핵심 개념과 동작 방식 (CLOSED → OPEN → HALF-OPEN)
- Spring Cloud CircuitBreaker + Resilience4j 설정 방법
- FeignClient와 CircuitBreaker의 연동 포인트
- 장애 전파를 차단하는 fallback 전략 구현 방법

## 프로젝트 구조

```text

├── product-service
│   ├── src
│   ├── build.gradle
│   ├── settings.gradle
│   └── Dockerfile
├── order-service
│   ├── src
│   ├── build.gradle
│   ├── settings.gradle
│   └── Dockerfile
└── docker-compose.yml
```

## 2. 프로젝트 실행


```bash
docker-compose up --build
```

2개의 서비스가 모두 정상적으로 띄워졌다면, 다음 URL로 health check를 해보면 된다.

```text
order-service: http://localhost:8080/actuator/health
product-service: http://localhost:8081/actuator/health
```

## Circuit Breaker란?

정의: 장애가 반복되는 외부 호출을 차단해 전체 시스템으로 장애가 확산되는 것을 방지하는 패턴

상태 전이:

- CLOSED: 정상 동작 중
- OPEN: 일정 실패율 이상 시 차단 (외부 호출 막음)
- HALF-OPEN: 일부 요청만 허용하여 복구 여부 판단

## feignclient 와 resilience4j 연동하기

환경 구성

- Spring Boot 애플리케이션 2개
  - product-service
  - order-service

## order-service

### dependencies

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'  # Resilience4j가 Spring Cloud CircuitBreaker 추상화 안에 통합
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
}
```

최근 Spring Cloud CircuitBreaker 구조에서는 Resilience4j가 Spring Cloud CircuitBreaker 추상화 안에 통합되어 있기 때문에, 예전처럼 별도의 의존성을 Feign과 직접 연결하지 않아도 된다.

1. Before (Spring Cloud CircuitBreaker 통합 전)

- Feign + Resilience4j 직접 의존성 추가
- @EnableCircuitBreaker + Resilience4j 설정 필요
- Feign 호출 시 CircuitBreaker를 수동으로 감싸거나 fallback 지정

2. After (Spring Cloud CircuitBreaker 통합)

- Spring Cloud에서 CircuitBreaker 추상화 제공
- Resilience4j를 구현체로 선택만 하면 Feign에 자동 적용 가능
- spring-cloud-starter-openfeign + spring-cloud-starter-circuitbreaker-resilience4j 의존성으로 충분
- FeignClient에 fallback만 지정하면 CircuitBreaker가 자동으로 호출 감싸기
- Resilience4j 관련 설정은 application.yml에서 관리
- 별도의 수동 의존성 관리 필요 없음

### application.yaml

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true # Circuit Breaker 활성화
```

### FeignClient 설정

```java
@FeignClient(
    name = "product-service",
    url = "${product.service.url}",
    fallback = ProductClientFallback.class
)
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    public ProductResponse getProduct(@PathVariable(name = "id") Long id);
}
```

```java
@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ProductResponse getProduct(
        @PathVariable(name = "id") Long id
    ) {
        log.warn("⚠️ [Feign-Fallback] Product-service 응답 실패, fallback 실행 (productId={})", id);
        return new ProductResponse(id,"상품 정보를 가져올 수 없습니다",0,false);
    }
}
```

### CircuitBreaker 설정

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 3000
        permittedNumberOfCallsInHalfOpenState: 3
        maxWaitDurationInHalfOpenState: 3000
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 1000
    instances:
      product-service_getProduct:
        baseConfig: default
```

항목에 대한 설명은 다음과 같다.

| 항목                                      | 값           | 설명                                                                                      |
| --------------------------------------- | ----------- | --------------------------------------------------------------------------------------- |
| `failureRateThreshold`                  | 50          | 실패 비율 임계치 (%)<br>예: 최근 호출 중 실패율이 50% 이상이면 **OPEN** 상태로 전환                               |
| `slowCallRateThreshold`                 | 50          | 느린 호출 비율 임계치 (%)<br>예: 최근 호출 중 지연이 `slowCallDurationThreshold` 이상인 비율이 50% 이상이면 OPEN 가능 |
| `slowCallDurationThreshold`             | 3000        | 느린 호출 기준(ms)<br>3000ms 이상이면 느린 호출로 간주                                                   |
| `permittedNumberOfCallsInHalfOpenState` | 3           | HALF_OPEN 상태에서 **시험 호출 허용 횟수**<br>3번 중 성공하면 CLOSED, 실패하면 다시 OPEN                        |
| `maxWaitDurationInHalfOpenState`        | 3000        | HALF_OPEN 상태에서 **다음 호출까지 최대 대기 시간(ms)**                                                 |
| `slidingWindowType`                     | COUNT_BASED | 실패율 계산 방식<br>`COUNT_BASED` → 최근 N개의 호출 기준, `TIME_BASED` → 일정 시간 동안 호출 기준                |
| `slidingWindowSize`                     | 10          | sliding window 크기<br>COUNT_BASED이면 최근 10번 호출 기준으로 실패율 계산                                |
| `minimumNumberOfCalls`                  | 5           | Circuit Breaker가 상태 변경 판단을 시작하는 최소 호출 수<br>최소 5번 호출 후 실패율 계산 시작                         |
| `waitDurationInOpenState`               | 1000        | OPEN 상태 유지 시간(ms)<br>1초 후 HALF_OPEN으로 전환                                                |


## product-service

### dependencies

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

### product 조회 API

```java
@GetMapping("/api/v1/products/{id}")
public ProductResponse getProduct(@PathVariable(name = "id") Long id) {
    return productService.getProduct(id);
}
```

### product 조회 API 장애 발생

```java
@Slf4j
@Service
public class ProductService {
	private final Random random = new Random();

	public ProductResponse getProduct(Long id) {
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
```

simulateRandomFailure 메서드는 50% 확률로 장애를 발생시킨다. 이는 circuit breaker가 작동하는 테스트를 위해 추가한 것이다.

## 테스트 실행

테스트는 order-service 에서 product-service로 20회 호출하도록 했으며, /actuator/circuitbreakers 엔드포인트를 통해 circuit breaker의 상태를 확인할 수 있다.

### Application logging

```bash
docker-compose logs -f product-service
docker-compose logs -f order-service
```

### request

```bash
for i in {1..10}; do
    curl -s curl http://localhost:8080/api/v1/orders/1/product/1
done
```

### logs

- product-service

```bash
product-service  | 2025-10-20T01:16:15.353Z  INFO 1 --- [product-service] [nio-8081-exec-8] c.p.product.service.ProductService       : 📦 [Product Service] 상품 조회 요청 - ProductId: 1
product-service  | 2025-10-20T01:16:15.353Z  INFO 1 --- [product-service] [nio-8081-exec-8] c.p.product.service.ProductService       : ✅ [Product Service] 상품 조회 성공 - ProductId: 1, Name: 상품 이름 1, Price: 10000
product-service  | 2025-10-20T01:16:15.400Z  INFO 1 --- [product-service] [nio-8081-exec-9] c.p.product.service.ProductService       : 📦 [Product Service] 상품 조회 요청 - ProductId: 1
product-service  | 2025-10-20T01:16:15.400Z ERROR 1 --- [product-service] [nio-8081-exec-9] c.p.product.service.ProductService       : 💥 [Product Service] 랜덤 장애 발생! - 50% 확률로 실패
```

- order-service

```bash
⚠️ [Feign-Fallback] Product-service 응답 실패, fallback 실행 (productId=1)
order-service  | 2025-10-20T01:16:15.337Z  INFO 1 --- [order-service] [nio-8080-exec-8] c.o.o.application.OrderService           : ✅ [OrderService] Product 조회 성공 - ProductResponse(id=1, name=상품 정보를 가져올 수 없습니다, price=0, available=false)
order-service  | 2025-10-20T01:16:15.351Z  INFO 1 --- [order-service] [io-8080-exec-10] c.o.o.application.OrderService           : 🔄 [OrderService] Product 조회 시도 - OrderId=1, ProductId=1
order-service  | 2025-10-20T01:16:15.351Z DEBUG 1 --- [order-service] [pool-2-thread-1] feign.template.Template                  : Explicit slash decoding specified, decoding all slashes in uri
order-service  | 2025-10-20T01:16:15.383Z DEBUG 1 --- [order-service] [io-8080-exec-10] i.g.r.t.internal.TimeLimiterImpl         : Event SUCCESS published: 2025-10-20T01:16:15.383708471Z[Etc/UTC]: TimeLimiter 'product-service_getProduct' recorded a successful call.
order-service  | 2025-10-20T01:16:15.383Z DEBUG 1 --- [order-service] [io-8080-exec-10] i.g.r.c.i.CircuitBreakerStateMachine     : CircuitBreaker 'product-service_getProduct' succeeded:
order-service  | 2025-10-20T01:16:15.383Z  INFO 1 --- [order-service] [io-8080-exec-10] c.o.o.i.c.CircuitBreakerLogger           : Circuit breaker success: 2025-10-20T01:16:15.383904179Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' recorded a successful call. Elapsed time: 32 ms
order-service  | 2025-10-20T01:16:15.384Z DEBUG 1 --- [order-service] [io-8080-exec-10] i.g.r.c.i.CircuitBreakerStateMachine     : Event SUCCESS published: 2025-10-20T01:16:15.383904179Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' recorded a successful call. Elapsed time: 32 ms

...

order-service  | 2025-10-20T01:16:15.403Z  WARN 1 --- [order-service] [nio-8080-exec-2] c.o.o.i.client.ProductClientFallback     : ⚠️ [Feign-Fallback] Product-service 응답 실패, fallback 실행 (productId=1)
order-service  | 2025-10-20T01:16:15.404Z  INFO 1 --- [order-service] [nio-8080-exec-2] c.o.o.application.OrderService           : ✅ [OrderService] Product 조회 성공 - ProductResponse(id=1, name=상품 정보를 가져올 수 없습니다, price=0, available=false)
order-service  | 2025-10-20T01:16:15.416Z  INFO 1 --- [order-service] [nio-8080-exec-4] c.o.o.application.OrderService           : 🔄 [OrderService] Product 조회 시도 - OrderId=1, ProductId=1
order-service  | 2025-10-20T01:16:15.417Z DEBUG 1 --- [order-service] [pool-2-thread-1] feign.template.Template                  : Explicit slash decoding specified, decoding all slashes in uri
order-service  | 2025-10-20T01:16:15.421Z  WARN 1 --- [order-service] [pool-2-thread-1] c.o.o.infrastructure.config.FeignConfig  : 🚨 FeignErrorDecoder: ProductClient#getProduct(Long) - status 500
order-service  | 2025-10-20T01:16:15.422Z DEBUG 1 --- [order-service] [nio-8080-exec-4] i.g.r.t.internal.TimeLimiterImpl         : Event ERROR published: 2025-10-20T01:16:15.422241971Z[Etc/UTC]: TimeLimiter 'product-service_getProduct' recorded an error: 'java.lang.RuntimeException: Remote service error: 500'
order-service  | 2025-10-20T01:16:15.422Z DEBUG 1 --- [order-service] [nio-8080-exec-4] i.g.r.c.i.CircuitBreakerStateMachine     : CircuitBreaker 'product-service_getProduct' recorded an exception as failure:
...
order-service  | 2025-10-20T01:16:15.422Z  INFO 1 --- [order-service] [nio-8080-exec-4] c.o.o.i.c.CircuitBreakerLogger           : Circuit breaker error: 2025-10-20T01:16:15.422697013Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' recorded an error: 'java.lang.RuntimeException: Remote service error: 500'. Elapsed time: 5 ms
order-service  | 2025-10-20T01:16:15.422Z DEBUG 1 --- [order-service] [nio-8080-exec-4] i.g.r.c.i.CircuitBreakerStateMachine     : Event ERROR published: 2025-10-20T01:16:15.422697013Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' recorded an error: 'java.lang.RuntimeException: Remote service error: 500'. Elapsed time: 5 ms
order-service  | 2025-10-20T01:16:15.423Z  INFO 1 --- [order-service] [nio-8080-exec-4] c.o.o.i.c.CircuitBreakerLogger           : Circuit breaker failure rate exceeded: 2025-10-20T01:16:15.423345096Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' exceeded failure rate threshold. Current failure rate: 80.0
order-service  | 2025-10-20T01:16:15.423Z DEBUG 1 --- [order-service] [nio-8080-exec-4] i.g.r.c.i.CircuitBreakerStateMachine     : Event FAILURE_RATE_EXCEEDED published: 2025-10-20T01:16:15.423345096Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' exceeded failure rate threshold. Current failure rate: 80.0
order-service  | 2025-10-20T01:16:15.425Z  INFO 1 --- [order-service] [nio-8080-exec-4] c.o.o.i.c.CircuitBreakerLogger           : Circuit breaker state transition: 2025-10-20T01:16:15.425174721Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' changed state from CLOSED to OPEN
order-service  | 2025-10-20T01:16:15.425Z DEBUG 1 --- [order-service] [nio-8080-exec-4] i.g.r.c.i.CircuitBreakerStateMachine     : Event STATE_TRANSITION published: 2025-10-20T01:16:15.425174721Z[Etc/UTC]: CircuitBreaker 'product-service_getProduct' changed state from CLOSED to OPEN

```

## 결과 분석

order-service에서 product-service로 호출에 대한 circuit breaker의 상태를 확인하면 다음과 같다.

```jsaon
// http://localhost:8080/actuator/circuitbreakers

{
  "circuitBreakers": {
    "product-service_getProduct": {
      "failureRate": "80.0%",
      "slowCallRate": "0.0%",
      "failureRateThreshold": "50.0%",
      "slowCallRateThreshold": "50.0%",
      "bufferedCalls": 5,
      "failedCalls": 4,
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "notPermittedCalls": 15,
      "state": "OPEN"
    }
  }
}

```

테스트 결과를 확인하면 현재 실패율은 80%로 설정한 50% 이상이므로 circuit breaker가 작동하여 product-service의 장애가 order-service에 전파되지 않는 것을 확인할 수 있다.

- 결과 요약
  - 장애 발생 시 Feign 요청이 자동으로 차단되고 fallback 동작 실행
  - 이후 일정 시간 경과 후 HALF-OPEN → CLOSED 복귀

## 나의 인사이트

- MSA 환경에서 서비스 간 의존성이 높을수록 장애 전파 위험이 크고 치명적일 수 있다.
- Circuit Breaker 패턴을 사용하면 장애 전파를 제어할 수 있고, 로깅 및 알림 서비스와 통합하여 장애 상황을 모니터링할 수 있다.
- Resilience4j는 Feign과 매우 자연스럽게 통합되며, **“서비스 독립성 확보”**에 큰 도움을 줄 수 있다.


## 참고자료

- [Spring Cloud CircuitBreaker 공식 문서](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/)
- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Spring Cloud CircuitBreaker + Resilience4j 통합 문서](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/#_resilience4j)
