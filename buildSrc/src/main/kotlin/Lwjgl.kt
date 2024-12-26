import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

object Lwjgl {
    const val group = "org.lwjgl"
    val modules = setOf(
        "lwjgl",
        "lwjgl-glfw",
        "lwjgl-opengl",
        "lwjgl-stb",
        "lwjgl-freetype",
    )

    fun requireNativesName(): String {
        val os = DefaultNativePlatform.getCurrentOperatingSystem()
        when {
            os.isLinux -> return "natives-linux"
            os.isMacOsX -> {
                val architecture = DefaultNativePlatform.getCurrentArchitecture()
                when (architecture.name) {
                    "arm-v8", "aarch64" -> return "natives-macos-arm64"
                }
                error("Operating System ${os.name} with architecture ${architecture.name} is not supported!")
            }
        }
        error("Operating System ${os.name} is not supported!")
    }
}
