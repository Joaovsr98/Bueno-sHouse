package com.restaurante.sistema.modules.identity.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.EmailAlreadyExistsException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.identity.domain.Profile;
import com.restaurante.sistema.modules.identity.domain.User;
import com.restaurante.sistema.modules.identity.dto.UserRequest;
import com.restaurante.sistema.modules.identity.dto.UserResponse;
import com.restaurante.sistema.modules.identity.dto.UserUpdateRequest;
import com.restaurante.sistema.modules.identity.repository.ProfileRepository;
import com.restaurante.sistema.modules.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Gestao de usuarios internos (staff) pelo ADMINISTRADOR (RF-041). */
@Service
public class UserService {

    private static final String CLIENTE = "CLIENTE";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listStaff() {
        return userRepository.findAll().stream()
                .filter(u -> !CLIENTE.equals(u.getProfile().getName()))
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException("Ja existe uma conta com este e-mail");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProfile(staffProfile(request.profileName()));
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, Long currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (CLIENTE.equals(user.getProfile().getName())) {
            throw new BusinessException("Clientes nao sao gerenciados por este endpoint");
        }
        boolean self = id.equals(currentUserId);
        if (request.profileName() != null) {
            if (self) {
                throw new BusinessException("Voce nao pode alterar o proprio perfil");
            }
            user.setProfile(staffProfile(request.profileName()));
        }
        if (request.active() != null) {
            if (self && !request.active()) {
                throw new BusinessException("Voce nao pode desativar a propria conta");
            }
            user.setActive(request.active());
        }
        if (request.newPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user.setUpdatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    private Profile staffProfile(String name) {
        if (CLIENTE.equals(name)) {
            throw new BusinessException("Perfil CLIENTE so e criado pelo cadastro publico");
        }
        return profileRepository.findByName(name)
                .orElseThrow(() -> new BusinessException("Perfil inexistente: " + name));
    }
}
