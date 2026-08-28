package com.openex.core.config

import com.openex.core.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
private val jwtService: JwtService
) : OncePerRequestFilter() {

private val log =
    LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
) {

    val authHeader =
        request.getHeader("Authorization")

    log.info(
        "JWT filter: method={} uri={} authorizationPresent={}",
        request.method,
        request.requestURI,
        authHeader != null
    )

    if (
        authHeader == null ||
        !authHeader.startsWith("Bearer ")
    ) {
        log.info(
            "JWT filter: no Bearer token"
        )

        filterChain.doFilter(
            request,
            response
        )

        return
    }

    val token =
        authHeader.substring(7)

    if (jwtService.isValid(token)) {

        val accountId =
            jwtService.extractAccountId(token)

        log.info(
            "JWT filter: valid token accountId={}",
            accountId
        )

        val authentication =
            UsernamePasswordAuthenticationToken(
                accountId,
                null,
                emptyList()
            )

        SecurityContextHolder
            .getContext()
            .authentication = authentication

    } else {

        log.warn(
            "JWT filter: INVALID token"
        )
    }

    filterChain.doFilter(
        request,
        response
    )
}

}
