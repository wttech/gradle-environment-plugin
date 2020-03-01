package com.cognifide.gradle.environment.health

import com.cognifide.gradle.common.http.HttpClient
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus

class HttpCheck(val url: String) {

    var method = "GET"

    internal var options: HttpClient.() -> Unit = {}

    fun options(options: HttpClient.() -> Unit) {
        this.options = options
    }

    internal val checks = mutableListOf<HttpClient.(HttpResponse) -> Unit>()

    fun check(spec: HttpClient.(HttpResponse) -> Unit) {
        checks.add(spec)
    }

    // Shorthand methods for checking responses

    fun containsText(text: String, statusCode: Int = HttpStatus.SC_OK) {
        check { response ->
            checkStatus(response, statusCode)
            checkText(response, text)
        }
    }
}
