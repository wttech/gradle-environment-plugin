package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.environment.EnvironmentException
import java.net.MalformedURLException
import java.net.URL

class Host(url: String) {

    var name = try {
        URL(url).host
    } catch (e: MalformedURLException) {
        url
    }

    var ip = "127.0.0.1"

    var tags = mutableSetOf<String>()

    val text: String get() = "$ip\t$name"

    fun tag(id: String) {
        tags.add(id)
    }

    fun tag(ids: Iterable<String>) {
        tags.addAll(ids)
    }

    fun tag(vararg ids: String) = tag(ids.asIterable())

    init {
        if (url.isBlank()) {
            throw EnvironmentException("Host URL cannot be blank!")
        }
    }

    override fun toString(): String = "Host(name='$name', ip='$ip', tags=$tags)"
}
