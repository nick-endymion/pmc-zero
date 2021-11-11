package org.endy.pmczero

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PmcZeroApplication

fun main(args: Array<String>) {
    runApplication<PmcZeroApplication>(*args)
}
