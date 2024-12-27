package test.lwjgl.texts

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

internal class Renders {
    private val fontHeight = 48f
    private val chars = 32..126

    private class STBFontInfo(
        val container: ByteBuffer,
        val delegate: STBTTFontinfo,
    )

    private class FontTexture(
        val id: Int,
        val charBuffer: STBTTPackedchar.Buffer,
        val width: Int,
        val height: Int,
        val ascent: Float,
    )

    private val fontInfo: STBFontInfo
    private var fontTexture: FontTexture? = null

    private fun STBFontInfo.toFontTexture(fontHeight: Float): FontTexture {
        val oversample = 2
        val width: Int = fontHeight.toInt() * oversample * 32 // todo 32?
        val height: Int = fontHeight.toInt() * oversample
        val limit = chars.last - chars.first + 1
        val charBuffer = STBTTPackedchar.malloc(limit)
        val pixels = BufferUtils.createByteBuffer(width * height)
        STBTTPackContext.malloc().use { context ->
            val padding = 1
            val strideInBytes = 0
            STBTruetype.stbtt_PackBegin(
                context,
                pixels,
                width,
                height,
                strideInBytes,
                padding,
                MemoryUtil.NULL,
            )
            STBTruetype.stbtt_PackSetOversampling(context, oversample, oversample)
            STBTruetype.stbtt_PackFontRange(
                context,
                container,
                0,
                fontHeight,
                chars.first,
                charBuffer,
            )
            charBuffer.clear()
            STBTruetype.stbtt_PackEnd(context)
        }
        val id = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_ALPHA,
            width,
            height,
            0,
            GL11.GL_ALPHA,
            GL11.GL_UNSIGNED_BYTE,
            pixels,
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        val ascentBuffer = BufferUtils.createIntBuffer(1)
        STBTruetype.stbtt_GetFontVMetrics(delegate, ascentBuffer, null, null)
        val scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo.delegate, fontHeight)
        return FontTexture(
            id = id,
            charBuffer = charBuffer,
            width = width,
            height = height,
            ascent = ascentBuffer[0] * scale,
        )
    }

    init {
//        val fontName = "JetBrainsMono.ttf"
        val fontName = "OpenSans.ttf"
        val fontBytes = Thread.currentThread().contextClassLoader.getResourceAsStream(fontName)!!.use { it.readBytes() }
        fontInfo = STBFontInfo(
            container = BufferUtils.createByteBuffer(fontBytes.size)
                .put(fontBytes)
                .flip(),
            delegate = STBTTFontinfo.create(),
        )
        STBTruetype.stbtt_InitFont(fontInfo.delegate, fontInfo.container)
    }

    private fun STBTTAlignedQuad.draw() {
        GL11.glTexCoord2f(s0(), t0())
        GL11.glVertex2f(x0(), y0())
        GL11.glTexCoord2f(s1(), t0())
        GL11.glVertex2f(x1(), y0())
        GL11.glTexCoord2f(s1(), t1())
        GL11.glVertex2f(x1(), y1())
        GL11.glTexCoord2f(s0(), t1())
        GL11.glVertex2f(x0(), y1())
    }

    private val xBuffer = BufferUtils.createFloatBuffer(1)
    private val yBuffer = BufferUtils.createFloatBuffer(1)

    private fun drawText(
        xTopLeft: Float,
        yTopLeft: Float,
        text: CharSequence,
    ) {
        val fontTexture = checkNotNull(fontTexture)
        xBuffer.put(0, xTopLeft)
        yBuffer.put(0, yTopLeft + fontTexture.ascent)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTexture.id)
        GL11.glColor4ub(0, -1, 0, -1)
        STBTTAlignedQuad.malloc().use { quad ->
            GL11.glBegin(GL11.GL_QUADS)
            for (char in text) {
                val index = chars.indexOf(char.code)
                if (index < 0) continue
                STBTruetype.stbtt_GetPackedQuad(
                    fontTexture.charBuffer,
                    fontTexture.width,
                    fontTexture.height,
                    index,
                    xBuffer,
                    yBuffer,
                    quad,
                    false,
                )
                quad.draw()
            }
            GL11.glEnd()
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    private fun toCharSequence(chars: Iterable<Int>): CharSequence {
        return String(chars.map { it.toChar() }.toCharArray())
    }

    fun onRenderTexts() {
        if (fontTexture == null) {
            fontTexture = fontInfo.toFontTexture(fontHeight = fontHeight)
        }
        drawText(
            xTopLeft = 48f,
            yTopLeft = 48f * 1,
            text = "the quick brown fox jumps over the lazy dog",
        )
        drawText(
            xTopLeft = 48f,
            yTopLeft = 48f * 3,
            text = toCharSequence((32 * 1) until (32 * 2)),
        )
        drawText(
            xTopLeft = 48f,
            yTopLeft = 48f * 5,
            text = toCharSequence((32 * 2) until (32 * 3)),
        )
        drawText(
            xTopLeft = 48f,
            yTopLeft = 48f * 7,
            text = toCharSequence((32 * 3) until (32 * 4)),
        )
        drawText(
            xTopLeft = 48f,
            yTopLeft = 48f * 9,
            text = "GL_UNPACK_ALIGNMENT: ${GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)}",
        )
    }
}
