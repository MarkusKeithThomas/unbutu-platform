// Starter dùng chung cho service Spring MVC (servlet). Gateway (WebFlux) có filter riêng.
plugins {
    id("ticket.java-conventions")
    `java-library`
}

dependencies {
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.slf4j.api)
    // Service tự mang web stack của mình → ở đây chỉ cần lúc compile.
    compileOnly(libs.spring.web)
    compileOnly(libs.jakarta.servlet.api)
    compileOnly(libs.spring.boot.restclient)

    testImplementation(libs.spring.web)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.spring.boot.restclient)
}
