package test.lwjgl.texts

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.engine.EngineInputCallback
import sp.kx.lwjgl.engine.EngineLogics
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.Color
import sp.kx.lwjgl.entity.font.FontInfo
import sp.kx.lwjgl.entity.input.KeyboardButton
import sp.kx.lwjgl.stb.isAvailable
import sp.kx.lwjgl.stb.pack
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
//    private val fontName = "JetBrainsMono.ttf"
    private val fontName = "OpenSans.ttf"
    private val fontInfo = object : FontInfo {
        override val height = measure.transform(0.75).toFloat()
        override val id = fontName

        override fun getInputStream(): InputStream {
            return Thread.currentThread().contextClassLoader.getResourceAsStream(id)!!
        }
    }

    private class STBFontInfo(
        val container: ByteBuffer,
        val delegate: STBTTFontinfo,
    )

    private class FontTexture(
        val id: Int,
        val charBuffer: STBTTPackedchar.Buffer,
    )

    private class MutableFonts(
        val info: STBFontInfo,
        val textures: MutableMap<Float, FontTexture>,
    )

    private val fonts = mutableMapOf<String, MutableFonts>()
    private fun getFonts(fontName: String): MutableFonts {
        return fonts.getOrPut(fontName) {
            val fontBytes = Thread.currentThread().contextClassLoader.getResourceAsStream(fontName)!!.use { it.readBytes() }
            val info = STBFontInfo(
                container = ByteBuffer.allocateDirect(fontBytes.size).order(ByteOrder.nativeOrder()).put(fontBytes)
                    .flip(),
                delegate = STBTTFontinfo.create(),
            )
            STBTruetype.stbtt_InitFont(info.delegate, info.container)
            MutableFonts(
                info = info,
                textures = mutableMapOf(),
            )
        }
    }
    private fun STBFontInfo.toFontTexture(fontHeight: Float): FontTexture {
        val id = GL11.glGenTextures()
        val chars = Char.MIN_VALUE..Char.MAX_VALUE
        val width = MemoryStack.stackPush().use { stack ->
            val widthBuffer = stack.mallocInt(1)
            chars.sumOf { char ->
                STBTruetype.stbtt_GetCodepointHMetrics(delegate, char.code, widthBuffer, null)
                widthBuffer.get(0)
            }
        }
        val limit = Char.MAX_VALUE.code
        val charBuffer = STBTTPackedchar.malloc(limit)
//        val pixels = BufferUtils.createByteBuffer(1)
//        STBTTPackContext.malloc().use { context ->
//            context.pack(
//                pixels = pixels,
//                width = width,
//                height = height,
//            ) {
//                charBuffer.limit(limit)
//                charBuffer.position(position)
//
//                STBTruetype.stbtt_PackSetOversampling(context, 2, 2)
//
//                context.packFontRange(
//                    fontByteBuffer = fontByteBuffer,
//                    fontIndex = 0,
//                    fontSize = fontVMetrics.ascent - fontVMetrics.descent,
//                    firstUnicodeCharInRange = position,
//                    charBufferForRange = charBuffer,
//                )
//                charBuffer.clear()
//            }
//        }
//        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
//        GLFWUtil.texImage2D(
//            textureTarget = GL11.GL_TEXTURE_2D,
//            textureInternalFormat = GL11.GL_ALPHA,
//            width = width,
//            height = height,
//            texelDataFormat = GL11.GL_ALPHA,
//            texelDataType = GL11.GL_UNSIGNED_BYTE,
//            pixels = pixels,
//        )
//        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
//        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        return FontTexture(
            id = id,
            charBuffer = charBuffer,
        )
    }

    private fun getFontTexture(fontName: String, fontHeight: Float): FontTexture {
        val fonts = getFonts(fontName = fontName)
        return fonts.textures.getOrPut(fontHeight) {
            fonts.info.toFontTexture(fontHeight = fontHeight)
        }
    }

    init {
        val stbFontInfo = getFonts("JetBrainsMono.ttf").info
    }

    private fun drawFontNameStrings(canvas: Canvas, stbFontInfo: STBFontInfo) {
        // https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html
        // The name identifier codes
        (0..25).forEach { index ->
            val nameID = index
            val bytes = STBTruetype.stbtt_GetFontNameString(
                stbFontInfo.delegate,
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

    private fun drawFontVMetrics(canvas: Canvas, stbFontInfo: STBFontInfo) {
        val ascentBuffer = BufferUtils.createIntBuffer(1)
        val descentBuffer = BufferUtils.createIntBuffer(1)
        val lineGapBuffer = BufferUtils.createIntBuffer(1)
        STBTruetype.stbtt_GetFontVMetrics(stbFontInfo.delegate, ascentBuffer, descentBuffer, lineGapBuffer)
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

    private fun getCharWidth(info: STBFontInfo, char: Char): Int {
        return MemoryStack.stackPush().use { stack ->
            val widthBuffer = stack.mallocInt(1)
            STBTruetype.stbtt_GetCodepointHMetrics(info.delegate, char.code, widthBuffer, null)
            widthBuffer.get(0)
        }
    }

    private fun drawFontChars(canvas: Canvas, stbFontInfo: STBFontInfo) {
        val chars = (32..126).map { it.toChar() }
//        val chars = (Char.MIN_VALUE..Char.MAX_VALUE).toList()
//        val chars = (0..512).map { it.toChar() }
//        var index = -1
//        var number = -1
//        while (true) {
//            index++
//            if (index == chars.size) break
//            val char = chars[index]
//            if (!stbFontInfo.delegate.isAvailable(char)) continue
//            number++
//            canvas.texts.draw(
//                color = Color.White,
//                info = fontInfo,
//                pointTopLeft = pointOf(1 + (number / 20 * 4), 1 + number % 20),
//                text = "${char.code}($char)",
//                measure = measure,
//            )
//        }
        for ((index: Int, char) in chars.withIndex()) {
            val width = getCharWidth(info = stbFontInfo, char = char) * STBTruetype.stbtt_ScaleForPixelHeight(stbFontInfo.delegate, fontInfo.height)
            canvas.texts.draw(
                color = Color.White,
                info = fontInfo,
                pointTopLeft = pointOf(1 + (index / 16 * 6), 1 + index % 16),
//                text = "${char.code}($char)[$width]",
                text = String.format("%d(%s)[%.2f]", char.code, char, width),
                measure = measure,
            )
        }
    }

    override fun onRender(canvas: Canvas) {
        val fonts = getFonts(fontName)
//        drawFontNameStrings(canvas = canvas)
//        drawFontVMetrics(canvas = canvas, stbFontInfo = stbFontInfo)
        drawFontChars(canvas = canvas, stbFontInfo = fonts.info)
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
