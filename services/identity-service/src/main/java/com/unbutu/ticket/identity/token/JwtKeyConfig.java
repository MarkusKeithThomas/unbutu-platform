package com.unbutu.ticket.identity.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Khoá ký JWT (RS256). Private key chỉ ở identity-service; public key công bố qua JWKS cho gateway.
 * Prod: {@code JWT_PRIVATE_KEY} (PEM PKCS#8) từ AWS Secrets Manager. Local/test: được phép tự sinh key tạm.
 */
@Configuration
@Slf4j
class JwtKeyConfig {

    @Bean
    RSAKey signingKey(JwtProperties props) throws GeneralSecurityException, JOSEException {
        if (props.privateKey() != null && !props.privateKey().isBlank()) {
            RSAPrivateCrtKey privateKey = parsePrivateKey(props.privateKey());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(props.keyId()).keyUse(KeyUse.SIGNATURE).build();
        }
        if (!props.allowEphemeralKey()) {
            throw new IllegalStateException("Thiếu identity.jwt.private-key (JWT_PRIVATE_KEY) – bắt buộc ngoài local/test");
        }
        log.warn("Không có JWT_PRIVATE_KEY → tự sinh key RSA tạm (chỉ dùng local/test, token mất hiệu lực khi restart)");
        return new RSAKeyGenerator(2048).keyID(props.keyId()).keyUse(KeyUse.SIGNATURE).generate();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** identity tự verify access token của chính nó (cho /api/identity/me). Chỉ nhận token_use=access. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()), TokenUse.validator(TokenUse.ACCESS)));
        return decoder;
    }

    /**
     * Nhận private key RSA dạng base64 (có hoặc không có dòng BEGIN/END), DER theo:
     * <ul>
     *   <li>PKCS#8 ({@code PrivateKeyInfo}) – {@code openssl pkcs8 -topk8 -nocrypt -outform DER}</li>
     *   <li>PKCS#1 ({@code RSAPrivateKey}) – {@code openssl genpkey … -outform DER} (OpenSSL 3+) ghi dạng này</li>
     * </ul>
     * PKCS#1 được bọc thành PKCS#8 trước khi nạp (Java chỉ đọc PKCS#8).
     */
    static RSAPrivateCrtKey parsePrivateKey(String encoded) throws GeneralSecurityException {
        String base64 = encoded.replaceAll("-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        try {
            return (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException notPkcs8) {
            return (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1(der)));
        }
    }

    /** PrivateKeyInfo ::= SEQUENCE { INTEGER 0, AlgorithmIdentifier rsaEncryption NULL, OCTET STRING pkcs1 } */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] rsaAlgorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octet = derTlv(0x04, pkcs1);
        byte[] body = new byte[version.length + rsaAlgorithm.length + octet.length];
        System.arraycopy(version, 0, body, 0, version.length);
        System.arraycopy(rsaAlgorithm, 0, body, version.length, rsaAlgorithm.length);
        System.arraycopy(octet, 0, body, version.length + rsaAlgorithm.length, octet.length);
        return derTlv(0x30, body);
    }

    private static byte[] derTlv(int tag, byte[] value) {
        int len = value.length;
        byte[] lenBytes = len < 0x80 ? new byte[] {(byte) len}
                : len < 0x100 ? new byte[] {(byte) 0x81, (byte) len}
                : len < 0x10000 ? new byte[] {(byte) 0x82, (byte) (len >> 8), (byte) len}
                : new byte[] {(byte) 0x83, (byte) (len >> 16), (byte) (len >> 8), (byte) len};
        byte[] out = new byte[1 + lenBytes.length + len];
        out[0] = (byte) tag;
        System.arraycopy(lenBytes, 0, out, 1, lenBytes.length);
        System.arraycopy(value, 0, out, 1 + lenBytes.length, len);
        return out;
    }
}
