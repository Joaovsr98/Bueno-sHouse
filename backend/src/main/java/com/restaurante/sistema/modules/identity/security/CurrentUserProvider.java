package com.restaurante.sistema.modules.identity.security;

import com.restaurante.sistema.modules.identity.domain.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ponto unico para os modulos de negocio obterem o usuario autenticado atual
 * (necessario para preencher "opened_by", "registered_by", "changed_by" etc.
 * em varias tabelas, conforme regra de auditoria do projeto).
 */
@Component
public class CurrentUserProvider {

    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new IllegalStateException("Nenhum usuario autenticado no contexto de seguranca");
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
