package com.openex.core.service

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthDebugTest {

    @Test
    fun `verify seeded password hash`() {
        val encoder = BCryptPasswordEncoder()

        val hash =
            "\$2a\$10\$gqpDd1Tx7ysUAEjMkeCBwOIJ2WvJZKrJwsb4sUDtpun/iVOALN1ti"

        println("PASSWORD_MATCHES=" + encoder.matches("password", hash))
        println("WRONG_PASSWORD_MATCHES=" + encoder.matches("wrong-password", hash))
    }
}
