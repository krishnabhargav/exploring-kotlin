plugins {
    val kotlinVersion = "1.3.50"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
}

repositories {
    mavenCentral()
    jcenter()
}

//application dependencies
dependencies {
    //jetbrains & kotlin stuff
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.2")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.slf4j:slf4j-simple:1.7.26")
    implementation("io.javalin:javalin:3.5.0")

    //open api
    implementation("io.swagger.core.v3:swagger-core:2.0.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")

    //prometheus
    implementation("io.micrometer:micrometer-core:latest.release")
    implementation("io.micrometer:micrometer-registry-prometheus:latest.release")
}