package com.unbutu.ticket.loadtest.run;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param k6Binary      đường dẫn binary k6 (image: /usr/local/bin/k6)
 * @param targetBaseUrl k6 bắn vào đâu – prod: thẳng api-gateway trong mạng Docker (đo hệ thống, không đo Funnel/Internet)
 * @param maxRps        trần an toàn: từ chối bậc lớn hơn (bấm nhầm 50000 không làm sập prod)
 * @param maxDuration   tổng thời gian tối đa 1 lượt (số bậc × thời gian bậc)
 * @param workDir       nơi giải nén kịch bản + file kết quả/log tạm của k6
 * @param apiPort       cổng REST API của k6 (chỉ 127.0.0.1 trong container) – đọc số liệu trực tiếp, dừng test
 * @param pollInterval  chu kỳ đọc số liệu = độ mịn biểu đồ
 * @param catalogUrl    tạo sự kiện ẩn + hạng vé cho kịch bản mua vé
 * @param bookingUrl    bật/tắt cờ bỏ giới hạn 4 vé cho sự kiện load test
 * @param identityUrl   đăng ký/đăng nhập tài khoản test
 * @param account       tài khoản test mua vé (mật khẩu: secret – ADR-0004)
 */
@ConfigurationProperties("loadtest")
public record LoadtestProperties(String k6Binary, String targetBaseUrl, int maxRps, Duration maxDuration, Path workDir,
                                 int apiPort, Duration pollInterval, String catalogUrl, String bookingUrl,
                                 String identityUrl, Account account) {

    public record Account(String email, String password) {
    }
}
