package test.lwjgl.texts

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.stb.STBTTAlignedQuad
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
import sp.kx.lwjgl.entity.copy
import sp.kx.lwjgl.entity.font.FontInfo
import sp.kx.lwjgl.entity.input.KeyboardButton
import sp.kx.lwjgl.glfw.GLFWUtil
import sp.kx.lwjgl.opengl.GLUtil
import sp.kx.lwjgl.stb.STBUtil
import sp.kx.lwjgl.stb.isAvailable
import sp.kx.lwjgl.stb.pack
import sp.kx.lwjgl.stb.packFontRange
import sp.kx.lwjgl.stb.toFontVMetrics
import sp.kx.lwjgl.util.toArray
import sp.kx.math.Point
import sp.kx.math.copy
import sp.kx.math.measure.measureOf
import sp.kx.math.plus
import sp.kx.math.pointOf
import sp.kx.math.sizeOf
import sp.kx.math.toVector
import sp.kx.math.vectorOf
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
    private val fontName = "JetBrainsMono.ttf"
//    private val fontName = "OpenSans.ttf"
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
        val width: Int,
        val height: Int,
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

    private fun getScale(info: STBTTFontinfo, fontHeight: Float): Float {
        return STBTruetype.stbtt_ScaleForPixelHeight(info, fontHeight)
    }

    private fun STBFontInfo.toFontTexture(fontHeight: Float): FontTexture {
        val width: Int = (fontHeight * 128.0).toInt()
        val height: Int = (fontHeight * 16.0).toInt()
        val limit = Char.MAX_VALUE.code
        val charBuffer = STBTTPackedchar.malloc(limit)
        val position = Char.MIN_VALUE.code
        val fontVMetrics = delegate.toFontVMetrics(fontHeight = fontHeight)
        val pixels = BufferUtils.createByteBuffer(width * height)
        STBTTPackContext.malloc().use { context ->
            context.pack(
                pixels = pixels,
                width = width,
                height = height,
            ) {
                charBuffer.limit(limit)
                charBuffer.position(position)
                STBTruetype.stbtt_PackSetOversampling(context, 2, 2)
                context.packFontRange(
                    fontByteBuffer = container,
                    fontIndex = 0,
                    fontSize = fontVMetrics.ascent - fontVMetrics.descent,
                    firstUnicodeCharInRange = position,
                    charBufferForRange = charBuffer,
                )
                charBuffer.clear()
            }
        }
        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GLFWUtil.texImage2D(
            textureTarget = GL11.GL_TEXTURE_2D,
            textureInternalFormat = GL11.GL_ALPHA,
            width = width,
            height = height,
            texelDataFormat = GL11.GL_ALPHA,
            texelDataType = GL11.GL_UNSIGNED_BYTE,
            pixels = pixels,
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        return FontTexture(
            id = textureId,
            charBuffer = charBuffer,
            width = width,
            height = height,
        )
    }

    private fun STBFontInfo.toFontTextureOld(fontHeight: Float): FontTexture {
        val id = GL11.glGenTextures()
        val chars = 0..126
        val scale = getScale(info = delegate, fontHeight = fontHeight)
        val ascentBuffer = BufferUtils.createIntBuffer(1)
        val descentBuffer = BufferUtils.createIntBuffer(1)
        STBTruetype.stbtt_GetFontVMetrics(delegate, ascentBuffer, descentBuffer, null)
//        val width = MemoryStack.stackPush().use { stack ->
//            val widthBuffer = stack.mallocInt(1)
//            chars.sumOf { char ->
//                STBTruetype.stbtt_GetCodepointHMetrics(delegate, char, widthBuffer, null)
//                widthBuffer.get(0)
//            }
//        } * scale
        val width: Int = (fontHeight * 128.0).toInt() // todo
        val limit = chars.toList().size
        val charBuffer = STBTTPackedchar.malloc(limit)
//        val height = fontHeight // todo
//        val height = fontHeight * 2 // todo
        val height: Int = (fontHeight * 16.0).toInt() // todo
        val pixels = BufferUtils.createByteBuffer((width * height).toInt())
//        val pixels = BufferUtils.createByteBuffer(1024 * 1024)
        println("width: $width")
        println("fontHeight: $fontHeight")
        println("height: $height")
        println("scale: $scale")
        println("ascent: ${ascentBuffer[0]}")
        println("descent: ${descentBuffer[0]}")
        println("a - d: ${ascentBuffer[0] - descentBuffer[0]}")
        println("a - d * s: ${(ascentBuffer[0] - descentBuffer[0]) * scale}")
        STBTTPackContext.malloc().use { context ->
            context.pack(
                pixels = pixels,
                width = width.toInt(),
                height = height.toInt(),
            ) {
                charBuffer.limit(limit)
                charBuffer.position(chars.first)
                STBTruetype.stbtt_PackSetOversampling(context, 2, 2)
                context.packFontRange(
                    fontByteBuffer = container,
                    fontIndex = 0,
                    fontSize = height.toFloat(),
                    firstUnicodeCharInRange = chars.first,
                    charBufferForRange = charBuffer,
                )
                charBuffer.clear()
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GLFWUtil.texImage2D(
            textureTarget = GL11.GL_TEXTURE_2D,
            textureInternalFormat = GL11.GL_ALPHA,
//            textureInternalFormat = GL11.GL_RED, // todo
            width = width.toInt(),
            height = height.toInt(),
            texelDataFormat = GL11.GL_ALPHA,
            texelDataType = GL11.GL_UNSIGNED_BYTE,
            pixels = pixels,
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        return FontTexture(
            id = id,
            charBuffer = charBuffer,
            width = width,
            height = height,
        )
    }

    private fun getFontTexture(fontName: String, fontHeight: Float): FontTexture {
        val fonts = getFonts(fontName = fontName)
        return fonts.textures.getOrPut(fontHeight) {
            fonts.info.toFontTexture(fontHeight = fontHeight)
        }
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

    private fun getCharsWidth(info: STBFontInfo, chars: CharSequence, fontHeight: Float): Float {
        return MemoryStack.stackPush().use { stack ->
            val widthBuffer = stack.mallocInt(1)
            chars.sumOf { char ->
                STBTruetype.stbtt_GetCodepointHMetrics(info.delegate, char.code, widthBuffer, null)
                widthBuffer.get(0)
            }
        } * getScale(info = info.delegate, fontHeight = fontHeight)
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

    private fun STBTTAlignedQuad.draw() {
        GL11.glTexCoord2f(s0(), t0())
        GLUtil.vertexOf(x0(), y0())
        GL11.glTexCoord2f(s1(), t0())
        GLUtil.vertexOf(x1(), y0())
        GL11.glTexCoord2f(s1(), t1())
        GLUtil.vertexOf(x1(), y1())
        GL11.glTexCoord2f(s0(), t1())
        GLUtil.vertexOf(x0(), y1())
    }

    private fun drawText(
        info: STBFontInfo,
        fontHeight: Float,
        fontTexture: FontTexture,
        text: CharSequence,
        xTopLeft: Float,
        yTopLeft: Float,
    ) {
        val color = Color.Green
        val fontVMetrics = info.delegate.toFontVMetrics(fontHeight = fontHeight)
//        val fontHeight = fontVMetrics.ascent - fontVMetrics.descent
        val xBuffer = BufferUtils.createFloatBuffer(1)
        val yBuffer = BufferUtils.createFloatBuffer(1)
        xBuffer.put(0, xTopLeft.toFloat())
//        yBuffer.put(0, (yTopLeft + fontVMetrics.ascent).toFloat())
        yBuffer.put(0, yTopLeft + fontVMetrics.ascent)
        GLUtil.enabled(GL11.GL_TEXTURE_2D) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTexture.id)
            GLUtil.colorOf(color)
            STBTTAlignedQuad.malloc().use { quad ->
                GLUtil.transaction(GL11.GL_QUADS) {
                    for (char in text) {
                        STBTruetype.stbtt_GetPackedQuad(
                            fontTexture.charBuffer,
                            fontTexture.width,
                            fontTexture.height,
                            char.code,
                            xBuffer,
                            yBuffer,
                            quad,
                            false,
                        )
                        quad.draw()
                    }
                }
            }
        }
    }

    private fun drawTextOld(
        info: STBFontInfo,
        fontHeight: Float,
        fontTexture: FontTexture,
        text: CharSequence,
    ) {
        val xTopLeft = 24f
        val yTopLeft = 24f
        val color = Color.Green
        val scale = STBTruetype.stbtt_ScaleForPixelHeight(info.delegate, fontHeight)
        val ascentBuffer = BufferUtils.createIntBuffer(1)
        val descentBuffer = BufferUtils.createIntBuffer(1)
        STBTruetype.stbtt_GetFontVMetrics(info.delegate, ascentBuffer, descentBuffer, null)
//        val fontHeight = info.metrics.ascent - info.metrics.descent
//        val xBuffer = BufferUtils
//            .createFloatBuffer(1)
//            .put(xTopLeft)
//            .flip()
//        val yBuffer = BufferUtils
//            .createFloatBuffer(1)
//            .put(yTopLeft + ascentBuffer[0] * scale)
//            .flip()
        val xBuffer = BufferUtils.createFloatBuffer(1)
        val yBuffer = BufferUtils.createFloatBuffer(1)
        xBuffer.put(0, xTopLeft.toFloat())
        yBuffer.put(0, (yTopLeft + ascentBuffer[0] * scale).toFloat())
//        yBuffer.put(0, yTopLeft.toFloat())
        GLUtil.enabled(GL11.GL_TEXTURE_2D) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTexture.id)
            GLUtil.colorOf(color)
            STBTTAlignedQuad.malloc().use { quad ->
                GLUtil.transaction(GL11.GL_QUADS) {
                    for (char in text) {
//                        val width = getCharWidth(info = info, char = char) * scale
                        val width = fontHeight * 128.0
                        val height = fontHeight
//                        val height = fontHeight * 16.0
                        STBTruetype.stbtt_GetPackedQuad(
                            fontTexture.charBuffer,
                            width.toInt(),
                            height.toInt(),
                            char.code,
                            xBuffer,
                            yBuffer,
                            quad,
                            false,
                        )
                        quad.draw()
                    }
                }
            }
        }
    }

    private fun drawText(
        fontName: String,
        fontHeight: Float,
        text: CharSequence,
        pointTopLeft: Point,
    ) {
        val fonts = getFonts(fontName)
        val fontTexture = fonts.textures.getOrPut(fontHeight) {
            fonts.info.toFontTexture(fontHeight = fontHeight)
        }
        drawText(
            info = fonts.info,
            fontHeight = fontHeight,
            fontTexture = fontTexture,
            text = text,
            xTopLeft = pointTopLeft.x.toFloat(),
            yTopLeft = pointTopLeft.y.toFloat(),
        )
    }

    private fun drawText(
        canvas: Canvas,
        fontHeight: Double,
        pointTopLeft: Point,
        text: CharSequence,
    ) {
        drawText(
            fontName = fontName,
            fontHeight = measure.transform(2.0).toFloat(),
            text = text,
            pointTopLeft = pointTopLeft + measure,
        )
        val textWidth = getCharsWidth(info = getFonts(fontName).info, chars = text, fontHeight = fontHeight.toFloat())
        canvas.polygons.drawRectangle(
            color = Color.Yellow.copy(alpha = 0.75f),
            pointTopLeft = pointTopLeft,
            size = sizeOf(width = textWidth.toDouble(), height = fontHeight),
            lineWidth = 0.05,
            measure = measure,
        )
        val fontVMetrics = getFonts(fontName).info.delegate.toFontVMetrics(fontHeight = fontHeight.toFloat())
        canvas.vectors.draw(
            color = Color.White.copy(alpha = 0.75f),
//            vector = vectorOf(1.0, 1.0 + fontVMetrics.ascent, 1.0 + textWidth, 1.0 + fontVMetrics.ascent),
            vector = pointTopLeft.copy(
                y = pointTopLeft.y + fontVMetrics.ascent,
            ) + pointTopLeft.copy(
                x = pointTopLeft.x + textWidth,
                y = pointTopLeft.y + fontVMetrics.ascent,
            ),
            lineWidth = 0.05,
            measure = measure,
        )
    }

    private fun toCharSequence(chars: Iterable<Int>): CharSequence {
        return String(chars.map { it.toChar() }.toCharArray())
    }

    override fun onRender(canvas: Canvas) {
        val fontHeight = 2.0
        drawText(
            canvas = canvas,
            fontHeight = fontHeight,
            pointTopLeft = pointOf(1.0, 1.0 + fontHeight * 1),
            text = "the quick brown fox jumps over the lazy dog",
        )
        drawText(
            canvas = canvas,
            fontHeight = fontHeight,
            pointTopLeft = pointOf(1.0, 1.0 + fontHeight * 2),
            text = toCharSequence((32 * 1) until (32 * 2)),
        )
        drawText(
            canvas = canvas,
            fontHeight = fontHeight,
            pointTopLeft = pointOf(1.0, 1.0 + fontHeight * 3),
            text = toCharSequence((32 * 2) until (32 * 3)),
        )
        drawText(
            canvas = canvas,
            fontHeight = fontHeight,
            pointTopLeft = pointOf(1.0, 1.0 + fontHeight * 4),
            text = toCharSequence((32 * 3) until (32 * 4)),
        )
    }

    override fun shouldEngineStop(): Boolean {
        return ::shouldEngineStopUnit.isInitialized
    }
}
