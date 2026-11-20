package com.unbutu.ticket.identity.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.ui")
public record UiProperties(String baseUrl) {
}
