package com.unbutu.ticket.payment.mock;

import com.unbutu.ticket.payment.mock.fault.Dice;
import com.unbutu.ticket.payment.mock.fault.MockFaults;
import com.unbutu.ticket.payment.vnpay.VnpaySigner;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * querydr của ngân hàng giả lập – đúng hình dạng VNPay merchant_webapi 2.1.0 (JSON, chữ ký HMAC-SHA512 nối "|").
 * Mã: 00 tìm thấy · 91 không thấy giao dịch · 97 sai chữ ký · 99 lỗi (lỗi giả lập querydr-error-rate).
 * vnp_TransactionStatus: 00 thành công · 01 chưa hoàn tất (OPEN / PROCESSING) · 02 lỗi/huỷ.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
class MockQueryDrController {

    private final VnpaySigner signer;
    private final MockBank bank;
    private final MockFaults faults;
    private final Dice dice;

    @PostMapping("/api/payments/mock/merchant_webapi/api/transaction")
    ResponseEntity<Map<String, String>> query(@RequestBody Map<String, String> r) {
        String cmd = r.getOrDefault("vnp_Command", "");
        if (!"querydr".equals(cmd)) {
            return ResponseEntity.ok(response(r, "99", "Command not supported", null));
        }
        if (!signer.verifyPipe(r.get("vnp_SecureHash"), r.get("vnp_RequestId"), r.get("vnp_Version"), cmd, r.get("vnp_TmnCode"),
                r.get("vnp_TxnRef"), r.get("vnp_TransactionDate"), r.get("vnp_CreateDate"), r.get("vnp_IpAddr"), r.get("vnp_OrderInfo"))) {
            return ResponseEntity.ok(response(r, "97", "Invalid Checksum", null));
        }
        if (dice.roll(faults.querydrErrorRate())) {
            log.warn("GIẢ LẬP querydr-error cho {}", r.get("vnp_TxnRef"));
            return ResponseEntity.ok(response(r, "99", "Unknown error (giả lập)", null));
        }
        var txn = bank.find(r.getOrDefault("vnp_TxnRef", ""))
                .filter(t -> t.createDate().equals(r.get("vnp_TransactionDate")));
        return ResponseEntity.ok(txn.map(t -> response(r, "00", "QueryDR Success", t))
                .orElseGet(() -> response(r, "91", "Transaction not found", null)));
    }

    private Map<String, String> response(Map<String, String> req, String code, String msg, MockBank.Txn t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("vnp_ResponseId", UUID.randomUUID().toString().replace("-", ""));
        m.put("vnp_Command", "querydr");
        m.put("vnp_ResponseCode", code);
        m.put("vnp_Message", msg);
        m.put("vnp_TmnCode", req.getOrDefault("vnp_TmnCode", ""));
        m.put("vnp_TxnRef", req.getOrDefault("vnp_TxnRef", ""));
        m.put("vnp_Amount", t == null ? "" : Long.toString(t.amount() * 100));
        m.put("vnp_BankCode", t == null ? "" : t.bankCode());
        m.put("vnp_PayDate", t == null || t.payDate() == null ? "" : t.payDate());
        m.put("vnp_TransactionNo", t == null || t.transactionNo() == null ? "" : t.transactionNo());
        m.put("vnp_TransactionType", "01");
        m.put("vnp_TransactionStatus", t == null ? "" : switch (t.status()) {
            case "SUCCESS" -> "00";
            case "OPEN", "PROCESSING" -> "01";
            default -> "02";
        });
        m.put("vnp_OrderInfo", t == null ? "" : t.orderInfo());
        m.put("vnp_PromotionCode", "");
        m.put("vnp_PromotionAmount", "");
        m.put("vnp_SecureHash", signer.signPipe(m.get("vnp_ResponseId"), "querydr", code, msg, m.get("vnp_TmnCode"), m.get("vnp_TxnRef"),
                m.get("vnp_Amount"), m.get("vnp_BankCode"), m.get("vnp_PayDate"), m.get("vnp_TransactionNo"), "01",
                m.get("vnp_TransactionStatus"), m.get("vnp_OrderInfo"), "", ""));
        // Mã lỗi cuối giao dịch (để merchant biết huỷ/không đủ tiền) – trường thật của VNPay querydr.
        if (t != null && t.responseCode() != null) {
            m.put("vnp_TransactionResponseCode", t.responseCode());
        }
        return m;
    }
}
