plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id → MDC, access log
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)   // JwtEncoder/JwtDecoder (Nimbus) cho /me
    implementation(libs.spring.boot.starter.security.oauth2.client)            // login Google (Authorization Code)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
}
