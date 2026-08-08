package com.restaurante.sistema.config;

import com.restaurante.sistema.modules.identity.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Autentica o handshake STOMP: valida o JWT no frame CONNECT antes de aceitar a
 * conexao ao endpoint /ws. Sem isso, qualquer cliente poderia se inscrever no
 * painel da cozinha em tempo real sem estar logado (a lacuna de seguranca que
 * existia enquanto so o REST validava token).
 *
 * O cliente deve enviar o header nativo "Authorization: Bearer <token>" no
 * CONNECT. Token ausente/invalido derruba a conexao (excecao propagada).
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("WebSocket: token JWT ausente no CONNECT");
            }

            String token = authHeader.substring(7);
            String username;
            try {
                username = jwtService.extractUsername(token);
            } catch (Exception ex) {
                throw new IllegalArgumentException("WebSocket: token JWT invalido");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(token, userDetails)) {
                throw new IllegalArgumentException("WebSocket: token JWT invalido ou expirado");
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
        }

        return message;
    }
}
