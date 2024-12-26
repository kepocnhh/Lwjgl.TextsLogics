package test.lwjgl.texts

import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_BitmapGlyph
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Glyph
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
import sp.kx.lwjgl.opengl.GLUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer

internal class FTRenders {
    private var ftFace: FT_Face? = null

    private fun Int.ftChecked() {
        if (this == FreeType.FT_Err_Ok) return
        error("FT error: $this!")
    }

    private class Glyph(
        val char: Char,
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
        val metrics = gs.metrics()
        println("$Tag: glyph[$char]:slot:metrics:width: ${metrics.width()}")
        println("$Tag: glyph[$char]:slot:metrics:height: ${metrics.height()}")
        return stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            FT_Get_Glyph(gs, pointer).ftChecked()
            val delegate = FT_Glyph.create(pointer.get(0))
            val advance = delegate.advance()
            // format 26.6
            val x = advance.x() shr 10
            println("$Tag: glyph[$char]:advance:x: $x")
            println("$Tag: glyph[$char]:advance:y: ${advance.y() shr 10}")
            FT_Glyph_To_Bitmap(pointer, FreeType.FT_RENDER_MODE_NORMAL, null, true)
            val bg = FT_BitmapGlyph.create(pointer.get(0))
            println("$Tag: glyph[$char]:bg:top: ${bg.top()}")
            println("$Tag: glyph[$char]:bg:left: ${bg.left()}")
            println("$Tag: left(${bg.left()}) + width(${metrics.width()}) (${bg.left() + metrics.width()}) <= advance:x($x)")
//            println("$Tag: top(${bg.top()}) <= ascender(${ftFace.size()?.metrics()?.ascender()})")
            val bitmap = bg.bitmap()
            println("$Tag: glyph[$char]:bitmap:width: ${bitmap.width()}")
            println("$Tag: glyph[$char]:bitmap:rows: ${bitmap.rows()}")
            println("$Tag: glyph[$char]:bitmap:pitch: ${bitmap.pitch()}")
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
        val pixel_height = fontHeight
        val pixel_width = pixel_height
        FT_Set_Pixel_Sizes(ftFace, pixel_width, pixel_height)
        val size = ftFace.size()
        if (size == null) {
            println("$Tag: no size!")
            return
        }
        val metrics = size.metrics()
        println("$Tag: size[$fontHeight]:metrics:ascender: ${metrics.ascender()}")
        println("$Tag: size[$fontHeight]:metrics:descender: ${metrics.descender()}")
        println("$Tag: size[$fontHeight]:metrics:height: ${metrics.height()}")
    }

    private fun newFace() {
        val fontName = "OpenSans.ttf"
//        val fontName = "JetBrainsMono.ttf"
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
        setSize(ftFace = ftFace, fontHeight = 24)
        printGlyph(ftFace = ftFace, char = 'a')
//        getGlyph(ftFace = ftFace, char = 'b')
//        getGlyph(ftFace = ftFace, char = '1')
//        getGlyph(ftFace = ftFace, char = '-')
        this.ftFace = ftFace
    }

    private var texture: Int? = null

    fun onRenderTexts() {
        val ftFace = ftFace
        if (ftFace == null) {
            newFace()
            return
        }
        val char = 'a'
        val glyph = getGlyph(ftFace = ftFace, char = char)
        if (glyph == null) {
            println("$Tag: no glyph by char: $char!")
            return
        }
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
            return
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glColor4ub(0, -1, 0, -1)
        //
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 12)
        //
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    companion object {
        private const val Tag = "[FT|Renders]"
    }
}
