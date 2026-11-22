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
 */
@ConfigurationProperties("loadtest")
public record LoadtestProperties(String k6Binary, String targetBaseUrl, int maxRps, Duration maxDuration, Path workDir,
                                 int apiPort, Duration pollInterval) {
}
