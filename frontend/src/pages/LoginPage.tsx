import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, ApiError, errorMessage, GOOGLE_LOGIN_URL } from '../api';
import { takeReturnTo } from '../returnTo';
import { useAuth } from '../auth';
import { AuthLayout, GoogleButton, PasswordInput } from '../AuthLayout';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState<'' | 'pw' | 'google'>('');
  const [formError, setFormError] = useState('');
  const [googleEnabled, setGoogleEnabled] = useState(false);

  useEffect(() => {
    api.providers().then((p) => setGoogleEnabled(p.google)).catch(() => setGoogleEnabled(false));
    const err = params.get('error');   // quay về từ login Google thất bại
    if (err) setFormError(errorMessage(new ApiError(401, err, err)));
  }, [params]);

  const emailErr = tried ? (!email.trim() ? 'Vui lòng nhập email.' : !EMAIL_RE.test(email.trim()) ? 'Email không đúng định dạng.' : '') : '';
  const passErr = tried && password.length < 8 ? 'Mật khẩu tối thiểu 8 ký tự.' : '';

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setTried(true);
    if (!EMAIL_RE.test(email.trim()) || password.length < 8) return;
    setBusy('pw');
    try {
      await login(email.trim(), password, remember);
      navigate(takeReturnTo(), { replace: true });
    } catch (err) {
      setFormError(errorMessage(err));
      setBusy('');
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={submit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div>
          <h2 className="display">Đăng nhập</h2>
          <p className="sub">Chưa có tài khoản? <Link to="/register">Đăng ký</Link></p>
        </div>

        {googleEnabled && (
          <>
            <GoogleButton busy={busy === 'google'} onClick={() => { setBusy('google'); window.location.assign(GOOGLE_LOGIN_URL); }} />
            <div className="divider"><span />hoặc dùng email<span /></div>
          </>
        )}

        {formError && <div role="alert" className="alert">{formError}</div>}

        <div className="field">
          <label htmlFor="lg-email">Email</label>
          <input id="lg-email" className={`input${emailErr ? ' invalid' : ''}`} type="email" autoComplete="username"
            placeholder="ban@email.com" value={email} aria-invalid={!!emailErr}
            onChange={(e) => { setEmail(e.target.value); setFormError(''); }} />
          {emailErr && <span className="field-error">{emailErr}</span>}
        </div>

        <div className="field">
          <div className="field-head">
            <label htmlFor="lg-pass">Mật khẩu</label>
          </div>
          <PasswordInput id="lg-pass" value={password} invalid={!!passErr} autoComplete="current-password"
            onChange={(v) => { setPassword(v); setFormError(''); }} />
          {passErr && <span className="field-error">{passErr}</span>}
        </div>

        <label className="check">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          Ghi nhớ đăng nhập trên máy này
        </label>

        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          {busy === 'pw' ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>

        <p className="fine">Tiếp tục nghĩa là bạn đồng ý với Điều khoản và Chính sách bảo mật của TicketHub.</p>
      </form>
    </AuthLayout>
  );
}
