package app.ratatouille.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import app.ratatouille.core.courses.QrCode

/** Dessine un QR code net (modules entiers, marge blanche de 4 modules exigée par la norme). */
class QrView(c: Context, private val qr: QrCode) : View(c) {
    private val noir = Paint().apply { color = Color.BLACK; isAntiAlias = false }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val n = qr.size + 8
        val m = minOf(width, height) / n
        if (m <= 0) return
        val x0 = (width - m * n) / 2 + 4 * m
        val y0 = (height - m * n) / 2 + 4 * m
        for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr.get(x, y))
            canvas.drawRect((x0 + x * m).toFloat(), (y0 + y * m).toFloat(), (x0 + (x + 1) * m).toFloat(), (y0 + (y + 1) * m).toFloat(), noir)
    }
}
