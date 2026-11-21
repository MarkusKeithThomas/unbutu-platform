plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id → MDC, access log
    implementation(project(":libs:event-contracts"))    // consumer catalog.event.v1
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jdbc)       // sổ cái Postgres: SQL tường minh (UPDATE có điều kiện), không JPA
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.data.redis) // giữ vé: Lua atomic
    implementation(libs.spring.boot.starter.kafka)
    runtimeOnly(libs.postgresql)
}
