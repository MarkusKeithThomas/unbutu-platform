// Quy ước cho service Spring Boot chạy được (deploy thành container).
// Service chỉ cần: plugins { id("ticket.spring-service-conventions") }
plugins {
    id("ticket.java-conventions")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    // /actuator/health cho healthcheck của Docker Compose và deploy script.
    implementation(lib("spring-boot-starter-actuator"))
}

springBoot {
    // Sinh build-info (version, thời gian build) → /actuator/info.
    buildInfo()
}

// Chỉ giữ bootJar với tên cố định build/libs/<service>.jar để Dockerfile copy đúng 1 file.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}
