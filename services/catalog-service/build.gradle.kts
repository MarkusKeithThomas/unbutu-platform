plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id → MDC, RestClient propagate
    implementation(project(":libs:event-contracts"))    // schema event catalog.event.v1
    implementation(project(":libs:outbox"))             // Transactional Outbox → Redpanda
    implementation(libs.spring.boot.starter.kafka)      // Outbox relay → Redpanda
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)   // verify access token (JWKS của identity)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)   // cache response tầng 2 (dùng chung giữa các instance)
    implementation(libs.caffeine)                         // cache response tầng 1 (trong bộ nhớ)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
