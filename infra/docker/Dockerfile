# 서버 5개가 함께 쓰는 이미지. jar는 내 PC에서 먼저 만든다(./gradlew bootJar).
# 사용법: docker build -f infra/docker/Dockerfile --build-arg SERVICE=order-service -t limited-deals/order-service .
#
# 1단계: jar를 의존성/스프링/앱 코드 층으로 나눈다. 코드만 바뀌면 맨 위 층만 다시 만들어져서 빌드와 배포가 빠르다.
FROM eclipse-temurin:25-jre AS extract
ARG SERVICE
WORKDIR /work
COPY services/${SERVICE}/build/libs/${SERVICE}-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# 2단계: 실행 이미지. 바뀌지 않는 층부터 쌓는다.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./
# 관리자 권한(root)으로 돌리지 않는다
USER 10001
# 컨테이너 메모리 한도의 75%까지만 힙으로 쓴다(나머지는 스레드, 메타스페이스 등)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
