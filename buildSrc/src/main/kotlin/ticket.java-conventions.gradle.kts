// Quy ước chung cho MỌI module Java của repo. Module chỉ cần: plugins { id("ticket.java-conventions") }
plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

group = "com.unbutu.ticket"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Nạp BOM vào mọi classpath → khai báo thư viện không cần ghi version.
    listOf(platform(lib("spring-boot-bom")), platform(lib("spring-cloud-bom"))).forEach {
        implementation(it)
        compileOnly(it)
        annotationProcessor(it)
        testImplementation(it)
        testCompileOnly(it)
        testAnnotationProcessor(it)
    }

    // Lombok chỉ cần lúc compile (sinh code), không đóng vào jar. Quy tắc dùng: xem lombok.config.
    compileOnly(lib("lombok"))
    annotationProcessor(lib("lombok"))
    testCompileOnly(lib("lombok"))
    testAnnotationProcessor(lib("lombok"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    // Giữ tên tham số khi compile (Spring cần cho @PathVariable, @RequestParam không ghi tên).
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
