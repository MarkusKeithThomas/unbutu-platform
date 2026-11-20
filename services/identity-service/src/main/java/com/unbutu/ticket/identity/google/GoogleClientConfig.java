package com.unbutu.ticket.identity.google;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/** Chỉ bật khi có GOOGLE_CLIENT_ID; không có → không có login Google, service vẫn chạy (local không cần Google). */
@Configuration
@ConditionalOnExpression("!'${identity.google.client-id:}'.isBlank()")
class GoogleClientConfig {

    static final String REGISTRATION_ID = "google";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleProperties props) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .redirectUri(props.redirectUri())
                .scope("openid", "email", "profile")
                .build());
    }
}
