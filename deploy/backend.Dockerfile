# syntax=docker/dockerfile:1
#
# 백엔드(Spring Boot) 프로덕션 이미지 — 멀티스테이지 빌드.
#
# 1단계(build): JDK 21 + Gradle 로 bootJar 를 만든다. 소스와 빌드 도구는 이 단계에만
#   있고 최종 이미지에는 포함되지 않는다(이미지 크기·공격 표면 축소).
# 2단계(run):  JRE 21 만 있는 가벼운 런타임에 jar 하나만 복사해 실행한다.
#
# 빌드 컨텍스트는 backend/ 디렉터리 기준이다. (compose 의 build.context 참고)

# ---- 1단계: 빌드 ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 스크립트/래퍼만 먼저 복사해 의존성을 내려받으면,
# 소스만 바뀐 재빌드에서 이 레이어가 캐시로 재사용된다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# 소스 복사 후 실행 가능한 fat jar 생성. 테스트는 CI 에서 돌리므로 이미지 빌드에서는 제외.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- 2단계: 런타임 ----
FROM eclipse-temurin:21-jre-jammy AS run
WORKDIR /app

# root 가 아닌 전용 사용자로 실행(컨테이너 탈출 시 피해 최소화).
RUN groupadd --system app && useradd --system --gid app --home /app app

# 빌드 단계에서 만든 jar 만 가져온다.
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown -R app:app /app
USER app

# 컨테이너 내부 노출 포트(도커 네트워크 전용, 호스트로 publish 하지 않음).
EXPOSE 8080

# 컨테이너 메모리 한도를 JVM 이 인식하도록 하고(-XX:+UseContainerSupport 는 기본 on),
# 프로파일은 compose 의 환경변수(SPRING_PROFILES_ACTIVE)로 주입한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
