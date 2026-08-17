package com.openex.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Allows the React dev server (localhost:5173) to call the REST API
 * directly from the browser. Without this, the browser's CORS policy
 * blocks all fetch() calls to a different origin/port, even though the
 * WebSocket endpoint already allows it separately (see WebSocketConfig).
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }
}