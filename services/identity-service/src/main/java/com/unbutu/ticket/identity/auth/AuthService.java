package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.identity.user.User;
import com.unbutu.ticket.identity.user.UserRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Hash giả để so khi email không tồn tại → thời gian trả lời như nhau, không dò được email nào đã đăng ký. */
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    User register(AuthDtos.RegisterRequest req) {
        String email = User.normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw AuthException.emailTaken();
        }
        User user = User.withPassword(email, passwordEncoder.encode(req.password()),
                blankToNull(req.fullName()), blankToNull(req.phone()), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {   // 2 request đăng ký cùng email song song
            throw AuthException.emailTaken();
        }
    }

    @Transactional(readOnly = true)
    User authenticate(String rawEmail, String password) {
        User user = users.findByEmail(User.normalizeEmail(rawEmail)).orElse(null);
        String hash = user != null && user.hasPassword() ? user.getPasswordHash() : dummyHash;
        boolean ok = passwordEncoder.matches(password, hash);
        if (user == null || !user.hasPassword() || !ok || !user.isActive()) {
            throw AuthException.invalidCredentials();
        }
        return user;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
