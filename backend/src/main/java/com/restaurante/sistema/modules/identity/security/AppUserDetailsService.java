package com.restaurante.sistema.modules.identity.security;

import com.restaurante.sistema.modules.identity.domain.User;
import com.restaurante.sistema.modules.identity.repository.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @Transactional aqui e obrigatorio: User.getAuthorities() le
     * profile.getPermissions() (ManyToMany LAZY), e o JwtAuthenticationFilter
     * chama getAuthorities() fora de qualquer transacao/sessao Hibernate. Sem
     * inicializar a colecao ainda dentro da transacao deste metodo, qualquer
     * requisicao autenticada estoura LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + email));
        Hibernate.initialize(user.getProfile().getPermissions());
        return user;
    }
}
