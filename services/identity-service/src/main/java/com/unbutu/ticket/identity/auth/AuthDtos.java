package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.identity.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

final class AuthDtos {

    private AuthDtos() {
    }

    /** Mật khẩu 8–72 ký tự: bcrypt chỉ dùng 72 byte đầu. */
    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 200) String fullName,
            @Size(max = 30) String phone) {
    }

    /** rememberMe: ô "Ghi nhớ đăng nhập" (null = false). */
    record LoginRequest(@NotBlank @Email String email, @NotBlank String password, Boolean rememberMe) {
    }

    record UserResponse(UUID id, String email, String fullName, String phone, List<String> roles,
                        boolean emailVerified, boolean hasPassword) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.roleList(),
                    u.isEmailVerified(), u.hasPassword());
        }
    }

    /** Access token trong body (UI giữ trong bộ nhớ); refresh token nằm ở cookie HttpOnly, không có ở đây. */
    record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
