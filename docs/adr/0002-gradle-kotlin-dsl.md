# ADR-0002: Gradle (Kotlin DSL) cho monorepo

- Trạng thái: Chấp nhận · 2026-11-15

## Bối cảnh
Monorepo nhiều service Spring Boot + thư viện dùng chung; CI chỉ nên build/test phần thay đổi.

## Quyết định
Gradle Kotlin DSL, multi-project, version catalog (`gradle/libs.versions.toml`), Gradle Wrapper commit vào repo.

## Hệ quả
- (+) Incremental build + build cache, version catalog gọn.
- (−) Cấu hình linh hoạt nhưng khó debug hơn Maven; cần học Gradle.
