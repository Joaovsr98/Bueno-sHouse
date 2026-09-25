package com.restaurante.sistema.modules.identity.service;

import com.restaurante.sistema.common.exception.AuthenticationFailedException;
import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.EmailAlreadyExistsException;
import com.restaurante.sistema.modules.customers.domain.Customer;
import com.restaurante.sistema.modules.customers.repository.CustomerRepository;
import com.restaurante.sistema.modules.identity.domain.Profile;
import com.restaurante.sistema.modules.identity.domain.User;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import com.restaurante.sistema.modules.identity.dto.RegisterRequest;
import com.restaurante.sistema.modules.identity.repository.ProfileRepository;
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

    private static final String CLIENTE_PROFILE_NAME = "CLIENTE";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public AuthService(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Cadastro publico de Cliente (RF-004, RF-039). Sempre cria o usuario com
     * perfil CLIENTE - nunca aceita perfil vindo do request (RF-039: cadastro
     * publico so cria CLIENTE). Cria tambem o Customer vinculado, para que o
     * cliente logado ja consiga pedir/ver seus proprios dados (RN10: e-mail
     * unico -> 409; telefone unico -> 422, mesma regra do cadastro por staff).
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException("Ja existe uma conta com este e-mail");
        }
        if (customerRepository.findByPhone(request.phone()).isPresent()) {
            throw new BusinessException("Ja existe um cliente cadastrado com este telefone");
        }

        Profile clienteProfile = profileRepository.findByName(CLIENTE_PROFILE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Perfil CLIENTE nao encontrado - verifique o seed (V900__seed_demo_data.sql)"));

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProfile(clienteProfile);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        Customer customer = new Customer();
        customer.setUserId(user.getId());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setCreatedAt(Instant.now());
        customerRepository.save(customer);

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes,
                new LoginResponse.UserSummary(user.getPublicId(), user.getEmail(), user.getProfile().getName())
        );
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
