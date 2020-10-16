buildscript {
    repositories {
        google()
        jcenter()

    }
    dependencies {
        classpath(kotlin("gradle-plugin", Version.kotlinVersion))
        classpath("com.android.tools.build:gradle:4.0.2")
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