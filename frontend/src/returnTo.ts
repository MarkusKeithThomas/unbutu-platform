// Trang cần quay lại sau đăng nhập (kể cả qua redirect Google → lưu sessionStorage, không dùng state của router).
const KEY = 'returnTo';

export function setReturnTo(path: string): void {
  try {
    sessionStorage.setItem(KEY, path);
  } catch {
    /* trình duyệt chặn storage → về trang chủ */
  }
}

/** Lấy và xoá; chỉ nhận đường dẫn nội bộ ("/…", không "//") để không bị dùng làm open redirect. */
export function takeReturnTo(): string {
  try {
    const p = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    if (p && p.startsWith('/') && !p.startsWith('//') && !p.startsWith('/login') && !p.startsWith('/register')) return p;
  } catch {
    /* bỏ qua */
  }
  return '/';
}
