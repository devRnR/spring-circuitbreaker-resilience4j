# --------------------------
# 1️⃣ Build Stage
# --------------------------
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

# Gradle 파일 먼저 복사 → 종속성 캐시 활용
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/

# 의존성만 먼저 다운로드 (캐시 최적화)
RUN gradle dependencies --no-daemon

# 소스 코드 복사
COPY src/ src/

# JAR 빌드 (테스트 제외)
RUN gradle bootJar -x test --no-daemon

# --------------------------
# 2️⃣ Runtime Stage
# --------------------------
FROM openjdk:21-jdk-slim

WORKDIR /app

# curl 설치 (healthcheck 용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 빌드 결과 JAR 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
