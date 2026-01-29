plugins {
    kotlin("jvm") version "2.3.0"
    id("application")
}

group = "me.ryan.interpreter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("me.ryan.interpreter.ReplKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
