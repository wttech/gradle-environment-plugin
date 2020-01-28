import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.3.41"
}

defaultTasks("publishToMavenLocal")
description = "Gradle Environment Plugin"
group = "com.cognifide.gradle"

repositories {
    mavenLocal()
    jcenter()
}

dependencies {
    implementation("com.cognifide.gradle:common-plugin:1.0.0")
    implementation("org.buildobjects:jproc:2.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("commons-io:commons-io:2.6")
    implementation("org.apache.httpcomponents:httpclient:4.5.10")
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {}
gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

tasks {
    register<Test>("functionalTest") {
        testClassesDirs = functionalTestSourceSet.output.classesDirs
        classpath = functionalTestSourceSet.runtimeClasspath
    }

    named("check") {
        dependsOn("functionalTest")
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.Experimental"
        }
    }
}

gradlePlugin {
    plugins {
        create("environment") {
            id = "com.cognifide.gradle.environment"
            implementationClass = "com.cognifide.gradle.environment.EnvironmentPlugin"
        }
    }
}
