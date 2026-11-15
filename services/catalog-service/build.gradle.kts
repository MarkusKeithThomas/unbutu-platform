plugins {
    id("ticket.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)

    testImplementation(libs.spring.boot.starter.webmvc.test)
}
