package com.openex.core.service

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthDebugTest {

    @Test
    fun `generate password hash`() {
        val encoder = BCryptPasswordEncoder()

        val hash = encoder.encode("password")

        println("NEW_HASH=$hash")
        println("MATCHES=" + encoder.matches("password", hash))
    }
}