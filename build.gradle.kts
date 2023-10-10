import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.format.SrgReader
import net.fabricmc.mappingio.tree.ClassAnalysisDescCompleter
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.md_5.specialsource.SpecialSource
import org.ajoberstar.grgit.operation.CheckoutOp
import org.ajoberstar.grgit.operation.CloneOp
import org.ajoberstar.grgit.operation.CommitOp
import org.ajoberstar.grgit.operation.OpenOp
import org.ajoberstar.grgit.service.ResolveService
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.FileOutputStream
import java.nio.file.Files

buildscript {
    repositories {
        maven("https://maven.fabricmc.net") { name = "FabricMC" }
    }
    dependencies {
        classpath("org.ow2.asm:asm:${properties["asm_version"]}")
        classpath("org.ow2.asm:asm-tree:${properties["asm_version"]}")
        classpath("net.fabricmc:mapping-io:${properties["mapping-io_version"]}")
        classpath("net.md-5:SpecialSource:${properties["specialsource_version"]}")
    }
}

plugins {
    id("java")
    id("org.ajoberstar.grgit") version "1.7.2"
}

group = "net.mine_diver"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "FabricMC" }
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

val mcdevExtractorCache = buildDir.resolve("mc-dev-extractor-cache")
val officialJar = mcdevExtractorCache.resolve("b1.7.3.jar")
val mcdevPatches = projectDir.resolve("mc-dev-patches")
val mcdevPatched = projectDir.resolve("mc-dev-patched")
val mcdevCache = mcdevExtractorCache.resolve("mc-dev")
val mcdevClasses = mcdevCache.resolve("classes/mc-dev")
val mcdevTransformedClasses = mcdevCache.resolve("classes/mc-dev-transformed")
val mcdevJar = mcdevCache.resolve("libs/mc-dev.jar")
val mcdevSrg = mcdevCache.resolve("generated/mc-dev.srg")
val mcdevTiny = mcdevCache.resolve("generated/mc-dev.tiny")

task("downloadOfficialJar") {
    group = "mc-dev-extractor"
    description = "Downloads the official server jar"

    doLast {
        if (officialJar.exists()) return@doLast
        officialJar.parentFile.mkdirs()
        officialJar.createNewFile()
        uri(properties["official_jar"] as String).toURL().openStream().use { it.copyTo(FileOutputStream(officialJar)) }
    }
}

tasks.register<Delete>("cleanMcdev") {
    group = "mc-dev-extractor"
    description = "Removes mc-dev-patched"

    delete(mcdevPatched)
}

task("setupMcdev") {
    group = "mc-dev-extractor"
    description = "Sets up mc-dev"

    doLast {
        with(CloneOp()) {
            uri = properties["mc-dev"] as String
            dir = mcdevPatched
            call()
        }.use { with(CheckoutOp(it.repository)) {
            createBranch = true
            with(ResolveService(it.repository)) {
                branch = toBranchName(properties["mc-dev_patched_branch"])
                startPoint = toRevisionString(properties["mc-dev_revision"])
            }
            call()
        }}
    }

    finalizedBy("applyMcdevPatches")
}

task("applyMcdevPatches") {
    group = "mc-dev-extractor"
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
    group = "mc-dev-extractor"
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
    group = "mc-dev-extractor"
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
    group = "mc-dev-extractor"
    description = "Compiles mc-dev"

    dependsOn("setupMcdev")

    source = fileTree(mcdevPatched) { include("**/*.java") }
    classpath = files(mcdevClasses)
    destinationDirectory.set(mcdevClasses)

    val javaVersion = JavaVersion.forClassVersion(49).toString()
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

task("transformMcdevClasses") {
    group = "mc-dev-extractor"
    description = "Fixes bytecode inconsistencies with vanilla jar, such as empty classes"

    dependsOn("compileMcdev")

    doLast {
        fileTree(mcdevClasses) { include("**/EmptyClass*.class") }.forEach {
            val classNode = ClassNode()
            ClassReader(Files.readAllBytes(it.toPath())).accept(classNode, 0)
            classNode.methods.clear()
            val writer = ClassWriter(0)
            classNode.accept(writer)
            val output = mcdevTransformedClasses.resolve(it.toRelativeString(mcdevClasses)).toPath()
            Files.createDirectories(output.parent)
            Files.createFile(output)
            Files.write(output, writer.toByteArray())
        }
    }
}

tasks.register<Jar>("jarMcdev") {
    group = "mc-dev-extractor"
    description = "Bundles mc-dev into an executable jar"

    dependsOn("transformMcdevClasses")

    from(fileTree(mcdevPatched) { exclude("**/*.java") }, mcdevClasses, mcdevTransformedClasses)
    destinationDirectory.set(mcdevJar.parentFile)
    archiveFileName.set(mcdevJar.name)

    manifest.from(mcdevPatched.resolve("META-INF/MANIFEST.MF"))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

task("generateMcdevMappings") {
    group = "mc-dev-extractor"
    description = "Compares vanilla server jar against patched mc-dev jar to generate mappings"

    dependsOn("downloadOfficialJar", "jarMcdev")

    doLast {
        mcdevSrg.parentFile.mkdirs()
        SpecialSource.main(arrayOf(
            "--first-jar", officialJar.toRelativeString(projectDir),
            "--second-jar", mcdevJar.toRelativeString(projectDir),
            "--srg-out", mcdevSrg.toRelativeString(projectDir)
        ))
        with(MemoryMappingTree()) {
            mcdevSrg.reader().use { srgMapping ->
                SrgReader.read(srgMapping, "server", "mc-dev", this)
            }
            ClassAnalysisDescCompleter.process(officialJar.toPath(), "server", this)
            mcdevTiny.writer().use { output ->
                accept(MappingWriter.create(output, MappingFormat.TINY_2))
            }
        }
    }
}