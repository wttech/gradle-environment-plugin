package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.environment.EnvironmentException
import java.net.URL

class Host(val url: String) {

    var ip = "127.0.0.1"

    var tags = mutableSetOf<String>()

    val config = URL(url)

    val text: String get() = "$ip\t${config.host}"

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

    override fun toString(): String = "Host(url='$url', ip='$ip', tags=$tags)"
}
