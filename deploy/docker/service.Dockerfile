# Dockerfile DÙNG CHUNG cho mọi Spring Boot service. Build từ thư mục gốc repo, sau khi đã `./gradlew build`:
#   docker build -f deploy/docker/service.Dockerfile --build-arg SERVICE=catalog-service -t catalog-service .
# Image không tự compile code: CI và máy dev build jar bằng Gradle trước, Docker chỉ đóng gói.
#
# Target: `runtime` (mặc định, mọi service). Service có file services/<svc>/docker-target dùng target ghi trong đó
# (vd. loadtest-service → `runtime-k6`: thêm binary k6). CI/release/compose.local đọc file này.

# k6 (binary Go tĩnh) cho loadtest-service – ghim digest. BuildKit bỏ qua stage này khi build target `runtime`.
FROM grafana/k6:1.8.2@sha256:00fdcebafbc86cb0137d429529b1caa401a02e09025f02fc74866fa6e2b004d8 AS k6

# ---- Stage 1: tách jar thành các layer (thư viện ít đổi / code đổi thường xuyên) ----
FROM eclipse-temurin:21-jre AS extract
ARG SERVICE
WORKDIR /work
COPY services/${SERVICE}/build/libs/${SERVICE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Stage 2: image chạy thật ----
FROM eclipse-temurin:21-jre AS runtime
ARG SERVICE
LABEL org.opencontainers.image.source="https://github.com/MarkusKeithThomas/unbutu-platform" \
      org.opencontainers.image.title="${SERVICE}"

# Không chạy bằng root: container bị chiếm quyền thì cũng chỉ là user thường.
RUN useradd --system --uid 10001 --user-group --no-create-home app
WORKDIR /app

# OpenTelemetry Java agent (ADR-0006, OBS-4): trace + metrics JVM/HTTP gửi OTLP tới SigNoz, gắn trace_id/span_id vào MDC.
# Ghim version + sha256 (Docker kiểm tra khi build). Agent CHỈ chạy khi JDK_JAVA_OPTIONS có -javaagent (compose.app.yml);
# local (compose.local.yml, IntelliJ) không bật. Layer ít đổi → đặt trước code.
ADD --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar \
    /opt/otel/opentelemetry-javaagent.jar
RUN chmod 444 /opt/otel/opentelemetry-javaagent.jar

# Thứ tự từ ít thay đổi → hay thay đổi: sửa code chỉ làm mới layer cuối (vài trăm KB) khi build/push.
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./

USER app

# JVM dùng tối đa 75% RAM giới hạn của container (mem_limit trong compose); hết RAM thì thoát để Docker restart
# thay vì treo. Muốn thêm option (vd. debug) thì đặt JDK_JAVA_OPTIONS – không ghi đè dòng này.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ---- Biến thể: + k6 (loadtest-service chạy k6 như tiến trình con – không cần docker.sock) ----
FROM runtime AS runtime-k6
COPY --from=k6 /usr/bin/k6 /usr/local/bin/k6

# ---- Mặc định (docker build không --target) ----
FROM runtime
