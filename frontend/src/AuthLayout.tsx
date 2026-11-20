import { useState, type ReactNode } from 'react';

const Check = () => (
  <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#60A5FA" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12l5 5L20 7" />
  </svg>
);

/** Khung 2 cột của màn đăng nhập/đăng ký: trái giới thiệu, phải là card. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section aria-label="Giới thiệu" className="intro">
        <div className="brand display">
          <div className="brand-mark">
            <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#111827" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" />
              <path d="M14 6v12" strokeDasharray="2 2" />
            </svg>
          </div>
          TicketHub
        </div>
        <div>
          <h1 className="display">Giữ chỗ nhanh, thanh toán một lần.</h1>
          <p>Đăng nhập để chọn vé, giữ chỗ trong 10 phút và nhận vé điện tử qua email.</p>
        </div>
        <ul>
          <li><Check />Một tài khoản cho mọi sự kiện</li>
          <li><Check />Lịch sử đơn hàng và vé đã mua</li>
          <li><Check />Đăng nhập nhanh bằng Google</li>
        </ul>
      </section>
      <section aria-label="Tài khoản" className="panel">
        <div className="card">{children}</div>
      </section>
    </div>
  );
}

export function GoogleButton({ busy, onClick }: { busy: boolean; onClick: () => void }) {
  return (
    <button type="button" className="btn btn-google" onClick={onClick} disabled={busy}>
      <span aria-hidden="true" className="g-mark">G</span>
      {busy ? 'Đang chuyển sang Google…' : 'Tiếp tục với Google'}
    </button>
  );
}

export function PasswordInput(props: { id: string; value: string; onChange: (v: string) => void; invalid: boolean; autoComplete: string }) {
  const [show, setShow] = useState(false);
  return (
    <div className="pw">
      <input id={props.id} className={`input with-toggle${props.invalid ? ' invalid' : ''}`} type={show ? 'text' : 'password'}
        autoComplete={props.autoComplete} placeholder="Tối thiểu 8 ký tự" value={props.value}
        onChange={(e) => props.onChange(e.target.value)} aria-invalid={props.invalid} />
      <button type="button" onClick={() => setShow(!show)} aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#4B5563" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
      </button>
    </div>
  );
}
