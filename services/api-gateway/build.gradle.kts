plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
}
