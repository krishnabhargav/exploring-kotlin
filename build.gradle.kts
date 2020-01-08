import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.3.50"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("com.google.cloud.tools.jib") version "1.7.0"
}

//jib helps generating docker image
jib.from.image = "openjdk:13-jdk-slim"
jib.container.mainClass = "ApiServerWithPrometheusKt"

repositories {
    mavenCentral()
    jcenter {
        this.url = uri("https://capsule.jfrog.io/capsule/libs-release")
        credentials {
            val au = System.getenv("ARTIFACTORY_CREDENTIALS_USR")
            val ap = System.getenv("ARTIFACTORY_CREDENTIALS_PSW")
            println("Artifactory username: $au")
            println("Artifactory key: $ap")
            username = au
            password = ap
        }
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

//application dependencies
val kotlinExtensionsVersion = "1.3.2"
val gsonVersion = "2.8.5"
val sl4jVersion = "1.7.26"
val javalinVersion = "3.6.0"
val swaggerCoreVersion = "2.0.9"
val jacksonKotlinVersion = "2.10.+"
val redocVersion = "2.0.0-rc.2" //rc.18 doesn't work yet
val atlasVersion = "4.3.0"

dependencies {
    //jetbrains & kotlin stuff
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinExtensionsVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinExtensionsVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.slf4j:slf4j-simple:$sl4jVersion")
    implementation("io.javalin:javalin:$javalinVersion")

    //openapi
    implementation("io.swagger.core.v3:swagger-core:$swaggerCoreVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonKotlinVersion")
    //openapi - enables redoc ui for viewing documentation
    implementation("org.webjars.npm:js-tokens:5.0.0") //workaround for maven range issue
    implementation("org.webjars.npm:redoc:$redocVersion")

    //prometheus
    implementation("io.micrometer:micrometer-core:latest.release")
    implementation("io.micrometer:micrometer-registry-prometheus:latest.release")

    //atlas
    implementation("com.capsule:atlas:$atlasVersion")
}