package com.fdl.platform.auth.service;

import com.fdl.platform.user.domain.EmailAlreadyRegisteredException;
import com.fdl.platform.user.domain.User;
import com.fdl.platform.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final Clock clock;
    // Hash de uma senha qualquer: o login de e-mail inexistente também faz uma verificação de
    // senha, para que o tempo de resposta não revele se o e-mail está cadastrado.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("senha-fictícia-para-igualar-o-tempo");
    }

    /**
     * Cadastra um usuário. A verificação prévia basta: o pool de uma única conexão do SQLite
     * serializa as transações, e a coluna de e-mail tem índice único como segunda barreira.
     */
    @Transactional
    public User register(String name, String email, String password) {
        var normalizedEmail = User.normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        return users.save(User.create(name, normalizedEmail, passwordEncoder.encode(password), clock));
    }

    @Transactional(readOnly = true)
    public JwtTokenService.IssuedToken login(String email, String password) {
        var user = users.findByEmailIgnoreCase(User.normalizeEmail(email)).orElse(null);
        var matches = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        return tokens.issue(user);
    }

    @Transactional(readOnly = true)
    public User currentUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new InsufficientAuthenticationException("O usuário do token não existe mais"));
    }
}
