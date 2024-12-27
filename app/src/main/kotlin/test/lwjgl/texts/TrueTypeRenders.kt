package test.lwjgl.texts

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.freetype.FreeType.FT_Done_Face
import org.lwjgl.util.freetype.FreeType.FT_Done_FreeType
import org.lwjgl.util.freetype.FreeType.FT_Init_FreeType
import org.lwjgl.util.freetype.FreeType.FT_Load_Char
import org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face
import org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes
import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.entity.Canvas
import java.nio.ByteBuffer

internal class TrueTypeRenders(
    private val engine: Engine,
) {
    private fun Int.ftChecked() {
        if (this == FreeType.FT_Err_Ok) return
        error("FT error: $this!")
    }

    private class Glyph(
        val x: Int,
        val width: Int,
        val height: Int,
    )

    private class Atlas(
        val id: Int,
        val width: Int,
        val height: Int,
        val glyphs: Map<Int, Glyph>,
    )

    private val fonts = mutableMapOf<String, Pair<ByteBuffer, MutableMap<Int, Atlas>>>()

    private fun getTexture(
        width: Int,
        height: Int,
    ): Int {
        val id = GL11.glGenTextures()
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        val buffer: ByteBuffer? = null
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_ALPHA,
            width,
            height,
            0,
            GL11.GL_ALPHA,
            GL11.GL_UNSIGNED_BYTE,
            buffer,
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        return id
    }

    private fun getAtlas(ftFace: FT_Face, fontHeight: Int): Atlas {
        val pixel_height = fontHeight
        val pixel_width = 0
        FT_Set_Pixel_Sizes(ftFace, pixel_width, pixel_height)
        val glyphs = mutableMapOf<Int, Glyph>()
        var width = 0
        var height = 0
        for (code in 32..126) {
            FT_Load_Char(ftFace, code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
            val glyph = ftFace.glyph() ?: continue
            val bitmap = glyph.bitmap()
            val pitch = bitmap.pitch()
            val rows = bitmap.rows()
            if (bitmap.buffer(pitch * rows) == null) continue
            height = kotlin.math.max(height, rows)
            glyphs[code] = Glyph(
                width = pitch,
                height = rows,
                x = width,
            )
            width += pitch
        }
        val id = getTexture(
            width = width,
            height = height,
        )
        for ((code, glyph) in glyphs) {
            FT_Load_Char(ftFace, code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
            val bitmap = ftFace.glyph()?.bitmap() ?: TODO()
            val buffer = bitmap.buffer(glyph.width * glyph.height) ?: TODO()
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                glyph.x,
                0,
                glyph.width,
                glyph.height,
                GL11.GL_ALPHA,
                GL11.GL_UNSIGNED_BYTE,
                buffer,
            )
        }
        return Atlas(
            id = id,
            width = width,
            height = height,
            glyphs = glyphs,
        )
    }

    private fun getAtlas(
        fontName: String,
        fontHeight: Int,
    ): Atlas {
        val (buffer, atlases) = fonts.getOrPut(fontName) {
            val fontBytes = Thread.currentThread()
                .contextClassLoader
                .getResourceAsStream(fontName)!!.use { it.readBytes() }
            val buffer = ByteBuffer
                .allocateDirect(fontBytes.size)
                .put(fontBytes)
                .flip()
            buffer to mutableMapOf()
        }
        return atlases.getOrPut(fontHeight) {
            stackPush().use { stack ->
                val lib = stack.mallocPointer(1)
                FT_Init_FreeType(lib).ftChecked()
                val pointer = stack.mallocPointer(1)
                val faceIndex: Long = 0
                FT_New_Memory_Face(lib.get(0), buffer, faceIndex, pointer).ftChecked()
                val ftFace = FT_Face.create(pointer.get(0))
                val atlas = getAtlas(ftFace = ftFace, fontHeight = fontHeight)
                println("$Tag: atlas:id: ${atlas.id}")
                println("$Tag: atlas:width: ${atlas.width}")
                println("$Tag: atlas:height: ${atlas.height}")
                println("$Tag: atlas:glyphs: ${atlas.glyphs.size}")
                FT_Done_Face(ftFace).ftChecked()
                FT_Done_FreeType(lib.get(0)).ftChecked()
                atlas
            }
        }
    }

    private fun onRenderGlyph(
        x: Float,
        y: Float,
        atlas: Atlas,
        glyph: Glyph,
    ) {
        val ipw = 1f / atlas.width
        val iph = 1f / atlas.height
        //
        val x0 = x
        val y0 = y
        val x1 = x + glyph.width
        val y1 = y + glyph.height
        val s0 = glyph.x * ipw
        val t0 = 0f
        val s1 = (glyph.x + glyph.width) * ipw
        val t1 = glyph.height * iph
        //
        GL11.glTexCoord2f(s0, t0)
        GL11.glVertex2f(x0, y0)
        GL11.glTexCoord2f(s1, t0)
        GL11.glVertex2f(x1, y0)
        GL11.glTexCoord2f(s1, t1)
        GL11.glVertex2f(x1, y1)
        GL11.glTexCoord2f(s0, t1)
        GL11.glVertex2f(x0, y1)
    }

    private fun onRenderChar(
        canvas: Canvas,
        atlas: Atlas,
        char: Char,
    ) {
        val glyph = atlas.glyphs[char.code] ?: return
        val x = 24
        val y = 24
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.id)
        GL11.glColor4ub(0, -1, 0, -1)
        GL11.glBegin(GL11.GL_QUADS)
        //
        onRenderGlyph(
            x = x.toFloat(),
            y = y.toFloat(),
            atlas = atlas,
            glyph = glyph,
        )
        //
        GL11.glEnd()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    fun onRenderTexts(canvas: Canvas) {
//        val fontName = "JetBrainsMono.ttf"
        val fontName = "OpenSans.ttf"
        val fontHeight = 256
        val atlas = getAtlas(fontName = fontName, fontHeight = fontHeight)
        val char = 'f'
        onRenderChar(
            canvas = canvas,
            atlas = atlas,
            char = char,
        )
    }

    companion object {
        private const val Tag = "[TrueType|Renders]"
    }
}
