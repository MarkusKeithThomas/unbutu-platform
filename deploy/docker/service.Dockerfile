# Dockerfile DÙNG CHUNG cho mọi Spring Boot service. Build từ thư mục gốc repo, sau khi đã `./gradlew build`:
#   docker build -f deploy/docker/service.Dockerfile --build-arg SERVICE=catalog-service -t catalog-service .
# Image không tự compile code: CI và máy dev build jar bằng Gradle trước, Docker chỉ đóng gói.

# ---- Stage 1: tách jar thành các layer (thư viện ít đổi / code đổi thường xuyên) ----
FROM eclipse-temurin:21-jre AS extract
ARG SERVICE
WORKDIR /work
COPY services/${SERVICE}/build/libs/${SERVICE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Stage 2: image chạy thật ----
FROM eclipse-temurin:21-jre
ARG SERVICE
LABEL org.opencontainers.image.source="https://github.com/MarkusKeithThomas/unbutu-platform" \
      org.opencontainers.image.title="${SERVICE}"

# Không chạy bằng root: container bị chiếm quyền thì cũng chỉ là user thường.
RUN useradd --system --uid 10001 --user-group --no-create-home app
WORKDIR /app

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
