package com.unbutu.ticket.booking.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Số liệu dashboard admin (ADM-1). Tiền: VND. Khoảng ngày theo giờ VN, tính theo ngày TẠO đơn.
 * <ul>
 *   <li>{@code netRevenue}: đơn CONFIRMED (vé còn hiệu lực) – con số chính.</li>
 *   <li>{@code grossRevenue}: tiền đã nhận = CONFIRMED + REFUND_PENDING + REFUNDED.</li>
 *   <li>{@code refundAmount}: REFUND_PENDING + REFUNDED (trả muộn mà hết vé → hoàn).</li>
 *   <li>{@code openOrders}: PENDING + AWAITING_PAYMENT (đang giữ vé / chờ cổng thanh toán).</li>
 *   <li>{@code failedOrders}: CANCELLED + EXPIRED; lý do huỷ ở {@code cancelReasons}.</li>
 * </ul>
 */
public record AdminStats(LocalDate from, LocalDate to, UUID eventId, boolean includeTest, Instant generatedAt,
                         Kpi kpi, Map<String, StatusRow> byStatus, Map<String, Long> cancelReasons,
                         List<Day> daily, List<EventRow> topEvents) {

    public record Kpi(long totalOrders, long confirmedOrders, long openOrders, long failedOrders, long refundOrders,
                      long ticketsSold, long netRevenue, long grossRevenue, long refundAmount,
                      double conversionRate, long avgOrderValue) {
    }

    public record StatusRow(long orders, long tickets, long amount) {
    }

    public record Day(LocalDate date, long orders, long confirmedOrders, long ticketsSold, long netRevenue) {
    }

    public record EventRow(UUID eventId, String title, Instant startsAt, boolean hidden,
                           long confirmedOrders, long ticketsSold, long netRevenue) {
    }
}
