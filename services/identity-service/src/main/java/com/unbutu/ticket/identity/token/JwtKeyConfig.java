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
            RSAPrivateCrtKey privateKey = parsePkcs8(props.privateKey());
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

    /** identity tự verify access token của chính nó (cho /api/identity/me). */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuer())));
        return decoder;
    }

    private static RSAPrivateCrtKey parsePkcs8(String pem) throws GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
