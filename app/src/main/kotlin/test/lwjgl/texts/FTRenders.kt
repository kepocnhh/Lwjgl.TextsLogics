package test.lwjgl.texts

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_BitmapGlyph
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Glyph
import org.lwjgl.util.freetype.FT_Glyph_Metrics
import org.lwjgl.util.freetype.FT_Size
import org.lwjgl.util.freetype.FT_Size_Request
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.freetype.FreeType.FT_Get_Char_Index
import org.lwjgl.util.freetype.FreeType.FT_Get_First_Char
import org.lwjgl.util.freetype.FreeType.FT_Get_Glyph
import org.lwjgl.util.freetype.FreeType.FT_Glyph_To_Bitmap
import org.lwjgl.util.freetype.FreeType.FT_Init_FreeType
import org.lwjgl.util.freetype.FreeType.FT_Library_Version
import org.lwjgl.util.freetype.FreeType.FT_Load_Char
import org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face
import org.lwjgl.util.freetype.FreeType.FT_Select_Size
import org.lwjgl.util.freetype.FreeType.FT_Set_Char_Size
import org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.Color
import sp.kx.lwjgl.entity.copy
import sp.kx.lwjgl.opengl.GLUtil
import sp.kx.math.copy
import sp.kx.math.moved
import sp.kx.math.plus
import sp.kx.math.pointOf
import sp.kx.math.sizeOf
import sp.kx.math.vectorOf
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class FTRenders {
    private var ftFace: FT_Face? = null
    private var ftSize: FT_Size? = null

    private fun Int.ftChecked() {
        if (this == FreeType.FT_Err_Ok) return
        error("FT error: $this!")
    }

    private class Glyph(
        val char: Char,
        val metrics: FT_Glyph_Metrics,
        val delegate: FT_Glyph,
        val bg: FT_BitmapGlyph,
        val bitmap: FT_Bitmap,
    )

    private fun getGlyph(ftFace: FT_Face, char: Char): Glyph? {
        FT_Load_Char(ftFace, char.code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
        val gs = ftFace.glyph() ?: return null
        return stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            FT_Get_Glyph(gs, pointer).ftChecked()
            val delegate = FT_Glyph.create(pointer.get(0))
            FT_Glyph_To_Bitmap(pointer, FreeType.FT_RENDER_MODE_NORMAL, null, true)
            val bg = FT_BitmapGlyph.create(pointer.get(0))
            val bitmap = bg.bitmap()
            Glyph(
                char = char,
                metrics = gs.metrics(),
                delegate = delegate,
                bg = bg,
                bitmap = bitmap,
            )
        }
    }

    private fun printGlyph(ftFace: FT_Face, char: Char) {
        // https://blog2k.ru/wp-content/uploads/2016/02/freetype_horz.png
        //  advance.x
        // <-------------->
        //  left  width
        // <----><------>
        // left + width <= advance.x
        println("$Tag: char($char) index: ${FT_Get_Char_Index(ftFace, char.code.toLong())}")
        FT_Load_Char(ftFace, char.code.toLong(), FreeType.FT_LOAD_RENDER).ftChecked()
        val gs = ftFace.glyph()
        if (gs == null) {
            println("$Tag: no glyph slot!")
            return
        }
        println("$Tag: glyph[$char]:slot:index: ${gs.glyph_index()}")
        // http://refspecs.linux-foundation.org/freetype/freetype-doc-2.1.10/docs/reference/ft2-base_interface.html#FT_Glyph_Metrics
        // A structure used to model the metrics of a single glyph.
        // The values are expressed in 26.6 fractional pixel format;
        // if the flag FT_LOAD_NO_SCALE is used, values are returned in font units instead.
        val metrics = gs.metrics()
        val width = metrics.width() shr 6
        val height = metrics.height() shr 6
        println("$Tag: glyph[$char]:slot:metrics:width: $width")
        println("$Tag: glyph[$char]:slot:metrics:height: $height")
        return stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            FT_Get_Glyph(gs, pointer).ftChecked()
            val delegate = FT_Glyph.create(pointer.get(0))
            val advance = delegate.advance()
            // format 16.16
            val x = advance.x() shr 16
            println("$Tag: glyph[$char]:advance:x: $x")
            println("$Tag: glyph[$char]:advance:y: ${advance.y() shr 16}")
            FT_Glyph_To_Bitmap(pointer, FreeType.FT_RENDER_MODE_NORMAL, null, true)
            val bg = FT_BitmapGlyph.create(pointer.get(0))
            println("$Tag: glyph[$char]:bg:top: ${bg.top()}")
            println("$Tag: glyph[$char]:bg:left: ${bg.left()}")
            val bitmap = bg.bitmap()
            println("$Tag: glyph[$char]:bitmap:width: ${bitmap.width()}")
            println("$Tag: glyph[$char]:bitmap:rows: ${bitmap.rows()}")
            println("$Tag: glyph[$char]:bitmap:pitch: ${bitmap.pitch()}")
            println("$Tag: left(${bg.left()}) + width(${bitmap.width()}) (${bg.left() + bitmap.width()}) <= advance:x($x)")
        }
    }

    private fun setSize(ftFace: FT_Face, fontHeight: Int) {
        // https://lists.nongnu.org/archive/html/freetype/2002-09/msg00076.html
        // It is a way of representing a non-integer number in a 32-bit word.
        // The first 26 bits are used to represent the integer portion of the number so the integer can vary from 0 to 67,108,864.
        // The remaining 6 are used to represent the fractional portion.
        // 32 = 26 + 6
        // 2^26 = 67108864
        // 2^6  = 64
        val char_height = fontHeight * 64L
        val char_width = char_height
//        val vert_resolution = 1
        val vert_resolution = 2
//        val vert_resolution = 72
        val horz_resolution = vert_resolution
//        FT_Set_Char_Size(ftFace, char_width, char_height, horz_resolution, vert_resolution)
        println("$Tag: font height: $fontHeight")
        val pixel_height = fontHeight
        val pixel_width = 0
        FT_Set_Pixel_Sizes(ftFace, pixel_width, pixel_height)
        val size = ftFace.size()
        if (size == null) {
            println("$Tag: no size!")
            return
        }
        // http://refspecs.linux-foundation.org/freetype/freetype-doc-2.1.10/docs/reference/ft2-base_interface.html
        val metrics = size.metrics()
        // format 26.6
        println("$Tag: size[$fontHeight]:metrics:ascender: ${metrics.ascender() shr 6}")
        println("$Tag: size[$fontHeight]:metrics:descender: ${metrics.descender() shr 6}")
        println("$Tag: size[$fontHeight]:metrics:height: ${metrics.height() shr 6}")
    }

    private fun newFace(fontHeight: Int) {
//        val fontName = "OpenSans.ttf"
        val fontName = "JetBrainsMono.ttf"
        val fontBytes = Thread.currentThread().contextClassLoader.getResourceAsStream(fontName)!!.use { it.readBytes() }
        val buffer = ByteBuffer
            .allocateDirect(fontBytes.size)
            .put(fontBytes)
            .flip()
        val faceIndex: Long = 0
        val ftFace = FTUtil.newFace(
            buffer = buffer,
            faceIndex = faceIndex,
        )
//        val ftFace = stackPush().use { stack ->
//            val address = stack.mallocPointer(1)
//            FT_Init_FreeType(address)
//            val major = stack.mallocInt(1)
//            val minor = stack.mallocInt(1)
//            val patch = stack.mallocInt(1)
//            FT_Library_Version(address.get(0), major, minor, patch)
//            val version = String.format("%d.%d.%d", major.get(0), minor.get(0), patch.get(0))
//            println("$Tag: version: $version")
//            val pointer = stack.mallocPointer(1)
//            FT_New_Memory_Face(address.get(0), buffer, faceIndex, pointer)
//            FT_Face.create(pointer.get(0))
//        }
        println("$Tag: num_glyphs: ${ftFace.num_glyphs()}")
        println("$Tag: family_name: ${ftFace.family_nameString()}")
        println("$Tag: style_name: ${ftFace.style_nameString()}")
        setSize(ftFace = ftFace, fontHeight = fontHeight)
//        printGlyph(ftFace = ftFace, char = 'a')
//        getGlyph(ftFace = ftFace, char = 'b')
//        printGlyph(ftFace = ftFace, char = 'f')
        printGlyph(ftFace = ftFace, char = 'p')
//        printGlyph(ftFace = ftFace, char = 'z')
//        getGlyph(ftFace = ftFace, char = '1')
//        getGlyph(ftFace = ftFace, char = '-')
        this.ftFace = ftFace
    }

    private var texture: Int? = null
    private var vao: Int? = null
    private var vbo: Int? = null

    fun onRenderTexts(canvas: Canvas) {
//        val fontHeight = 24
//        val fontHeight = 128
        val fontHeight = 256
        val ftFace = ftFace
        if (ftFace == null) {
            newFace(fontHeight = fontHeight)
            return
        }
        val ftSize = ftSize
        if (ftSize == null) {
            this.ftSize = ftFace.size() ?: TODO("No size!")
            return
        }
        val char = 'p'
        val glyph = getGlyph(ftFace = ftFace, char = char)
        if (glyph == null) {
            println("$Tag: no glyph by char: $char!")
            return
        }
        val x = 24f
        val y = 24f
        //
        val pointTopLeft = pointOf(x.toDouble(), y.toDouble())
//        canvas.polygons.drawRectangle(
//            color = Color.Yellow,
//            pointTopLeft = pointTopLeft,
//            size = sizeOf(fontHeight.toDouble(), fontHeight.toDouble()),
//            lineWidth = 1.0,
//        )
        val metrics = ftSize.metrics()
        val height = metrics.height() shr 6
        val yBot = y + height
        val ascender = metrics.ascender() shr 6
        val yBase = y + ascender
        val yTop = yBase - glyph.bg.top()
        val advance = glyph.delegate.advance().x() shr 16
        val xEnd = x + advance
        canvas.vectors.draw(
            color = Color.Gray,
            vector = pointOf(x = x.toDouble(), y = y.toDouble()).let { it + it.copy(x = xEnd.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Gray,
            vector = pointOf(x = x.toDouble(), y = y.toDouble()).let { it + it.copy(y = yBot.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Gray,
            vector = pointOf(x = xEnd.toDouble(), y = y.toDouble()).let { it + it.copy(y = yBot.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Gray,
            vector = pointOf(x = x.toDouble(), y = yBot.toDouble()).let { it + it.copy(x = xEnd.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Red.copy(alpha = 0.5f),
            vector = pointOf(x = x.toDouble(), y = yBase.toDouble()).let { it + it.copy(x = xEnd.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Yellow.copy(alpha = 0.5f),
            vector = pointOf(x = x.toDouble(), y = yTop.toDouble()).let { it + it.moved(advance.toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Blue.copy(alpha = 0.75f),
            vector = pointOf(x = x.toDouble(), y = yTop.toDouble()).let { it + it.moved(glyph.bg.left().toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.Green.copy(alpha = 0.75f),
            vector = pointOf(x = x.toDouble() + glyph.bg.left(), y = yTop.toDouble()).let { it + it.moved(glyph.bitmap.pitch().toDouble()) },
            lineWidth = 1.0,
        )
        canvas.vectors.draw(
            color = Color.White.copy(alpha = 0.5f),
            vector = pointOf(x = x.toDouble(), y = yTop.toDouble() + glyph.bitmap.rows()).let { it + it.moved(advance.toDouble()) },
            lineWidth = 1.0,
        )
        //
        val texture = texture
        if (texture == null) {
            this.texture = GL11.glGenTextures()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_ALPHA,
                glyph.bitmap.pitch(),
                glyph.bitmap.rows(),
                0,
                GL11.GL_ALPHA,
                GL11.GL_UNSIGNED_BYTE,
                glyph.bitmap.buffer(glyph.bitmap.pitch() * glyph.bitmap.rows()),
            )
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
//            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
//            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
//            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            return
        }
        //
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glColor4ub(0, -1, 0, -1)
        GL11.glBegin(GL11.GL_QUADS)
//        GL11.glTexCoord2f(0f, 0f)
//        GL11.glVertex2f(x, y)
//        GL11.glTexCoord2f(0f, 1f)
//        GL11.glVertex2f(x, y + glyph.bitmap.rows())
//        GL11.glTexCoord2f(1f, 0f)
//        GL11.glVertex2f(x + glyph.bitmap.pitch(), y)
//        GL11.glTexCoord2f(1f, 1f)
//        GL11.glVertex2f(x + glyph.bitmap.pitch(), y + glyph.bitmap.rows())
        GL11.glEnd()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        //
        /*
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, GL11.GL_FALSE, 8 * Float.SIZE_BYTES, 6 * Float.SIZE_BYTES)
        GL20.glEnableVertexAttribArray(0)
        val vertices = floatArrayOf(
            // Позиции          // Цвета             // Текстурные координаты
             0.5f,  0.5f, 0.0f,   1.0f, 0.0f, 0.0f,   1.0f, 1.0f,   // Верхний правый
             0.5f, -0.5f, 0.0f,   0.0f, 1.0f, 0.0f,   1.0f, 0.0f,   // Нижний правый
            -0.5f, -0.5f, 0.0f,   0.0f, 0.0f, 1.0f,   0.0f, 0.0f,   // Нижний левый
            -0.5f,  0.5f, 0.0f,   1.0f, 1.0f, 0.0f,   0.0f, 1.0f    // Верхний левый
        )
        */
        /*
        val vao = GL30.glGenVertexArrays()
        val vbo = GL15.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glColor4ub(0, -1, 0, -1)
        //
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
        val floats = FloatBuffer.allocate(4 * 6)
        val x = 24
        val y = 24
        val xpos = x.toFloat() + glyph.bg.left()
        val ypos = y.toFloat() - (glyph.bitmap.rows() - glyph.bg.top())
        val w = glyph.bitmap.width()
        val h = glyph.bitmap.rows()
        floats.put(4 * 0, floatArrayOf(xpos, ypos + h, 0f, 0f))
        floats.put(4 * 1, floatArrayOf(xpos, ypos, 0f, 1f))
        floats.put(4 * 2, floatArrayOf(xpos + w, ypos, 1f, 1f))
        floats.put(4 * 3, floatArrayOf(xpos, ypos + h, 0f, 0f))
        floats.put(4 * 4, floatArrayOf(xpos + w, ypos, 1f, 1f))
        floats.put(4 * 5, floatArrayOf(xpos + w, ypos + h, 1f, 0f))
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, floats)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6)
        //
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        */
    }

    companion object {
        private const val Tag = "[FT|Renders]"
    }
}
