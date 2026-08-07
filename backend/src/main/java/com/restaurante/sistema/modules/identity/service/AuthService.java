package com.restaurante.sistema.modules.identity.service;

import com.restaurante.sistema.common.exception.AuthenticationFailedException;
import com.restaurante.sistema.modules.identity.domain.User;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import com.restaurante.sistema.modules.identity.repository.UserRepository;
import com.restaurante.sistema.modules.identity.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Regras de autenticacao da Etapa 5.
 *
 * Limitacao de tentativas (requisito de seguranca do prompt original):
 * apos MAX_FAILED_ATTEMPTS tentativas incorretas consecutivas, a conta e
 * bloqueada por LOCK_DURATION_MINUTES. O contador zera a cada login bem-sucedido.
 *
 * A mensagem de erro nunca revela se o e-mail existe ou nao, para evitar
 * enumeracao de usuarios validos.
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;
    private static final String GENERIC_ERROR = "E-mail ou senha invalidos";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationFailedException(GENERIC_ERROR));

        if (!user.isEnabled()) {
            throw new AuthenticationFailedException(GENERIC_ERROR);
        }

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException(
                    "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente mais tarde.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedAttempt(user);
            throw new AuthenticationFailedException(GENERIC_ERROR);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes,
                new LoginResponse.UserSummary(user.getPublicId(), user.getEmail(), user.getProfile().getName())
        );
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
        }

        userRepository.save(user);
    }
}
