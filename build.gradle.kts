import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "12"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "12"
}

plugins {
    kotlin("jvm") version "1.3.50"
}

repositories {
    mavenCentral()
    jcenter()
}

buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.3.50"))
    }
}

//application dependencies
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.2")
    implementation("com.google.code.gson:gson:2.8.5")
    compile("org.slf4j:slf4j-simple:1.7.26")
    compile("io.javalin:javalin:3.5.0")
}


