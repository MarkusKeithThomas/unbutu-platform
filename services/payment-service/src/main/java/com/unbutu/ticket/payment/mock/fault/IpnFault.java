package com.unbutu.ticket.payment.mock.fault;

/**
 * Một loại lỗi IPN của ngân hàng giả lập. Thêm lỗi mới: tạo 1 {@code @Component} implement interface này
 * + 1 thuộc tính trong {@link MockFaults} / application.yml. Không cần sửa ngân hàng giả lập.
 * Thứ tự áp: {@link #order()} nhỏ trước (vd. nhân bản trước, bỏ sau cùng).
 */
public interface IpnFault {

    /** Tên ngắn hiện trong log / trang ngân hàng, vd. "ipn-drop". */
    String name();

    /** Đang bật không (tỉ lệ > 0 / thời gian > 0). */
    boolean enabled();

    /** Tung xúc xắc + nắn kế hoạch. Không áp thì không đụng plan. */
    void shape(IpnPlan plan, Dice dice);

    default int order() {
        return 100;
    }
}
