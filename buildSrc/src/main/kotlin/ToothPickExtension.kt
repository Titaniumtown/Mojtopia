import org.gradle.api.model.ObjectFactory
import java.io.File

open class ToothPickExtension(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {

    var minecraftVersion: String = "1.16.5"
    var serverMappings: String = "https://launcher.mojang.com/v1/objects/41285beda6d251d190f2bf33beadd4fee187df7a/server.txt"
    lateinit var forkName: String
    lateinit var upstreamName: String
    lateinit var groupId: String

    lateinit var subProjects :Map<String, List<File>>
}
