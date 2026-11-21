plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id, RestClient propagate trace
    implementation(project(":libs:event-contracts"))    // consumer catalog.event.v1 (bảng giá sao chép)
    implementation(project(":libs:outbox"))             // PAY-1c: booking.event.v1 (booking.confirmed)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)   // verify access token (JWKS identity)
    implementation(libs.spring.boot.starter.jdbc)       // SQL tường minh: UPDATE … WHERE status=<cũ>, SKIP LOCKED
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.restclient)         // gọi inventory /internal/**
    runtimeOnly(libs.postgresql)
}
