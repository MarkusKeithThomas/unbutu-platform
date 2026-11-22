package com.unbutu.ticket.loadtest.run;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Kịch bản k6 (loadtest/k6, đóng vào jar ở k6/**) → giải nén ra {@code <workDir>/scripts} lúc khởi động.
 * Danh sách kịch bản cho UI khai báo ở đây; file .js phải tồn tại thì mới chạy được.
 */
@Slf4j
@Component
class K6Scripts {

    static final List<RunDtos.Scenario> SCENARIOS = List.of(
            new RunDtos.Scenario("browse", "Xem sự kiện",
                    "Chỉ đọc: 40% danh sách sự kiện · 20% chi tiết · 20% hạng vé · 20% \"Còn x/y\" (inventory, Redis). "
                            + "Mỗi lượt = 1 request. Cần ≥ 1 sự kiện đang bán.", false));

    private final Path dir;

    K6Scripts(LoadtestProperties props) {
        this.dir = props.workDir().resolve("scripts");
        extract();
    }

    Path dir() {
        return dir;
    }

    Optional<Path> script(String scenario) {
        if (SCENARIOS.stream().noneMatch(s -> s.name().equals(scenario))) {
            return Optional.empty();
        }
        Path p = dir.resolve(scenario + ".js");
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    private void extract() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:k6/**/*.js");
            for (Resource r : files) {
                String url = r.getURL().toString();
                String rel = url.substring(url.lastIndexOf("/k6/") + 4);
                Path target = dir.resolve(rel).normalize();
                if (!target.startsWith(dir)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = r.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.info("Đã giải nén {} file kịch bản k6 vào {}", files.length, dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Không giải nén được kịch bản k6", e);
        }
    }
}
