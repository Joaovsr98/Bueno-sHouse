package com.restaurante.sistema.config;

import com.restaurante.sistema.modules.identity.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracao de seguranca da Etapa 5.
 *
 * Mudancas em relacao a Etapa 4 (fundacao):
 * - JWT real: JwtAuthenticationFilter e inserido antes do filtro padrao de
 *   usuario/senha do Spring Security.
 * - Endpoints publicos: apenas health check e login. Todo o resto exige
 *   autenticacao a partir de agora (mesmo que ainda nao existam endpoints
 *   de negocio - eles ja nascerao protegidos por padrao).
 * - Autorizacao fina por perfil/permissao (@PreAuthorize) sera aplicada nos
 *   controllers de cada modulo de negocio a partir da Etapa 6.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sem token ou token expirado -> 401 (o padrao seria 403); o frontend usa
            // o 401 para encerrar a sessao e voltar ao login (RF-042).
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                            org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                // Cardapio publico (RF-001, RF-002): leitura sem login; escrita
                // continua exigindo ADMINISTRADOR/GERENTE via @PreAuthorize nos
                // controllers (RN09: so pratos ATIVO aparecem, ja filtrado no Service).
                // /api/units GET tambem precisa ser publico: e como o cardapio
                // descobre qual unidade exibir antes do cliente logar.
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/products", "/api/products/**", "/api/units", "/api/units/**")
                    .permitAll()
                // Bairros atendidos e taxas: o cadastro do cliente (antes do login) usa para o endereco.
                .requestMatchers(HttpMethod.GET, "/api/delivery-zones").permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
