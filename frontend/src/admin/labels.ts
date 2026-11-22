import type { BookingStatus, PaymentStatus } from '../api';

export const BOOKING_STATUS: Record<BookingStatus, string> = {
  CONFIRMED: 'Thành công',
  PENDING: 'Đang giữ vé',
  AWAITING_PAYMENT: 'Chờ thanh toán',
  CANCELLED: 'Đã huỷ',
  EXPIRED: 'Hết hạn',
  REFUND_PENDING: 'Chờ hoàn tiền',
  REFUNDED: 'Đã hoàn tiền',
};
export const BOOKING_STATUS_ORDER: BookingStatus[] = ['CONFIRMED', 'AWAITING_PAYMENT', 'PENDING', 'EXPIRED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED'];

export const CANCEL_REASON: Record<string, string> = {
  USER: 'Khách tự huỷ',
  SOLD_OUT: 'Hết vé khi giữ',
  NOT_ON_SALE: 'Hạng vé ngừng bán',
  INVENTORY_ERROR: 'Lỗi kho vé',
  EXPIRED: 'Hết hạn giữ / không thanh toán',
  CANCELLED: 'Huỷ (không rõ lý do)',
};

export const PAYMENT_STATUS: Record<PaymentStatus, string> = {
  PENDING: 'Đang chờ', SUCCEEDED: 'Thành công', FAILED: 'Thất bại', REFUNDED: 'Đã hoàn',
};
/** Lớp màu badge dùng chung với trạng thái đơn (.db-st). */
export const PAYMENT_TONE: Record<PaymentStatus, string> = {
  PENDING: 'PENDING', SUCCEEDED: 'CONFIRMED', FAILED: 'CANCELLED', REFUNDED: 'REFUNDED',
};

/** vnp_ResponseCode thường gặp (VNPay v2.1.0). */
export const VNPAY_CODE: Record<string, string> = {
  '00': 'Thành công',
  '07': 'Trừ tiền thành công, nghi ngờ gian lận',
  '09': 'Thẻ chưa đăng ký Internet Banking',
  '10': 'Xác thực thông tin thẻ sai quá 3 lần',
  '11': 'Hết hạn chờ thanh toán',
  '12': 'Thẻ / tài khoản bị khoá',
  '13': 'Sai mật khẩu OTP',
  '24': 'Khách huỷ giao dịch',
  '51': 'Tài khoản không đủ số dư',
  '65': 'Vượt hạn mức giao dịch trong ngày',
  '75': 'Ngân hàng đang bảo trì',
  '79': 'Sai mật khẩu thanh toán quá số lần',
  '97': 'Sai chữ ký',
  '99': 'Lỗi khác',
  '?': 'Không có mã',
};
export const vnpayLabel = (code: string | null) => (code ? `${code} · ${VNPAY_CODE[code] ?? 'Mã khác'}` : '—');

export const money = (n: number) => `${n.toLocaleString('vi-VN')} đ`;
