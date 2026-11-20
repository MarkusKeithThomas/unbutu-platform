package com.unbutu.ticket.identity.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public key để verify access token. Gateway gọi nội bộ (http://identity-service:8080/.well-known/jwks.json) và cache. */
@RestController
@RequiredArgsConstructor
class JwksController {

    private final RSAKey signingKey;

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
