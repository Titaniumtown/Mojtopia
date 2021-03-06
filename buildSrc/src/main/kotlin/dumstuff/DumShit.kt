package dumstuff

import cmd
import ensureSuccess
import kotlinx.serialization.toUtf8Bytes
import org.cadixdev.atlas.Atlas
import org.cadixdev.lorenz.asm.LorenzRemapper
import org.cadixdev.lorenz.io.MappingFormats
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import stuff.taskGroupPrivate
import toothpick.DebugJarEntryRemappingTransformer
import java.net.URL
import java.nio.file.Files

fun dumShitTasks(project: Project): List<Task> {
    val origFolder = project.projectDir.resolve("work/decomp")
    val workFolder = project.projectDir.resolve("work/dumShit")
    val patches = workFolder.resolve("patches")
    val vanillaClasses = workFolder.resolve("vanillaClasses")
    val vanillaDecomp = workFolder.resolve("vanillaDecomp")
    val modClasses = workFolder.resolve("modClasses")
    val modDecomp = workFolder.resolve("modDecomp")
    val patched = workFolder.resolve("patched")
    val paperWorkDir = project.projectDir.resolve("work/Paper/work")
    var serverMappingUrl = ""
    var serverUrl = ""

    val minecraftVersion = "21w08a"

    if (minecraftVersion == "20w49a") {
        serverUrl = "https://launcher.mojang.com/v1/objects/2fc0afe1fd31ca872761efbd2a7f635db234b359/server.jar"
        serverMappingUrl = "https://launcher.mojang.com/v1/objects/0b30deba62ef6c2064dfd12f4f46b9d6388d9c8c/server.txt"
    } else if (minecraftVersion == "1.16.5") {
        serverUrl = "https://launcher.mojang.com/v1/objects/1b557e7b033b583cd9f66746b7a9ab1ec1673ced/server.jar"
        serverMappingUrl = "https://launcher.mojang.com/v1/objects/41285beda6d251d190f2bf33beadd4fee187df7a/server.txt"
    } else if (minecraftVersion == "21w06a") {
        serverUrl = "https://launcher.mojang.com/v1/objects/6290ba4b475fca4a74de990c7fd8eccffd9654dd/server.jar"
        serverMappingUrl = "https://launcher.mojang.com/v1/objects/c4e373406d2166580c33b075c2d05d9d2fb18d43/server.txt"
    } else if (minecraftVersion == "21w07a") {
        serverUrl = "https://launcher.mojang.com/v1/objects/99c3a9744719d0d401af63bb684cf1eb5231a75c/server.jar"
        serverMappingUrl = "https://launcher.mojang.com/v1/objects/66ebacdeccfdf8f6439f7a90234fc76e8ef5c5a6/server.txt"
    } else if (minecraftVersion == "21w08a") {
        serverUrl = "https://launcher.mojang.com/v1/objects/d5e31633d884e190e046b8645f802541bec2a5e9/server.jar"
        serverMappingUrl = "https://launcher.mojang.com/v1/objects/64b781c30f7fa920c721838f53510861ca3f8d4a/server.txt"
    } else {
        System.out.println("ERROR: minecraftVersion is invalid")
        System.exit(-1)
    }

    val decompileMod: Task by project.tasks.creating {
        group = taskGroupPrivate
        onlyIf {
            !modDecomp.resolve("net/minecraft/server/MinecraftServer.java").exists()
        }
        doLast {
            if (modDecomp.exists()) {
                modDecomp.deleteRecursively()
            }
            modDecomp.mkdirs()
            logger.lifecycle("Decompiling classes...")
            try {
                ensureSuccess(cmd("java", "-jar", "$paperWorkDir/BuildData/bin/fernflower.jar", "-dgs=1", "-hdc=0", "-asc=1", "-udv=0", "-rsy=1", "-aoa=1", modClasses.absolutePath, modDecomp.absolutePath, directory = project.rootDir, printToStdout = true))
            } catch (e: IllegalStateException) {
                modDecomp.deleteRecursively()
                throw GradleException("Failed to decompile classes.", e)
            }
        }
    }

    val diffServer: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(decompileMod)
        onlyIf {
            !patches.exists()
        }
        doLast {
            if (patches.exists()) patches.deleteRecursively()
            patches.mkdirs()

            modDecomp.resolve("net/minecraft").walk(FileWalkDirection.TOP_DOWN).filter { file -> file.isFile }.forEach { mod ->
                val relative = mod.relativeTo(modDecomp).toString()
                val orig = origFolder.resolve(relative)
                val patch = patches.resolve("$relative.patch")
                patch.parentFile.mkdirs()

                val (_, output) = cmd("diff", "-Nu", "--label", "a/$relative", orig.absolutePath, "--label", "b/$relative", mod.absolutePath, directory = patches)
                Files.write(patch.toPath(), output?.toUtf8Bytes()!!)
            }
        }
    }

    val downloadVanillaSnapshot: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(diffServer)
        onlyIf {
            !workFolder.resolve("$minecraftVersion.jar").exists()
        }
        doLast {
            workFolder.resolve("$minecraftVersion.jar").writeBytes(URL(serverUrl).readBytes())
        }
    }

    val remapVanillaSnapshot: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(downloadVanillaSnapshot)
        onlyIf {
            !workFolder.resolve("$minecraftVersion-mapped.jar").exists()
        }
        doLast {
            val mojangFile = workFolder.resolve("server.txt")
            if (!mojangFile.exists()) {
                mojangFile.writeText(URL(serverMappingUrl).readText())
            }
            val mojangMappings = MappingFormats.byId("proguard").read(mojangFile.toPath()).reverse()

            val atlas = Atlas()
            atlas.install { ctx ->
                DebugJarEntryRemappingTransformer(LorenzRemapper(mojangMappings, ctx.inheritanceProvider()))
            }
            atlas.run(workFolder.resolve("$minecraftVersion.jar").toPath(), workFolder.resolve("$minecraftVersion-mapped.jar").toPath())
            atlas.close()

            ensureSuccess(cmd("mvn", "install:install-file", "-q", "-Dfile=${workFolder.resolve("$minecraftVersion-mapped.jar").absolutePath}", "-Dpackaging=jar", "-DgroupId=me.minidigger", "-DartifactId=minecraft-server", "-Dversion=\"$minecraftVersion-SNAPSHOT\"", directory = project.projectDir))
        }
    }

    val extractVanillaSnapshot: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(remapVanillaSnapshot)
        onlyIf {
            !vanillaClasses.exists()
        }
        doLast {
            vanillaClasses.mkdirs()
            ensureSuccess(cmd("jar", "xf", "$workFolder/$minecraftVersion-mapped.jar", "net/minecraft", directory = vanillaClasses, printToStdout = true))

        }
    }

    val decompileVanillaSnapshot: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(extractVanillaSnapshot)
        onlyIf {
            !vanillaDecomp.resolve("net/minecraft/server/MinecraftServer.java").exists()
        }
        doLast {
            if (vanillaDecomp.exists()) {
                vanillaDecomp.deleteRecursively()
            }
            vanillaDecomp.mkdirs()
            logger.lifecycle("Decompiling classes...")
            try {
                ensureSuccess(cmd("java", "-jar", "$paperWorkDir/BuildData/bin/fernflower.jar", "-dgs=1", "-hdc=0", "-asc=1", "-udv=0", "-rsy=1", "-aoa=1", vanillaClasses.absolutePath, vanillaDecomp.absolutePath, directory = project.rootDir, printToStdout = true))
            } catch (e: IllegalStateException) {
                vanillaDecomp.deleteRecursively()
                throw GradleException("Failed to decompile classes.", e)
            }
        }
    }

    val applyVanillaPatches: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(decompileVanillaSnapshot)
        doLast {
            if (patched.exists()) patched.deleteRecursively()
            patched.mkdirs()

            patches.resolve("net/minecraft").walk(FileWalkDirection.TOP_DOWN).filter { file -> file.isFile }.forEach { patch ->
                val relative = patch.relativeTo(patches).toString().replace(".patch", "")
                val decompFile = vanillaDecomp.resolve(relative)
                val patchedFile = patched.resolve(relative)

                if(!decompFile.exists()) return@forEach

                patchedFile.parentFile.mkdirs()

                decompFile.copyTo(patchedFile)
                ensureSuccess(cmd("patch", "-d", patch.relativeTo(patches).parent, patchedFile.absolutePath, patch.absolutePath, directory = patched))
            }
        }
    }

    return listOf(diffServer, decompileVanillaSnapshot)
}
