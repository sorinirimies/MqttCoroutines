repositories {
    jcenter()
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

plugins {
    `kotlin-dsl`
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}
