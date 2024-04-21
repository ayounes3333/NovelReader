package my.noveldokusha.ui.browse.extractor.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint


object BitmapUtils {
    fun textAsBitmap(text: String, textSize: Float): Bitmap {
        val backgroundPaint = Paint(
            (Paint.ANTI_ALIAS_FLAG
                    or Paint.LINEAR_TEXT_FLAG)
        )
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.color = Color.GRAY
        val width = 200 //(paint.measureText(text) + 0.5f).roundToInt() // round
        val height = 277 //(baseline + paint.descent() + 0.5f).roundToInt()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawPaint(backgroundPaint)

        val myTextPaint = TextPaint()
        myTextPaint.isAntiAlias = true
        myTextPaint.textSize = textSize
        myTextPaint.color = Color.WHITE

        val textWidth = 180
        val alignment = Layout.Alignment.ALIGN_CENTER
        val spacingMultiplier = 1f
        val spacingAddition = 0f
        val includePadding = false
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, myTextPaint, textWidth)
            .setAlignment(alignment)
            .setLineSpacing(spacingAddition, spacingMultiplier)
            .setIncludePad(includePadding)
            .setMaxLines(5)
        val myStaticLayout = builder.build()

        /*// draw text to the Canvas center
        val boundsText = Rect()
        paint.getTextBounds(text, 0, text.length, boundsText)
        val x: Int = (image.width - boundsText.width()) / 2
        val y: Int = (image.height + boundsText.height()) / 2
        canvas.drawText(text, x - boundsText.left.toFloat(), y - boundsText.bottom.toFloat(), paint)
        */
        myStaticLayout.draw(canvas)
        return image
    }
}