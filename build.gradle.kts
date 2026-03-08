plugins {
    kotlin("jvm") version "2.3.0"
    id("application")
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "me.ryan.interpreter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jline:jline-reader:4.0.0")
    implementation("org.jline:jline-terminal-ffm:4.0.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("me.ryan.interpreter.repl.ReplKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("me.ryan.interpreter.repl.ReplKt")
            imageName.set("monkey")
        }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register("compile") {
    dependsOn("compileKotlin")
}
