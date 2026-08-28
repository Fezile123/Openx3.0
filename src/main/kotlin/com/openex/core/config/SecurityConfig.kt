package com.openex.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity
    ): SecurityFilterChain {

        http
            .csrf { csrf ->
                csrf.disable()
            }
            .cors { }
            .sessionManagement { session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            }
            .authorizeHttpRequests { auth ->
                auth
                    // CORS preflight
                    .requestMatchers(
                        HttpMethod.OPTIONS,
                        "/**"
                    )
                    .permitAll()

                    // Authentication
                    .requestMatchers(
                        "/api/auth/**"
                    )
                    .permitAll()

                    // Health check
                    .requestMatchers(
                        "/health"
                    )
                    .permitAll()

                    // SockJS / STOMP WebSocket endpoint
                    .requestMatchers(
                        "/ws",
                        "/ws/**"
                    )
                    .permitAll()

                    // Public trading GET endpoints
                    .requestMatchers(
                        HttpMethod.GET,
                        "/orderbook",
                        "/orders",
                        "/trades",
                        "/wallets"
                    )
                    .permitAll()

                    // Public API-prefixed trading GET endpoints
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/orderbook",
                        "/api/orders",
                        "/api/trades",
                        "/api/wallets"
                    )
                    .permitAll()

                    // Everything else requires JWT
                    .anyRequest()
                    .authenticated()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
