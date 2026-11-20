package com.unbutu.ticket.identity.auth;

import org.springframework.http.HttpStatus;
import lombok.Getter;

/** Lỗi nghiệp vụ trả về client dạng Problem Details, kèm {@code code} ổn định để UI xử lý. */
@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
    }

    static AuthException emailTaken() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã được đăng ký");
    }

    static AuthException invalidRefresh() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
    }
}
