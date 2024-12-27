package test.lwjgl.texts

import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.engine.EngineInputCallback
import sp.kx.lwjgl.engine.EngineLogics
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.input.KeyboardButton

internal class TextsEngineLogics(
    private val engine: Engine,
) : EngineLogics {
    private lateinit var shouldEngineStopUnit: Unit
    override val inputCallback = object : EngineInputCallback {
        override fun onKeyboardButton(button: KeyboardButton, isPressed: Boolean) {
            if (isPressed) return
            when (button) {
                KeyboardButton.Escape -> {
                    shouldEngineStopUnit = Unit
                }
                else -> Unit
            }
        }
    }
//    private val renders = Renders()
//    private val renders = FTRenders(engine = engine)
    private val renders = TrueTypeRenders(engine = engine)

    override fun onRender(canvas: Canvas) {
        renders.onRenderTexts(canvas = canvas)
    }

    override fun shouldEngineStop(): Boolean {
        return ::shouldEngineStopUnit.isInitialized
    }
}
