plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Để convention plugin của service (bước 3) dùng được id("org.springframework.boot").
    implementation(libs.spring.boot.gradle.plugin)
}
