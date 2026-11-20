import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorMessage, GOOGLE_LOGIN_URL } from '../api';
import { useAuth } from '../auth';
import { AuthLayout, GoogleButton, PasswordInput } from '../AuthLayout';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState<'' | 'pw' | 'google'>('');
  const [formError, setFormError] = useState('');
  const [googleEnabled, setGoogleEnabled] = useState(false);

  useEffect(() => {
    api.providers().then((p) => setGoogleEnabled(p.google)).catch(() => setGoogleEnabled(false));
  }, []);

  const emailErr = tried ? (!email.trim() ? 'Vui lòng nhập email.' : !EMAIL_RE.test(email.trim()) ? 'Email không đúng định dạng.' : '') : '';
  const passErr = tried && (password.length < 8 || password.length > 72) ? 'Mật khẩu 8–72 ký tự.' : '';

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setTried(true);
    if (!EMAIL_RE.test(email.trim()) || password.length < 8 || password.length > 72) return;
    setBusy('pw');
    try {
      await register(email.trim(), password, fullName.trim());
      navigate('/', { replace: true, state: { via: 'register' } });
    } catch (err) {
      setFormError(errorMessage(err));
      setBusy('');
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={submit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div>
          <h2 className="display">Tạo tài khoản</h2>
          <p className="sub">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
        </div>

        {googleEnabled && (
          <>
            <GoogleButton busy={busy === 'google'} onClick={() => { setBusy('google'); window.location.assign(GOOGLE_LOGIN_URL); }} />
            <div className="divider"><span />hoặc dùng email<span /></div>
          </>
        )}

        {formError && <div role="alert" className="alert">{formError}</div>}

        <div className="field">
          <label htmlFor="rg-name">Họ và tên</label>
          <input id="rg-name" className="input" autoComplete="name" placeholder="Nguyễn Văn A" value={fullName}
            onChange={(e) => setFullName(e.target.value)} />
        </div>

        <div className="field">
          <label htmlFor="rg-email">Email nhận vé</label>
          <input id="rg-email" className={`input${emailErr ? ' invalid' : ''}`} type="email" autoComplete="email"
            placeholder="ban@email.com" value={email} aria-invalid={!!emailErr}
            onChange={(e) => { setEmail(e.target.value); setFormError(''); }} />
          {emailErr && <span className="field-error">{emailErr}</span>}
        </div>

        <div className="field">
          <label htmlFor="rg-pass">Mật khẩu</label>
          <PasswordInput id="rg-pass" value={password} invalid={!!passErr} autoComplete="new-password"
            onChange={(v) => { setPassword(v); setFormError(''); }} />
          {passErr && <span className="field-error">{passErr}</span>}
        </div>

        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          {busy === 'pw' ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
        </button>
      </form>
    </AuthLayout>
  );
}
