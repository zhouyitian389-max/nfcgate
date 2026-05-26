buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.2")
        classpath(kotlin("gradle-plugin", version = "1.9.24"))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
