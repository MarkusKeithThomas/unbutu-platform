package com.unbutu.ticket.identity.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.google")
public record GoogleProperties(String clientId, String clientSecret, String redirectUri) {

    public boolean enabled() {
        return clientId != null && !clientId.isBlank();
    }
}
