# ADR-0005: Deploy prod sau khi duyệt – Tailscale OIDC + SSH forced command

- Trạng thái: Chấp nhận · 2026-11-15
- Liên quan: ADR-0003 (local + prod), ADR-0004 (cấu hình/secret trên AWS)

## Bối cảnh
Server `unbutu` ở nhà, không có IP public cho SSH; chỉ vào được qua tailnet. Cần: merge `main` → deploy prod tự động
nhưng **có người duyệt** (luyện "dev không tự đẩy lên prod"), runner CI có quyền **tối thiểu** trên server, rollback dễ.

## Quyết định
1. `release.yml` build image 1 lần rồi gọi `deploy.yml` (reusable); `deploy.yml` cũng chạy tay với 1 SHA để deploy lại/rollback.
2. Job deploy dùng GitHub Environment **`production`**: required reviewer, chỉ nhánh `main`.
3. Runner vào tailnet bằng **Tailscale workload identity federation (OIDC)** – không có auth key/secret; node mang `tag:ci`.
   Trust credential khớp subject `repo:MarkusKeithThomas@40294239/unbutu-platform@1458510956:environment:production`.
4. Tailscale grants: `tag:ci` → chỉ `100.67.135.86:22` (có `tests` trong policy để chặn sửa nhầm).
5. SSH vào user `deploy` bằng key riêng (secret `DEPLOY_SSH_KEY`), host key ghim trong `DEPLOY_KNOWN_HOSTS`.
   `authorized_keys`: `restrict,from="100.64.0.0/10",command="sudo -n /opt/unbutu-platform/bin/deploy.sh -"`;
   sudoers chỉ cho đúng lệnh đó. Runner chỉ gửi được `images.env` qua stdin.
6. `deploy.sh` (root) **chỉ admin cài tay**; nó tự tải `compose.app.yml` + `postgres/init` đúng `GIT_SHA` từ GitHub,
   thử pull 3 lần, `up --wait`, smoke test, lỗi thì rollback về bản thành công trước.

## Phương án đã loại
- Self-hosted runner trên `unbutu`: runner chạy code của mọi PR ngay trên prod → rủi ro cao, tốn RAM.
- Tailscale SSH: chế độ check cần xác thực trình duyệt, phá automation.
- Đẩy `compose.app.yml` qua SSH: runner sửa được cấu hình chạy bằng root (mount `/`, privileged) → leo quyền.

## Hệ quả
- (+) Bị lộ secret CI cũng chỉ deploy được image GHCR của mình theo digest; không shell, không đọc secret prod.
- (+) Rollback = chạy lại `deploy.yml` với SHA cũ (artifact giữ 90 ngày); tự rollback khi deploy lỗi (đã xảy ra thật với `4551749`).
- (−) Sửa `deploy.sh` cần admin cài lại thủ công lên server (cố ý).
- (−) Trong tailnet, MagicDNS trả IP 100.x → kiểm tra public phải phân giải qua DNS public.
- (−) Mạng nhà chập chờn khi pull image → đã thêm retry; vẫn phụ thuộc kết nối tới GHCR.
