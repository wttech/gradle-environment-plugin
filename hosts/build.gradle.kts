plugins {
    kotlin("jvm")
    application
    java
}

description = "Hosts Editor"
version = ""

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

application {
    mainClass.set("MainKt")
}

tasks {
    jar {
        manifest {
            attributes["Implementation-Title"] = project.description
            attributes["Main-Class"] = "MainKt"
        }
        from(configurations.runtimeClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
    }
}
