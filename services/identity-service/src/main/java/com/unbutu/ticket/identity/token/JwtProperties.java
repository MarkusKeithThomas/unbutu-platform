package com.unbutu.ticket.identity.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String privateKey,
        String keyId,
        boolean allowEphemeralKey) {
}
