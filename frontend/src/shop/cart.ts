// Lựa chọn vé của khách (chưa giữ vé) – lưu theo sự kiện trong sessionStorage để còn nguyên sau khi đi đăng nhập.
// Bước giữ vé (POST /api/bookings, PR sau) đọc từ đây. Giới hạn/giá/phí thật do booking-service quyết định.

export const MAX_TICKETS_PER_ORDER = 4;
/** Phí dịch vụ hiển thị tạm tính (booking-service sẽ tính và trả số chính thức). */
export const SERVICE_FEE_PER_TICKET = 10_000;

export type Cart = Record<string, number>; // ticketTypeId → số lượng

const key = (eventId: string) => `cart:${eventId}`;

export function loadCart(eventId: string): Cart {
  try {
    const raw = sessionStorage.getItem(key(eventId));
    const parsed = raw ? (JSON.parse(raw) as unknown) : null;
    if (parsed && typeof parsed === 'object') {
      const cart: Cart = {};
      for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
        if (typeof v === 'number' && Number.isInteger(v) && v > 0) cart[k] = v;
      }
      return cart;
    }
  } catch {
    /* storage bị chặn / dữ liệu hỏng → giỏ rỗng */
  }
  return {};
}

export function saveCart(eventId: string, cart: Cart): void {
  try {
    if (Object.keys(cart).length === 0) sessionStorage.removeItem(key(eventId));
    else sessionStorage.setItem(key(eventId), JSON.stringify(cart));
  } catch {
    /* bỏ qua */
  }
}

export const cartCount = (cart: Cart) => Object.values(cart).reduce((a, b) => a + b, 0);
