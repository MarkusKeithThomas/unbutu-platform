package com.unbutu.ticket.identity.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Mọi JWT do identity ký đều mang claim {@code token_use} → JWT loại này không dùng thay loại kia được
 * (vd. cookie trạng thái OAuth không thể dùng làm access token).
 */
public final class TokenUse {

    public static final String CLAIM = "token_use";
    public static final String ACCESS = "access";
    public static final String OAUTH2_STATE = "oauth2_state";

    private TokenUse() {
    }

    public static OAuth2TokenValidator<Jwt> validator(String expected) {
        return jwt -> expected.equals(jwt.getClaimAsString(CLAIM))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token_use must be " + expected, null));
    }
}
