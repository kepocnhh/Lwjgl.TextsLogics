package test.lwjgl.texts

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType.FT_Init_FreeType
import org.lwjgl.util.freetype.FreeType.FT_Library_Version
import org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face
import java.nio.ByteBuffer

internal object FTUtil {
    private const val Tag = "[FTUtil]"
    private var pointer: PointerBuffer? = null

    private fun getPointer(stack: MemoryStack): PointerBuffer = synchronized(this) {
        val pointer = pointer
        if (pointer != null) return pointer
        val newPointer = stack.mallocPointer(1)
        FT_Init_FreeType(newPointer)
        val major = stack.mallocInt(1)
        val minor = stack.mallocInt(1)
        val patch = stack.mallocInt(1)
        FT_Library_Version(newPointer.get(0), major, minor, patch)
        val version = String.format("%d.%d.%d", major.get(0), minor.get(0), patch.get(0))
        println("$Tag: version: $version")
        this.pointer = newPointer
        return newPointer
    }

    fun newFace(buffer: ByteBuffer, faceIndex: Long): FT_Face {
        return stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            FT_New_Memory_Face(getPointer(stack = stack).get(0), buffer, faceIndex, pointer)
            FT_Face.create(pointer.get(0))
        }
    }
}
