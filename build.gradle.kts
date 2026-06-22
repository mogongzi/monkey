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
        register("benchmark") {
            mainClass.set("me.ryan.interpreter.benchmark.BenchmarkKt")
            imageName.set("monkey-benchmark")
            classpath(sourceSets["main"].runtimeClasspath)
        }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register("compile") {
    dependsOn("compileKotlin")
}

tasks.register<JavaExec>("generateFixtures") {
    mainClass.set("me.ryan.interpreter.compiler.FixtureGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
    outputs.dir("src/test/fixtures")
}

tasks.register<JavaExec>("benchmark") {
    mainClass.set("me.ryan.interpreter.benchmark.BenchmarkKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<Copy>("nativeBin") {
    val nativeCompile =
        tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeCompile")

    val nativeBenchmarkCompile =
        tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeBenchmarkCompile")

    dependsOn(nativeCompile, nativeBenchmarkCompile)

    from(nativeCompile.flatMap { it.outputFile })
    from(nativeBenchmarkCompile.flatMap { it.outputFile })

    into(layout.buildDirectory.dir("native/bin"))
}