package com.openex.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [
        UserDetailsServiceAutoConfiguration::class
    ]
)
class OpenExApplication

fun main(args: Array<String>) {
    runApplication<OpenExApplication>(*args)
}