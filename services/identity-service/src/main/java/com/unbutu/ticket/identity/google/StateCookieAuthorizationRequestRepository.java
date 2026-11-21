package com.unbutu.ticket.identity.google;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.unbutu.ticket.identity.token.JwtProperties;
import com.unbutu.ticket.identity.token.RefreshProperties;
import com.unbutu.ticket.identity.token.TokenUse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Lưu "authorization request" (state, nonce, redirect_uri…) giữa lúc chuyển sang Google và lúc Google gọi lại
 * trong 1 cookie thay vì HttpSession → nhiều pod identity chạy song song vẫn đúng (không cần sticky session).
 * Nội dung cookie là JWT ký RS256 bằng key của identity (token_use=oauth2_state, sống 10′) → không sửa được,
 * và không deserialize object Java từ dữ liệu client gửi lên.
 */
@Component
class StateCookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "oauth2_auth_request";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final JwtProperties jwtProps;
    private final String keyId;
    private final RefreshProperties cookieProps;
    private final Clock clock;

    StateCookieAuthorizationRequestRepository(JwtEncoder encoder, RSAKey signingKey, JwtProperties jwtProps,
                                              RefreshProperties cookieProps, Clock clock) throws JOSEException {
        this.encoder = encoder;
        this.jwtProps = jwtProps;
        this.keyId = signingKey.getKeyID();
        this.cookieProps = cookieProps;
        this.clock = clock;
        this.decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwtProps.issuer()), TokenUse.validator(TokenUse.OAUTH2_STATE)));
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = readCookie(request);
        if (value == null) {
            return null;
        }
        try {
            return fromJwt(decoder.decode(value));
        } catch (JwtException e) {
            return null;   // hết hạn / bị sửa → Spring báo lỗi "authorization_request_not_found"
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest req, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (req == null) {
            clear(response);
            return;
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProps.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim(TokenUse.CLAIM, TokenUse.OAUTH2_STATE)
                .claim("authorization_uri", req.getAuthorizationUri())
                .claim("client_id", req.getClientId())
                .claim("redirect_uri", req.getRedirectUri())
                .claim("scopes", List.copyOf(req.getScopes()))
                .claim("state", req.getState())
                .claim("additional_parameters", stringMap(req.getAdditionalParameters()))
                .claim("attributes", stringMap(req.getAttributes()))
                .claim("authorization_request_uri", req.getAuthorizationRequestUri())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(jwt, TTL).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
        clear(response);
        return req;
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** SameSite=Lax: Google chuyển trình duyệt về bằng GET điều hướng top-level → cookie vẫn được gửi kèm. */
    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieProps.cookieSecure())
                .sameSite("Lax").path(cookieProps.cookiePath()).maxAge(maxAge).build();
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(c -> COOKIE.equals(c.getName()) && !c.getValue().isBlank())
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private static OAuth2AuthorizationRequest fromJwt(Jwt jwt) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(jwt.getClaimAsString("authorization_uri"))
                .clientId(jwt.getClaimAsString("client_id"))
                .redirectUri(jwt.getClaimAsString("redirect_uri"))
                .scopes(Set.copyOf(jwt.getClaimAsStringList("scopes")))
                .state(jwt.getClaimAsString("state"))
                .additionalParameters(Map.copyOf(jwt.getClaimAsMap("additional_parameters")))
                .attributes(Map.copyOf(jwt.getClaimAsMap("attributes")))
                .authorizationRequestUri(jwt.getClaimAsString("authorization_request_uri"))
                .build();
    }

    /** state/nonce/registration_id/code_verifier đều là chuỗi. */
    private static Map<String, String> stringMap(Map<String, Object> in) {
        Map<String, String> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (v != null) {
                out.put(k, v.toString());
            }
        });
        return out;
    }
}
