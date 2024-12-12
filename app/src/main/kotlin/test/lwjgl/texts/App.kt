package test.lwjgl.texts

import sp.kx.lwjgl.engine.Engine
import sp.kx.math.sizeOf

fun main() {
    Engine.run(::TextsEngineLogics, size = sizeOf(640, 640), title = "Texts")
}
