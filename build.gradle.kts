// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // keep this version in sync with /buildSrc/build.gradle.kts
        classpath(libs.android.plugin)
    }
}

plugins {
    id("splunk.spotless-conventions")
    alias(libs.plugins.publishPlugin)
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    if (findProperty("release") != "true") {
        version = "$version-SNAPSHOT"
    }
}

subprojects {
    apply(plugin = "splunk.spotless-conventions")
}

nexusPublishing.repositories {
    sonatype {
        username.set(System.getenv("SONATYPE_USERNAME"))
        password.set(System.getenv("SONATYPE_PASSWORD"))
    }
}
