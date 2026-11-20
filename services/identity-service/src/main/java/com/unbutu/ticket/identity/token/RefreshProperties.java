package com.unbutu.ticket.identity.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.refresh")
public record RefreshProperties(
        Duration ttl,
        String cookieName,
        String cookiePath,
        boolean cookieSecure) {
}
