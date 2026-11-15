// buildSrc là một build Gradle riêng, nên phải tự nạp version catalog của repo chính.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
