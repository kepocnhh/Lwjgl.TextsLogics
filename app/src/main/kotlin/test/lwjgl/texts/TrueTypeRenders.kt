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
import org.lwjgl.util.freetype.FreeType.FT_Set_Char_Size
import org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes
import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.Color
import sp.kx.lwjgl.entity.copy
import sp.kx.math.copy
import sp.kx.math.plus
import sp.kx.math.pointOf
import sp.kx.math.sizeOf
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class TrueTypeRenders(
    private val engine: Engine,
) {
    init {
        // todo
//        thread {
//            val time = System.currentTimeMillis().milliseconds
//            println("start job")
//            while (true) {
//                val now = System.currentTimeMillis().milliseconds
//                if (now - time > 10.minutes) {
//                    println("stop job")
//                    break
//                }
//                val hashCode = File("/tmp/foo-${now.inWholeMilliseconds % 3}").let {
//                    it.delete()
//                    it.writeBytes(Random.nextBytes(256))
//                    val bytes = it.readBytes()
//                    bytes.contentHashCode()
//                }
//                Thread.sleep(if (hashCode % 2 == 0) 500 else 1_000)
//            }
//        }
    }

    private fun Int.ftChecked() {
        if (this == FreeType.FT_Err_Ok) return
        error("FT error: $this!")
    }

    private class Glyph(
        val x: Int,
        val width: Int,
        val height: Int,
        val advance: Int,
        val left: Int,
        val top: Int,
    )

    private class Atlas(
        val id: Int,
        val width: Int,
        val height: Int,
        val ascender: Int,
        val descender: Int,
        val upe: Int,
        val space: Int,
        val glyphs: Map<Int, Glyph>,
    ) {
        fun getScale(fontHeight: Int): Double {
            return fontHeight.toDouble() / (ascender - descender)
        }
    }

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
//        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
//        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        return id
    }

    private fun getAtlas(ftFace: FT_Face, fontHeight: Int): Atlas {
        println("$Tag: font height: $fontHeight")
        val char_height = fontHeight * 64L
        val char_width = char_height
        val vert_resolution = 36
//        val vert_resolution = 72
        val horz_resolution = vert_resolution
        // http://refspecs.linux-foundation.org/freetype/freetype-doc-2.1.10/docs/reference/ft2-base_interface.html#FT_Set_Char_Size
//        FT_Set_Char_Size(ftFace, char_width, char_height, horz_resolution, vert_resolution)
        val pixel_height = fontHeight
        val pixel_width = 0
        // http://refspecs.linux-foundation.org/freetype/freetype-doc-2.1.10/docs/reference/ft2-base_interface.html#FT_Set_Pixel_Sizes
        FT_Set_Pixel_Sizes(ftFace, pixel_width, pixel_height).ftChecked()
        val size = ftFace.size() ?: TODO()
        val metrics = size.metrics()
        val ascender: Int = (metrics.ascender() shr 6).toInt()
        val descender: Int = (metrics.descender() shr 6).toInt()
        val glyphs = mutableMapOf<Int, Glyph>()
        var width = 0
        var height = 0
//        val padding = 0
        val padding = 4
        for (code in 33..126) {
            FT_Load_Char(ftFace, code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
            val glyph = ftFace.glyph()
            if (glyph == null) {
                println("$Tag: no glyph by char($code): \"${code.toChar()}\"")
                continue
            }
            val bitmap = glyph.bitmap()
            val pitch = bitmap.pitch()
            val rows = bitmap.rows()
            if (bitmap.buffer(pitch * rows) == null) {
                println("$Tag: no bitmap buffer by char($code): \"${code.toChar()}\"")
                continue
            }
            height = kotlin.math.max(height, rows)
            val advance: Int = (glyph.advance().x() shr 6).toInt()
            glyphs[code] = Glyph(
                width = pitch,
                height = rows,
                x = width,
                advance = advance,
                left = glyph.bitmap_left(),
                top = glyph.bitmap_top(),
            )
            val message = """
                code:         $code
                advance:      $advance
                bitmap:pitch: $pitch
                bitmap:rows:  $rows
            """.trimIndent()
            if (code == 'a'.code) {
                println("$Tag: char: \"${code.toChar()}\":\n$message")
            }
            width += pitch + padding
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
        FT_Load_Char(ftFace, ' '.code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
        val space = ftFace.glyph()?.advance()?.x()?.shr(6)?.toInt() ?: (fontHeight / 2)
        return Atlas(
            id = id,
            width = width,
            height = height,
            ascender = ascender,
            descender = descender,
            upe = ftFace.units_per_EM().toInt(),
            glyphs = glyphs,
            space = space,
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
                println("$Tag: atlas:u/e: ${atlas.upe}")
                println("$Tag: atlas:scale($fontHeight): ${atlas.getScale(fontHeight = fontHeight)}")
                FT_Done_Face(ftFace).ftChecked()
                FT_Done_FreeType(lib.get(0)).ftChecked()
                atlas
            }
        }
    }

    private fun onRenderGlyph(
        fontHeight: Int,
        atlas: Atlas,
        glyph: Glyph,
        x: Double,
        y: Double,
    ) {
        val ipw = 1.0 / atlas.width
        val iph = 1.0 / atlas.height
        //
        val scale = atlas.getScale(fontHeight = fontHeight)
        val x0 = x + glyph.left * scale
        val y0 = y + (atlas.ascender - glyph.top) * scale
        val x1 = x0 + glyph.width * scale
        val y1 = y0 + glyph.height * scale
        val s0 = glyph.x * ipw
        val t0 = 0.0
        val s1 = (glyph.x + glyph.width) * ipw
        val t1 = glyph.height * iph
        //
        GL11.glTexCoord2d(s0, t0)
        GL11.glVertex2d(x0, y0)
        GL11.glTexCoord2d(s1, t0)
        GL11.glVertex2d(x1, y0)
        GL11.glTexCoord2d(s1, t1)
        GL11.glVertex2d(x1, y1)
        GL11.glTexCoord2d(s0, t1)
        GL11.glVertex2d(x0, y1)
    }

    private fun onRenderChars(
        fontHeight: Int,
        atlas: Atlas,
        chars: CharArray,
        x: Double,
        y: Double,
    ) {
        var i = x
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.id)
        GL11.glColor4ub(0, -1, 0, -1)
        val scale = atlas.getScale(fontHeight = fontHeight)
        for (char in chars) {
            val glyph = atlas.glyphs[char.code]
            if (glyph == null) {
                if (char == ' ') i += atlas.space * scale
                continue
            }
            GL11.glBegin(GL11.GL_QUADS)
            onRenderGlyph(
                fontHeight = fontHeight,
                x = i,
                y = y,
                atlas = atlas,
                glyph = glyph,
            )
            GL11.glEnd()
            i += glyph.advance * scale
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    private fun onRenderAtlas(
        canvas: Canvas,
        fontHeight: Int,
        atlas: Atlas,
        x: Double,
        y: Double,
    ) {
        val scale = atlas.getScale(fontHeight = fontHeight)
        val ps = engine.property.pictureSize
        canvas.vectors.draw(
            color = Color.Gray.copy(alpha = 0.5f),
            vector = pointOf(x = 0.0, y = y).let { it + it.copy(x = ps.width) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Red.copy(alpha = 0.5f),
            vector = pointOf(x = 0.0, y = y + atlas.ascender * scale).let { it + it.copy(x = ps.width) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Gray.copy(alpha = 0.5f),
            vector = pointOf(x = 0.0, y = y + fontHeight).let { it + it.copy(x = ps.width) },
            lineWidth = 1.0,
        )
    }

    private fun onRenderGlyphMetrics(
        canvas: Canvas,
        fontHeight: Int,
        atlas: Atlas,
        glyph: Glyph,
        x: Double,
        y: Double,
    ) {
        val scale = atlas.getScale(fontHeight = fontHeight)
        val yBot = y + fontHeight
        canvas.vectors.draw(
            color = Color.Gray.copy(alpha = 0.5f),
            vector = pointOf(x = x, y = y).let { it + it.copy(y = yBot) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Gray.copy(alpha = 0.5f),
            vector = pointOf(x = x + glyph.advance * scale, y = y).let { it + it.copy(y = yBot) },
            lineWidth = 1.0,
        )
        val yTop = y + (atlas.ascender - glyph.top) * scale
        canvas.polygons.drawRectangle(
            color = Color.Yellow.copy(alpha = 0.5f),
            pointTopLeft = pointOf(x = x + glyph.left * scale, y = yTop),
            size = sizeOf(width = glyph.width * scale, height = glyph.height * scale),
            lineWidth = 1.0,
        )
    }

    private fun onRenderChar(
        canvas: Canvas,
        fontHeight: Int,
        atlas: Atlas,
        char: Char,
        x: Double,
        y: Double,
    ) {
        val glyph = atlas.glyphs[char.code] ?: return
        onRenderGlyphMetrics(
            canvas = canvas,
            fontHeight = fontHeight,
            atlas = atlas,
            glyph = glyph,
            x = x,
            y = y,
        )
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.id)
        GL11.glColor4ub(0, -1, 0, -1)
        GL11.glBegin(GL11.GL_QUADS)
        //
        onRenderGlyph(
            fontHeight = fontHeight,
            atlas = atlas,
            glyph = glyph,
            x = x,
            y = y,
        )
        //
        GL11.glEnd()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    fun onRenderTexts(canvas: Canvas) {
//        val fontName = "JetBrainsMono.ttf"
        val fontName = "OpenSans.ttf"
        val fontHeight = 48
//        val fontHeight = 64
//        val fontHeight = 128
//        val fontHeight = 256
//        val fontHeight = 300
//        val fontHeight = 400
//        val fontHeight = 512
        val atlas = getAtlas(fontName = fontName, fontHeight = fontHeight)
        val x = 24.0
        val y = 24.0
        val ps = engine.property.pictureSize
        canvas.vectors.draw(
            color = Color.Green.copy(alpha = 0.5f),
            vector = pointOf(x = 0.0, y = y).let { it + it.copy(x = ps.width) },
            lineWidth = 1.0
        )
        canvas.vectors.draw(
            color = Color.Green.copy(alpha = 0.5f),
            vector = pointOf(x = 0.0, y = y + fontHeight).let { it + it.copy(x = ps.width) },
            lineWidth = 1.0
        )
        onRenderAtlas(
            canvas = canvas,
            fontHeight = fontHeight,
            atlas = atlas,
            x = x,
            y = y,
        )
//        val chars = charArrayOf('a')
//        val chars = charArrayOf('a', 'f', ' ', 'p', '+')
        val chars = "the quick brown fox jumps over the lazy dog".toCharArray()
        var i = x
        val scale = atlas.getScale(fontHeight = fontHeight)
        for (char in chars) {
            if (char == ' ') {
                i += atlas.space * scale
                continue
            }
            val glyph = atlas.glyphs[char.code] ?: continue
//            onRenderGlyphMetrics(
//                canvas = canvas,
//                fontHeight = fontHeight,
//                atlas = atlas,
//                glyph = glyph,
//                x = i,
//                y = y,
//            )
            i += glyph.advance * scale
        }
        onRenderChars(
            fontHeight = fontHeight,
            atlas = atlas,
            chars = chars,
            x = x,
            y = y,
        )
//        val char = ' '
        val char = 'a'
//        val char = 'p'
//        onRenderChar(
//            canvas = canvas,
//            fontHeight = fontHeight,
//            atlas = atlas,
//            char = char,
//            x = x,
//            y = y,
//        )
    }

    companion object {
        private const val Tag = "[TrueType|Renders]"
    }
}
