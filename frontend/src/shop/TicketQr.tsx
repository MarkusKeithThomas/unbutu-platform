import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import type { Ticket } from '../api';
import { vnd } from './ShopLayout';

/** 1 vé: mã QR (JWS đã ký – không sửa được nội dung) + thông tin để khách đối chiếu tại cổng. */
export function TicketCard({ ticket, total }: { ticket: Ticket; total: number }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    // ECC M: chịu được ~15% hỏng (màn hình xước, chói) mà QR vẫn nhỏ gọn.
    QRCode.toDataURL(ticket.qr, { errorCorrectionLevel: 'M', margin: 2, width: 240 })
      .then((url) => alive && setSrc(url))
      .catch(() => alive && setSrc(null));
    return () => {
      alive = false;
    };
  }, [ticket.qr]);
  return (
    <article className="ticket-card" aria-label={`Vé ${ticket.seq}/${total}`}>
      <div className="ticket-qr">
        {src ? <img src={src} width={240} height={240} alt={`Mã QR vé ${ticket.seq}`} /> : <div className="qr-placeholder" />}
      </div>
      <div className="ticket-info">
        <small>Vé {ticket.seq}/{total} · {ticket.bookingCode}</small>
        <strong>{ticket.ticketTypeName}</strong>
        <span>{vnd(ticket.unitPrice)}</span>
        <code title={ticket.id}>#{ticket.id.slice(0, 8).toUpperCase()}</code>
      </div>
    </article>
  );
}
