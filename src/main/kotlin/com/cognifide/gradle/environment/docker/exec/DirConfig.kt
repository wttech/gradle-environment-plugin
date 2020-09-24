package com.cognifide.gradle.environment.docker.exec

class DirConfig {
    var owner: String = ""

    var group: String = ""

    var mode: String = ""

    fun own(owner: String, group: String, mode: String) {
        this.owner = owner
        this.group = group
        this.mode = mode
    }
}