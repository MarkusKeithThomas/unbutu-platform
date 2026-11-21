plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:platform-starter"))   // X-Request-Id, RestClient propagate trace
    implementation(project(":libs:outbox"))             // payment.result.v1 qua Transactional Outbox (+ event-contracts)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.restclient)         // cổng giả lập gọi IPN server→server
    runtimeOnly(libs.postgresql)
}
