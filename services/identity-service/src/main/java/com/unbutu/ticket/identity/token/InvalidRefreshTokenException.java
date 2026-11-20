package com.unbutu.ticket.identity.token;

/** Refresh token không tồn tại / hết hạn / đã thu hồi → client phải đăng nhập lại. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
