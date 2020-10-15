buildscript {
    repositories {
        google()
        jcenter()

    }
    dependencies {
        classpath(Lib.kotlinLang)
        classpath("com.android.tools.build:gradle:4.0.2")
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
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