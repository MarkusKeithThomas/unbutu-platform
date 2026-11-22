plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id, log ECS
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)   // verify access token (JWKS identity), chỉ ADMIN
    implementation(libs.spring.boot.restclient)         // đọc REST API của k6 (127.0.0.1:6565)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
}

// Kịch bản k6 dùng chung với `make loadtest`: một nguồn duy nhất loadtest/k6 → đóng vào jar (classpath k6/**),
// lúc khởi động service giải nén ra đĩa để tiến trình k6 đọc.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("loadtest/k6")) {
        into("k6")
    }
}
