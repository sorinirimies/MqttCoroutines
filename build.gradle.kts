buildscript {
    repositories {
        google()
        jcenter()

    }
    dependencies {
        classpath(kotlin("gradle-plugin", Version.kotlinVersion))
        classpath("com.android.tools.build:gradle:4.1.0")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${Version.dokka}")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

tasks.register("clean").configure {
    delete("build")
}


/*
tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
}*/
