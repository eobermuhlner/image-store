package ch.obermuhlner.imagestore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ImageStoreApplication

fun main(args: Array<String>) {
    runApplication<ImageStoreApplication>(*args)
}
