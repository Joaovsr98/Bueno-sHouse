package com.restaurante.sistema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Etapa 8 - painel da cozinha em tempo real.
 *
 * Endpoint de conexao: /ws (com fallback SockJS para redes que bloqueiam
 * WebSocket puro). O frontend deve se inscrever no topico
 * "/topic/kitchen/{unitId}" para receber atualizacoes de itens de pedido
 * (novo item enviado, item iniciado, item pronto).
 *
 * Seguranca: o handshake STOMP e autenticado por
 * {@link WebSocketAuthChannelInterceptor}, que valida o mesmo JWT usado no REST
 * no frame CONNECT. Conexoes sem token valido sao recusadas - o painel em tempo
 * real exige login, assim como os endpoints REST.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
