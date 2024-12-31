package test.lwjgl.texts

import sp.kx.lwjgl.engine.Engine
import sp.kx.math.sizeOf

fun main() {
    Engine.run(
        title = "Texts",
        supplier = ::TextsEngineLogics,
        size = sizeOf(1024, 640),
//        defaultFontName = "JetBrainsMono.ttf",
        defaultFontName = "OpenSans.ttf",
    )
}
