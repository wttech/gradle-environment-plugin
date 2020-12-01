package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.environment.EnvironmentException

class HostException : EnvironmentException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
