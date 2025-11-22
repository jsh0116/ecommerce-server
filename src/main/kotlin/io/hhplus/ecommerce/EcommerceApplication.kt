package io.hhplus.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class EcommerceApplication

fun main(args: Array<String>) {
    runApplication<EcommerceApplication>(*args)
}
