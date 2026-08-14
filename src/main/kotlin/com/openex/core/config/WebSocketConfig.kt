package com.openex.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * Enables STOMP-over-WebSocket messaging. Clients connect to /ws, then
 * subscribe to topics like /topic/orderbook/BTC-USD or /topic/trades/BTC-USD
 * to receive live pushes whenever the matching engine changes state.
 *
 * We don't accept messages FROM clients here (no /app prefix wired up) —
 * this is broadcast-only for now. Clients still place orders via the
 * existing REST API; the socket is purely for receiving live updates.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // frontend runs on a different port (5173) during dev
            .withSockJS() // fallback for environments/browsers without native WebSocket support
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
    }
}