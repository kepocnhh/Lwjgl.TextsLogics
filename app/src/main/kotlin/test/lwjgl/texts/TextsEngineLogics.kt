package test.lwjgl.texts

import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.engine.EngineInputCallback
import sp.kx.lwjgl.engine.EngineLogics
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.Color
import sp.kx.lwjgl.entity.font.FontInfo
import sp.kx.lwjgl.entity.input.KeyboardButton
import sp.kx.lwjgl.util.toArray
import sp.kx.math.measure.measureOf
import sp.kx.math.pointOf
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

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
    private val measure = measureOf(24.0)
    private val fontInfo = object : FontInfo {
        override val height = measure.transform(1.0).toFloat()
        override val id = "JetBrainsMono.ttf"

        override fun getInputStream(): InputStream {
            return Thread.currentThread().contextClassLoader.getResourceAsStream(id)!!
        }
    }
    private val fontSTBInfo: STBTTFontinfo
    private val fontByteBuffer: ByteBuffer

    init {
        val fontName = "JetBrainsMono.ttf"
        val fontBytes = Thread.currentThread().contextClassLoader.getResourceAsStream(fontName)!!.use { it.readBytes() }
        fontByteBuffer = ByteBuffer.allocateDirect(fontBytes.size).order(ByteOrder.nativeOrder()).put(fontBytes).flip()
        fontSTBInfo = STBTTFontinfo.create()
        STBTruetype.stbtt_InitFont(fontSTBInfo, fontByteBuffer)
    }

    private fun drawFontNameStrings(canvas: Canvas) {
        // https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html
        // The name identifier codes
        (0..25).forEach { index ->
            val nameID = index
            val bytes = STBTruetype.stbtt_GetFontNameString(
                fontSTBInfo,
                STBTruetype.STBTT_PLATFORM_ID_MICROSOFT,
                STBTruetype.STBTT_UNICODE_EID_UNICODE_1_1,
                STBTruetype.STBTT_MS_LANG_ENGLISH,
                nameID,
            )?.toArray()
            if (bytes != null) {
                canvas.texts.draw(
                    color = Color.White,
                    info = fontInfo,
                    pointTopLeft = pointOf(1, 1 + index),
                    text = "$index] ${String(bytes, Charsets.UTF_16)}",
                    measure = measure,
                )
            }
        }
    }

    private fun drawFontVMetrics(canvas: Canvas) {
        val ascentBuffer = BufferUtils.createIntBuffer(1)
        val descentBuffer = BufferUtils.createIntBuffer(1)
        val lineGapBuffer = BufferUtils.createIntBuffer(1)
        STBTruetype.stbtt_GetFontVMetrics(fontSTBInfo, ascentBuffer, descentBuffer, lineGapBuffer)
        val ascent = ascentBuffer[0]
        val descent = descentBuffer[0]
        canvas.texts.draw(
            color = Color.White,
            info = fontInfo,
            pointTopLeft = pointOf(1, 1),
            text = "ascent: $ascent",
            measure = measure,
        )
        canvas.texts.draw(
            color = Color.White,
            info = fontInfo,
            pointTopLeft = pointOf(1, 2),
            text = "descent: $descent",
            measure = measure,
        )
        canvas.texts.draw(
            color = Color.White,
            info = fontInfo,
            pointTopLeft = pointOf(1, 3),
            text = "lineGap: ${lineGapBuffer[0]}",
            measure = measure,
        )
    }

    override fun onRender(canvas: Canvas) {
//        drawFontNameStrings(canvas = canvas)
        drawFontVMetrics(canvas = canvas)
//        canvas.texts.draw(
//            color = Color.White,
//            info = fontInfo,
//            pointTopLeft = pointOf(1, 1),
//            text = "foo bar baz",
//            measure = measure,
//        )
    }

    override fun shouldEngineStop(): Boolean {
        return ::shouldEngineStopUnit.isInitialized
    }
}
