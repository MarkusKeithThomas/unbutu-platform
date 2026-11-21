import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { takeReturnTo } from '../returnTo';
import { useAuth } from '../auth';

/** Backend đã đặt refresh cookie sau login Google → đổi lấy access token rồi về trang chính. */
export function AuthCallbackPage() {
  const { restore } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    restore().then((ok) =>
      navigate(ok ? takeReturnTo() : '/login?error=GOOGLE_LOGIN_FAILED', { replace: true }),
    );
  }, [restore, navigate]);

  return <div className="center">Đang hoàn tất đăng nhập…</div>;
}
