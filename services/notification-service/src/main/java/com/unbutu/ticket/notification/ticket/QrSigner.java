package com.unbutu.ticket.notification.ticket;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Ký QR vé = JWS compact ES256 {@code {tid, eid, tt, iat}} (header có kid). Cổng soát vé verify offline bằng JWKS
 * {@code /api/tickets/.well-known/jwks.json} – không cần gọi server; chống dùng lại (1 vé vào 2 lần) là việc của check-in
 * (đánh dấu USED có điều kiện) – làm sau. Payload ngắn → QR ít ô, quét nhanh.
 */
@Slf4j
@Component
public class QrSigner {

    @ConfigurationProperties("notification.qr")
    record QrProps(String signingKey, String keyId, boolean allowEphemeralKey) {
    }

    @Configuration
    @EnableConfigurationProperties(QrProps.class)
    static class PropsConfig {
    }

    private final ECKey key;
    private final ECDSASigner signer;

    QrSigner(QrProps props) throws GeneralSecurityException, JOSEException, java.text.ParseException {
        this.key = load(props);
        this.signer = new ECDSASigner(key);
        var probe = SignedJWT.parse(sign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH));
        if (!probe.verify(new com.nimbusds.jose.crypto.ECDSAVerifier(key.toECPublicKey()))) {
            throw new IllegalStateException("QR_SIGNING_KEY: public key kèm theo không khớp private key");
        }
        log.info("Khoá ký QR sẵn sàng kid={}", key.getKeyID());
    }

    private static ECKey load(QrProps props) throws GeneralSecurityException, JOSEException {
        if (props.signingKey() != null && !props.signingKey().isBlank()) {
            String b64 = props.signingKey().replaceAll("-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPrivateKey priv;
            try {
                priv = (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (java.security.spec.InvalidKeySpecException notPkcs8) {
                // OpenSSL 3.x `genpkey -outform DER` ghi SEC1 (ECPrivateKey), OpenSSL 4 ghi PKCS#8 → nhận cả hai.
                priv = (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(wrapSec1(der)));
            }
            if (!Curve.forECParameterSpec(priv.getParams()).equals(Curve.P_256)) {
                throw new IllegalStateException("QR_SIGNING_KEY phải là EC P-256 (ES256)");
            }
            ECPublicKey pub = (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(embeddedPublic(der), priv.getParams()));
            return new ECKey.Builder(Curve.P_256, pub).privateKey(priv).keyID(props.keyId()).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256).build();
        }
        if (!props.allowEphemeralKey()) {
            throw new IllegalStateException("Thiếu notification.qr.signing-key (QR_SIGNING_KEY) – bắt buộc ngoài local/test");
        }
        ECKey generated = new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).generate();
        // kid theo thumbprint: key tạm đổi mỗi lần khởi động → kid đổi theo (như identity).
        String kid = props.keyId() + "-" + generated.computeThumbprint().toString().substring(0, 8);
        log.warn("Không có QR_SIGNING_KEY → tự sinh key EC tạm kid={} (chỉ local/test)", kid);
        return new ECKey.Builder(generated).keyID(kid).build();
    }

    /**
     * Khoá do {@code openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -outform DER} sinh (PKCS#8 hoặc SEC1 tuỳ
     * phiên bản OpenSSL) đều có kèm public key (ECPrivateKey [1] BIT STRING 04‖X‖Y). Java không suy public từ private được (không có BouncyCastle) → đọc phần này,
     * rồi ký thử + verify lúc khởi động để chắc cặp khoá khớp.
     */
    private static ECPoint embeddedPublic(byte[] der) {
        byte[] marker = {0x03, 0x42, 0x00, 0x04};
        for (int i = der.length - 68; i >= 0; i--) {
            if (der[i] == marker[0] && der[i + 1] == marker[1] && der[i + 2] == marker[2] && der[i + 3] == marker[3]) {
                var x = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(der, i + 4, i + 36));
                var y = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(der, i + 36, i + 68));
                return new ECPoint(x, y);
            }
        }
        throw new IllegalStateException("QR_SIGNING_KEY không kèm public key – tạo bằng: openssl genpkey -algorithm EC "
                + "-pkeyopt ec_paramgen_curve:P-256 -outform DER | base64");
    }

    /** PrivateKeyInfo ::= SEQUENCE { INTEGER 0, AlgorithmIdentifier {ecPublicKey, prime256v1}, OCTET STRING sec1 } */
    private static byte[] wrapSec1(byte[] sec1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algId = {0x30, 0x13, 0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01,
                0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03, 0x01, 0x07};
        byte[] octet = derTlv(0x04, sec1);
        byte[] body = new byte[version.length + algId.length + octet.length];
        System.arraycopy(version, 0, body, 0, version.length);
        System.arraycopy(algId, 0, body, version.length, algId.length);
        System.arraycopy(octet, 0, body, version.length + algId.length, octet.length);
        return derTlv(0x30, body);
    }

    private static byte[] derTlv(int tag, byte[] value) {
        int len = value.length;
        byte[] lenBytes = len < 0x80 ? new byte[] {(byte) len}
                : len < 0x100 ? new byte[] {(byte) 0x81, (byte) len}
                : new byte[] {(byte) 0x82, (byte) (len >> 8), (byte) len};
        byte[] out = new byte[1 + lenBytes.length + len];
        out[0] = (byte) tag;
        System.arraycopy(lenBytes, 0, out, 1, lenBytes.length);
        System.arraycopy(value, 0, out, 1 + lenBytes.length, len);
        return out;
    }

    public String sign(UUID ticketId, UUID eventId, UUID ticketTypeId, Instant issuedAt) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .claim("tid", ticketId.toString())
                    .claim("eid", eventId.toString())
                    .claim("tt", ticketTypeId.toString())
                    .issueTime(Date.from(issuedAt))
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Ký QR lỗi", e);
        }
    }

    /** JWKS chỉ có public key. */
    public Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
