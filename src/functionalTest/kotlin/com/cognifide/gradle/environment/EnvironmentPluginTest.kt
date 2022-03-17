package com.cognifide.gradle.environment

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class EnvironmentPluginTest {

    @Test
    fun `can run task`() {
        // Setup the test build
        val projectDir = File("build/functionalTest").apply {
            deleteRecursively()
            mkdirs()
            resolve("settings.gradle.kts").writeText("")
            resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("com.cognifide.environment")
                }
                """.trimIndent()
            )
            resolve("src/environment/docker-compose.yml.peb").apply { parentFile.mkdirs() }.writeText(
                """
                version: "3"
                services:
                  dispatcher:
                    image: centos/httpd:latest
                    command: ["tail", "-f", "--retry", "/usr/local/apache2/logs/error.log"]
                    ports:
                      - "80:80"
                    volumes:
                      - "{{ rootPath }}/app/aem/origin/dispatcher/src/conf.d:/etc/httpd/conf.d"
                      - "{{ rootPath }}/app/aem/origin/dispatcher/src/conf.dispatcher.d:/etc/httpd/conf.dispatcher.d"
                      - "{{ sourcePath }}/dispatcher:/etc/httpd.extra"
                      - "{{ workPath }}/dispatcher/modules/mod_dispatcher.so:/etc/httpd/modules/mod_dispatcher.so"
                      - "{{ workPath }}/dispatcher/logs:/etc/httpd/logs"
                      {% if docker.runtime.safeVolumes %}
                      - "{{ workPath }}/dispatcher/cache:/var/www/localhost/cache"
                      - "{{ workPath }}/dispatcher/htdocs:/var/www/localhost/htdocs"
                      {% endif %}
                    {% if docker.runtime.hostInternalIpMissing %}
                    extra_hosts:
                      - "host.docker.internal:{{ docker.runtime.hostInternalIp }}"
                    {% endif %}
                """.trimIndent()
            )
        }

        // Run the build
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("environmentResolve") // TODO 'withArguments("environmentHosts")' after fixing classpath issue
            .withProjectDir(projectDir)
            .build()

        // Verify the result
        assertEquals(TaskOutcome.SUCCESS, result.task(":environmentResolve")?.outcome)
    }
}
