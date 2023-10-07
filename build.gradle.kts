import org.ajoberstar.grgit.operation.CloneOp
import org.ajoberstar.grgit.operation.CommitOp
import org.ajoberstar.grgit.operation.OpenOp
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import java.io.FileOutputStream

plugins {
    id("java")
    id("org.ajoberstar.grgit") version "1.7.2"
}

group = "net.mine_diver"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

abstract class TaskWithPrompts : DefaultTask() {
    // using internal api https://github.com/gradle/gradle/issues/1251#issuecomment-512710561
    @Internal val userInput: UserInputHandler = services.get(UserInputHandler::class.java)
    @Internal val outputEventBroadcaster: OutputEventListener = services.get(OutputEventListener::class.java)
    @Internal val clock: Clock = Time.clock()

    internal fun sendPrompt(prompt: String) {
        outputEventBroadcaster.onOutput(PromptOutputEvent(clock.currentTime, prompt))
    }
}

// General tasks

val serverJar = project.buildDir.resolve("tmp/mcdevMappingsExtractor/b1.7.3.jar")

task("downloadServer") {
    group = "bukric"
    description = "Downloads the server"

    doLast {
        if (serverJar.exists()) return@doLast
        serverJar.parentFile.mkdirs()
        serverJar.createNewFile()
        uri("https://files.betacraft.uk/server-archive/beta/b1.7.3.jar").toURL().openStream().use { it.copyTo(FileOutputStream(serverJar)) }
    }
}

// mc-dev tasks

val mcdev = projectDir.resolve("mc-dev")
val mcdevSubmodule = projectDir.resolve(".git/modules/mc-dev")
val mcdevPatches = projectDir.resolve("mc-dev-patches")
val mcdevPatched = projectDir.resolve("mc-dev-patched")
val mcdevClasses = project.buildDir.resolve("classes/mc-dev")

tasks.register<Delete>("cleanMcdev") {
    group = "mc-dev"
    description = "Removes mc-dev-patched"

    delete(mcdevPatched)
}

task("setupMcdev") {
    group = "mc-dev"
    description = "Sets up mc-dev"

    dependsOn("cleanMcdev")

    doLast {
        with(CloneOp()) {
            uri = mcdevSubmodule.toURI().toString()
            dir = mcdevPatched
            call()
        }
    }

    finalizedBy("applyMcdevPatches")
}

task("applyMcdevPatches") {
    group = "mc-dev"
    description = "Applies patches"

    doLast {
        fileTree(mcdevPatches) { include("*.patch") }.forEach {
            exec {
                workingDir(mcdevPatched)
                executable("git")
                args("am", "--3way", "--ignore-whitespace", it.toRelativeString(mcdevPatched))
            }
        }
    }
}

tasks.register<TaskWithPrompts>("commitMcdev") {
    group = "mc-dev"
    description = "Commits changes to patched repo"

    doLast {
        with(CommitOp(
            with(OpenOp()) {
                dir = mcdevPatched
                call()
            }.repository
        )) {
            all = true
            message = userInput.askQuestion("Commit message", "No message")
            call()
        }
    }

    finalizedBy("rebuildMcdevPatches")
}

task("rebuildMcdevPatches") {
    group = "mc-dev"
    description = "Rebuilds patches"

    doLast {
        exec {
            workingDir(mcdevPatched)
            executable("git")
            args("format-patch", "--no-stat", "-N", "-o", "../mc-dev-patches", "origin/master")
        }
    }
}

tasks.register<JavaCompile>("compileMcdev") {
    group = "mc-dev"
    description = "Compiles mc-dev"

    source = fileTree(mcdevPatched) { include("**/*.java") }
    classpath = files(mcdevClasses)
    destinationDirectory.set(mcdevClasses)

    // JavaCompile doesn't support Java 5 anymore
//    val javaVersion = JavaVersion.forClassVersion(49).toString()
    val javaVersion = JavaVersion.VERSION_1_7.toString()
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.register<Jar>("jarMcdev") {
    group = "mc-dev"
    description = "Bundles mc-dev into an executable jar"

    dependsOn("compileMcdev")

    from(fileTree(mcdevPatched) { exclude("**/*.java") }, mcdevClasses)
    archiveFileName.set("mc-dev.jar")

    manifest.from(mcdevPatched.resolve("META-INF/MANIFEST.MF"))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}