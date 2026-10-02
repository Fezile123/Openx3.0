package com.openex.core.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms}") private val expirationMs: Long
) {

    private val signingKey by lazy {
        Keys.hmacShaKeyFor(
            secret.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun generateToken(accountId: UUID, email: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(accountId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun extractAccountId(token: String): UUID {
        val subject = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject

        return UUID.fromString(subject)
    }

    fun isValid(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)

            true
        } catch (ex: Exception) {
            false
        }
    }
}