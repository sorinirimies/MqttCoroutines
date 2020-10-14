plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}
