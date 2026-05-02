package org.javafreedom.kdiab.profiles

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File

class ConfigParsingTest {
    @Test
    fun `test config parses audience`() {
        val file = File("src/main/resources/application.conf")
        val config = HoconApplicationConfig(ConfigFactory.parseFile(file).resolve())
        println("Audience is: " + config.property("jwt.audience").getString())
        assertNotNull(config.property("jwt.audience").getString())
    }
}
