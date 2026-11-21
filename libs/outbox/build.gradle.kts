// Transactional Outbox dùng chung (ADR-0007): Outbox.append trong transaction nghiệp vụ + OutboxRelay đẩy lên Redpanda.
// Service tự mang spring-kafka / jdbc / jackson (starter) và tự tạo bảng `outbox` bằng Flyway (schema: README).
plugins {
    id("ticket.java-conventions")
    `java-library`
}

dependencies {
    api(project(":libs:event-contracts"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.kafka)
    compileOnly(libs.spring.jdbc)
    compileOnly(libs.spring.tx)
    compileOnly(libs.jackson.databind)
}
