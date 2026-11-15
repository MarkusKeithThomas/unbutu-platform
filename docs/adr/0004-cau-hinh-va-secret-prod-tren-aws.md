# ADR-0004: Cấu hình & secret prod trên AWS – repo `pro-ticket-workload` giữ đường dẫn, value chỉ ở AWS

- Trạng thái: Chấp nhận · 2026-11-15
- Thay thế: "secret trong `.env` trên server" (§4.5 cũ), `deploy/.env.example`

## Bối cảnh
Mật khẩu DB và cấu hình prod nằm trong `.env` gõ tay trên server: không review được, không có lịch sử, khó biết service nào cần biến gì,
lộ server = lộ tất cả. Ở công ty: repo workload (IaC) khai báo service cần biến gì, value nằm trong secret store, hạ tầng đưa vào container lúc chạy
(External Secrets Operator / ECS `secrets.valueFrom`).

## Quyết định
1. **Repo private `MarkusKeithThomas/pro-ticket-workload` (Terraform)**, GitOps: PR → `plan` (comment vào PR), merge `main` → `apply`.
   - CI đăng nhập AWS bằng **GitHub OIDC** (không có access key trong GitHub), trust theo *immutable subject* (`repo:<owner>@<id>/<repo>@<id>:…`).
   - 2 role: `gha-pro-ticket-workload-plan` (PR, `ReadOnlyAccess`) và `gha-pro-ticket-workload-apply` (chỉ `main`, chỉ tài nguyên `unbutu/*`).
   - **Không role CI nào đọc được value secret** (`GetSecretValue` bị từ chối – đã kiểm chứng bằng `simulate-principal-policy`).
   - State: S3 `pro-ticket-tfstate-038774852192` (versioning, mã hoá, chỉ TLS, `use_lockfile`).
   - `bootstrap/` (bucket, OIDC, role, boundary) chỉ admin apply từ máy quản trị – con gà/quả trứng.
2. **Mỗi service 1 folder** `prod/services/<service>/env.yaml`:
   ```yaml
   env:      { DB_URL: jdbc:postgresql://postgres:5432/catalog_db, DB_USERNAME: catalog }   # cấu hình thường, trong git
   secrets:  { DB_PASSWORD: unbutu/prod/catalog-service#DB_PASSWORD }                       # CHỈ đường dẫn
   ```
   Terraform đẩy bản đồ (không value) lên **SSM** `/unbutu/prod/<service>/env-map` và kiểm tra secret được trỏ tới tồn tại (plan lỗi nếu sai).
3. **Value secret**: người có quyền tạo/sửa trên **AWS Console** (Secrets Manager, 1 secret/service, nhiều key). Không bao giờ nằm trong git hay Terraform state.
4. **Server**: IAM user `unbutu/unbutu-server` – chỉ `ssm:GetParameter` trên các env-map và `GetSecretValue` trên các secret được trỏ tới;
   gắn permissions boundary `pro-ticket-workload-boundary` (role apply không thể cấp quá mức này). Access key tạo tay 1 lần, ở `/root/.aws` (600).
5. **`deploy.sh`** lúc deploy: đọc env-map → lấy value theo đường dẫn → `env/<service>.env` (root 600) → `docker compose up` theo digest.
   Thiếu env-map/secret/key → dừng, không deploy. Value đổi trên Console hoặc bản đồ đổi → có hiệu lực ở **lần deploy kế tiếp**.

## Hệ quả
- (+) Biến của service review được qua PR; value chỉ admin và server đọc được; CloudTrail ghi ai sửa secret; thêm service = thêm folder.
- (+) Chi phí ~0,40 USD/tháng mỗi secret (SSM standard miễn phí).
- (−) **Access key dài hạn** trên server (máy ở nhà không gắn được IAM Role) → nợ kỹ thuật: chuyển sang **IAM Roles Anywhere** (chứng chỉ X.509).
- (−) Đổi mật khẩu DB trên Console **không** đổi mật khẩu trong Postgres (init chỉ chạy khi volume trống) → cần runbook xoay vòng (`ALTER USER` rồi deploy).
- (−) Container không tự nhận value mới – phải deploy lại (sau: tự kích hoạt deploy khi workload thay đổi).
