package com.unbutu.ticket.loadtest.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RunRepository {

    /** Chi tiết: thêm 3 cột JSON/text (nặng hơn) – danh sách lịch sử không đọc chúng. */
    record Stored(RunDtos.RunSummary summary, String result, String samples, String logTail) {
    }

    private static final String COLS = """
            id, scenario, steps, step_seconds, p95_ms, max_err_pct, stop_on_fail, status, started_by, started_at,
            finished_at, pass_up_to_rps""";

    private final JdbcClient jdbc;

    private static RunDtos.RunSummary map(ResultSet rs, int i) throws SQLException {
        Timestamp fin = rs.getTimestamp("finished_at");
        Object pass = rs.getObject("pass_up_to_rps");
        return new RunDtos.RunSummary(rs.getObject("id", UUID.class), rs.getString("scenario"),
                parseSteps(rs.getString("steps")), rs.getInt("step_seconds"), rs.getInt("p95_ms"),
                rs.getDouble("max_err_pct"), rs.getBoolean("stop_on_fail"), rs.getString("status"),
                rs.getString("started_by"), rs.getTimestamp("started_at").toInstant(),
                fin == null ? null : fin.toInstant(), pass == null ? null : ((Number) pass).intValue());
    }

    static List<Integer> parseSteps(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }

    void insert(RunDtos.RunSummary r) {
        jdbc.sql("""
                        insert into runs (id, scenario, steps, step_seconds, p95_ms, max_err_pct, stop_on_fail, status,
                                          started_by, started_at)
                        values (:id, :scenario, :steps, :stepSec, :p95, :err, :stop, :status, :by, :at)""")
                .param("id", r.id()).param("scenario", r.scenario())
                .param("steps", String.join(",", r.steps().stream().map(String::valueOf).toList()))
                .param("stepSec", r.stepSeconds()).param("p95", r.p95Ms()).param("err", r.maxErrPct())
                .param("stop", r.stopOnFail()).param("status", r.status()).param("by", r.startedBy())
                .param("at", Timestamp.from(r.startedAt()))
                .update();
    }

    void finish(UUID id, String status, Instant finishedAt, Integer passUpToRps, String resultJson, String samplesJson,
                String logTail) {
        jdbc.sql("""
                        update runs set status = :status, finished_at = :fin, pass_up_to_rps = :pass,
                                        result = cast(:result as jsonb), samples = cast(:samples as jsonb), log_tail = :log
                        where id = :id""")
                .param("id", id).param("status", status).param("fin", Timestamp.from(finishedAt))
                .param("pass", passUpToRps).param("result", resultJson).param("samples", samplesJson)
                .param("log", logTail)
                .update();
    }

    /** Lượt còn RUNNING sau khi service khởi động lại = tiến trình k6 đã chết theo container. */
    int abortDangling(Instant now) {
        return jdbc.sql("update runs set status = 'ABORTED', finished_at = :now where status = 'RUNNING'")
                .param("now", Timestamp.from(now)).update();
    }

    List<RunDtos.RunSummary> recent(int limit) {
        return jdbc.sql("select " + COLS + " from runs order by started_at desc limit :lim")
                .param("lim", limit).query(RunRepository::map).list();
    }

    Optional<Stored> find(UUID id) {
        return jdbc.sql("select " + COLS + ", result::text as result, samples::text as samples, log_tail from runs where id = :id")
                .param("id", id)
                .query((rs, i) -> new Stored(map(rs, i), rs.getString("result"), rs.getString("samples"),
                        rs.getString("log_tail")))
                .optional();
    }
}
