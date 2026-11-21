"""alert-relay: nhận webhook của SigNoz (payload kiểu Alertmanager) → gửi Telegram (OBS-5, S1).

Chỉ dùng thư viện chuẩn – chạy thẳng trên image python:*-alpine, không build image riêng.
Biến môi trường (env/alert-relay.env, sinh từ AWS bằng render-env.sh – ADR-0004):
  TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID   bắt buộc
  SIGNOZ_URL                             tuỳ chọn, link "Mở SigNoz" trong tin nhắn (mặc định URL tailnet)
Không publish port: SigNoz gọi http://alert-relay:8080/alert trong network signoz-network.
Telegram lỗi → trả 502 để SigNoz/Alertmanager gửi lại. Không bao giờ log token.
"""
import html
import json
import os
import sys
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]
CHAT_ID = os.environ["TELEGRAM_CHAT_ID"]
SIGNOZ_URL = os.environ.get("SIGNOZ_URL", "https://unbutu.tail3c11f4.ts.net:8443")
MAX_BODY = 1 << 20          # 1 MiB – payload Alertmanager bình thường vài KB
TELEGRAM_LIMIT = 4000       # Telegram giới hạn 4096 ký tự / tin


def log(level, msg, **kv):
    print(json.dumps({"@timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                      "log.level": level, "service.name": "alert-relay", "message": msg, **kv},
                     ensure_ascii=False), flush=True)


def fmt_alert(a):
    labels, ann = a.get("labels", {}), a.get("annotations", {})
    firing = a.get("status") == "firing"
    icon = "🔴" if firing else "✅"
    name = labels.get("alertname", "alert")
    lines = [f"{icon} <b>{html.escape(('FIRING' if firing else 'RESOLVED') + ': ' + name)}</b>"]
    svc = labels.get("service.name") or labels.get("service_name")
    if svc:
        lines.append(f"service: <code>{html.escape(svc)}</code>")
    sev = labels.get("severity")
    if sev:
        lines.append(f"mức: {html.escape(sev)}")
    for key in ("summary", "description"):
        if ann.get(key):
            lines.append(html.escape(ann[key]))
    if a.get("startsAt"):
        lines.append(f"từ: {html.escape(a['startsAt'][:19].replace('T', ' '))} UTC")
    return "\n".join(lines)


def build_message(payload):
    alerts = payload.get("alerts") or []
    if not alerts:
        return None
    parts = [fmt_alert(a) for a in alerts]
    parts.append(f'<a href="{html.escape(SIGNOZ_URL)}/alerts">Mở SigNoz</a> (tailnet)')
    msg = "\n\n".join(parts)
    return msg if len(msg) <= TELEGRAM_LIMIT else msg[:TELEGRAM_LIMIT] + "\n…(cắt bớt)"


def send_telegram(text):
    body = json.dumps({"chat_id": CHAT_ID, "text": text, "parse_mode": "HTML",
                       "disable_web_page_preview": True}).encode()
    req = urllib.request.Request(f"https://api.telegram.org/bot{TOKEN}/sendMessage", data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return resp.status


class Handler(BaseHTTPRequestHandler):
    server_version = "alert-relay"

    def log_message(self, *args):  # tắt access log mặc định (có thể in URL); tự log JSON bên dưới
        pass

    def _reply(self, code, text=""):
        data = text.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._reply(200, "ok") if self.path == "/healthz" else self._reply(404)

    def do_POST(self):
        if self.path != "/alert":
            return self._reply(404)
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            return self._reply(413 if length > MAX_BODY else 400)
        try:
            payload = json.loads(self.rfile.read(length))
        except ValueError:
            return self._reply(400, "json không hợp lệ")
        msg = build_message(payload)
        if msg is None:
            return self._reply(200, "không có alert")
        names = sorted({a.get("labels", {}).get("alertname", "?") for a in payload["alerts"]})
        try:
            send_telegram(msg)
        except urllib.error.HTTPError as e:   # e.url chứa token → chỉ log mã lỗi
            log("ERROR", "Telegram từ chối", status=e.code, alerts=names)
            return self._reply(502)
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            log("ERROR", "Không gọi được Telegram", error=type(e).__name__, alerts=names)
            return self._reply(502)
        log("INFO", "Đã gửi Telegram", status=payload.get("status"), alerts=names, count=len(payload["alerts"]))
        self._reply(200, "ok")


if __name__ == "__main__":
    if not TOKEN or not CHAT_ID:
        sys.exit("thiếu TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID")
    log("INFO", "alert-relay lắng nghe :8080")
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
