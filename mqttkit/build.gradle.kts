import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinjvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "io.github.mehedidevs"
version = "0.1.3"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // `api`: these types appear in the public API surface (Flow, StateFlow, Json),
    // so consumers need them on their compile classpath.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    // `implementation`: HiveMQ is an internal transport detail hidden behind MqttClient.
    implementation(libs.hivemq.mqtt.client)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "mqttkit"

            pom {
                name.set("MqttKit")
                description.set(
                    "Coroutines-first MQTT 5 client kit for Kotlin/JVM and Android, " +
                        "backed by the HiveMQ MQTT client."
                )
                url.set("https://github.com/mehedidevs/mqttkit")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mehedidevs")
                        name.set("Mehedi Hasan")
                    }
                }
                scm {
                    url.set("https://github.com/mehedidevs/mqttkit")
                }
            }
        }
    }
}
