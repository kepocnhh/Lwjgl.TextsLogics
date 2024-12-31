package test.lwjgl.texts

import sp.kx.lwjgl.engine.Engine
import sp.kx.lwjgl.engine.EngineInputCallback
import sp.kx.lwjgl.engine.EngineLogics
import sp.kx.lwjgl.entity.Canvas
import sp.kx.lwjgl.entity.Color
import sp.kx.lwjgl.entity.copy
import sp.kx.lwjgl.entity.input.KeyboardButton
import sp.kx.math.Point
import sp.kx.math.measure.MutableDoubleMeasure
import sp.kx.math.offsetOf
import sp.kx.math.pointOf
import sp.kx.math.sizeOf
import sp.kx.math.vectorOf

internal class TextsEngineLogics(
    private val engine: Engine,
) : EngineLogics {
    private lateinit var ses: Unit
    override val inputCallback = object : EngineInputCallback {
        override fun onKeyboardButton(button: KeyboardButton, isPressed: Boolean) {
            if (isPressed) return
            when (button) {
                KeyboardButton.Minus -> {
                    when (measure.magnitude) {
                        256.0 -> measure.magnitude = 200.0
                        200.0 -> measure.magnitude = 128.0
                        128.0 -> measure.magnitude = 100.0
                        100.0 -> measure.magnitude = 64.0
                        else -> if (measure.magnitude > 8.0) {
                            measure.magnitude -= 8
                        }
                    }
                }
                KeyboardButton.Equal -> {
                    when (measure.magnitude) {
                        200.0 -> measure.magnitude = 256.0
                        128.0 -> measure.magnitude = 200.0
                        100.0 -> measure.magnitude = 128.0
                        64.0 -> measure.magnitude = 100.0
                        else -> if (measure.magnitude < 64.0) {
                            measure.magnitude += 8
                        }
                    }
                }
                KeyboardButton.Escape -> ses = Unit
                else -> Unit
            }
        }
    }
    private val measure = MutableDoubleMeasure(48.0)

    override fun shouldEngineStop(): Boolean {
        return ::ses.isInitialized
    }

    private fun onRenderGrid(canvas: Canvas) {
        val ps = engine.property.pictureSize
        val xNum = (ps.width / measure.magnitude).toInt()
        val color = Color.Green.copy(alpha = 0.5f)
        val hc = Color.Green.copy(alpha = 0.75f)
        val fontHeight = 16.0
        for (x in 1..xNum) {
            val h = x % 5 == 0
            val mX = measure.transform(x.toDouble())
            canvas.texts.draw(
                color = if (h) hc else color,
                fontHeight = fontHeight,
                text = "$x",
                pointTopLeft = pointOf(x = mX + 4.0, y = 4.0),
            )
            canvas.vectors.draw(
                color = if (h) hc else color,
                vector = vectorOf(
                    startX = mX,
                    startY = 0.0,
                    finishX = mX,
                    finishY = ps.height,
                ),
                lineWidth = if (h) 1.5 else 1.0,
            )
        }
        val yNum = (ps.height / measure.magnitude).toInt()
        for (y in 1..yNum) {
            val h = y % 5 == 0
            val mY = measure.transform(y.toDouble())
            canvas.texts.draw(
                color = if (h) hc else color,
                fontHeight = fontHeight,
                text = "$y",
                pointTopLeft = pointOf(x = 4.0, y = mY),
            )
            canvas.vectors.draw(
                color = if (h) hc else color,
                vector = vectorOf(
                    startX = 0.0,
                    startY = mY,
                    finishX = ps.width,
                    finishY = mY,
                ),
                lineWidth = if (h) 1.5 else 1.0,
            )
        }
    }

    override fun onRender(canvas: Canvas) {
        onRenderGrid(canvas = canvas)
        val fontHeight = 1.0
        val text = "Hello world!"
        val offset = offsetOf(1, 1)
        val pointTopLeft = Point.Center
        canvas.texts.draw(
            color = Color.Green,
            fontHeight = fontHeight,
            text = text,
            pointTopLeft = pointTopLeft,
            offset = offset,
            measure = measure,
        )
        canvas.polygons.drawRectangle(
            color = Color.Yellow,
            pointTopLeft = pointTopLeft,
            size = sizeOf(height = fontHeight, width = canvas.texts.getTextWidth(fontHeight = fontHeight, text = text, measure = measure)),
            lineWidth = 0.1,
            offset = offset,
            measure = measure,
        )
        canvas.texts.draw(
            color = Color.Green,
            fontHeight = 24.0,
            text = "${measure.magnitude}",
            pointTopLeft = Point.Center,
            offset = offsetOf(dX = 0.0, engine.property.pictureSize.height - 24.0),
        )
    }
}
