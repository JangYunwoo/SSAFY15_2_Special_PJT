# 백엔드 이미지 (Spring Boot 4.1 / Java 21)
#
# 빌드와 실행을 나눕니다. 빌드에는 JDK 와 Gradle 캐시가 필요하지만 실행에는 JRE 만
# 있으면 됩니다. 한 단계로 만들면 이미지에 소스와 빌드 도구가 그대로 남아 크기가
# 몇 배로 커지고, 서버에 소스가 올라가는 것 자체가 불필요한 노출입니다.

# ---------- 빌드 ----------
# Gradle 을 이미지에 든 것으로 쓰지 않고 래퍼(gradlew)를 씁니다. 프로젝트가 요구하는
# 버전(gradle-wrapper.properties)을 그대로 따라가므로, 팀이 Gradle 을 올려도 이
# Dockerfile 을 고칠 필요가 없습니다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 래퍼와 의존성 목록만 먼저 복사해 받아 둡니다. 소스만 바뀐 재빌드에서 이 레이어가
# 캐시에 남아 빌드 시간이 크게 줄어듭니다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
# 테스트는 CI 에서 돌립니다. 이미지 빌드에서 또 돌리면 배포가 그만큼 느려집니다.
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 돌리지 않습니다. 컨테이너가 뚫려도 호스트로 넘어가기 어렵게 합니다.
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너 메모리에 맞춰 힙을 잡습니다. 고정값을 쓰면 인스턴스를 바꿀 때마다
# 이미지를 다시 만들어야 합니다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
