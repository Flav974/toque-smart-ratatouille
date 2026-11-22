package app.ratatouille.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider

/**
 * Habillage « Édition Ratatouille » (phase R1) : couleurs chaudes de l'image de référence 3
 * (bois, crème, brun). Le reste de l'appli garde pour l'instant le thème sombre de Toque.
 */
object RatTheme {
    const val CREAM = 0xFFF8EEDF.toInt()
    const val CREAM_DARK = 0xFFEBDCC6.toInt()
    const val BROWN = 0xFF4A2A16.toInt()
    const val BROWN_SOFT = 0xFF7A5236.toInt()
    const val WOOD_DARK = 0xFF2A170C.toInt()
    const val WOOD = 0xFF5A3418.toInt()
    const val WOOD_LIGHT = 0xFF8A5528.toInt()
    const val GOLD = 0xFFE7A93B.toInt()
    const val WATER = 0xFF2E8BE6.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val SHADOW = 0x99000000.toInt()

    /** Police étroite, proche de celle des tuiles de l'image 3. */
    val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    val CONDENSED_BOLD: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    /** Écriture manuscrite (« Édition Ratatouille ») ; Android remplace par une police normale si absente. */
    val SCRIPT: Typeface = Typeface.create("cursive", Typeface.BOLD)

    /** Fond sans pack : dégradé de bois chaud (image neutre, EX-41). */
    fun woodBackground(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(WOOD_DARK, WOOD, WOOD_LIGHT))

    /** Coupe une vue (image comprise) en rectangle à coins arrondis. */
    fun clipRound(v: View, radiusPx: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        v.clipToOutline = true
    }
}

/** Petits pictogrammes de l'accueil absents de [app.toque.ui.Icon], dessinés sur une grille de 24. */
class RatGlyph(c: Context, private var kind: Kind, color: Int) : View(c) {
    enum class Kind { WIFI, WIFI_OFF, CHEF_HAT, DROP, HEART }

    var color: Int = color
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    fun set(k: Kind) {
        if (k != kind) {
            kind = k
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        if (s <= 0f) return
        stroke.color = color
        fill.color = color
        canvas.save()
        canvas.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        canvas.scale(s, s)
        stroke.strokeWidth = 2.2f
        val p = path
        p.reset()
        when (kind) {
            Kind.WIFI, Kind.WIFI_OFF -> {
                for (r in floatArrayOf(4.5f, 9f, 13.5f)) {
                    rect.set(12f - r, 20f - r, 12f + r, 20f + r)
                    canvas.drawArc(rect, 225f, 90f, false, stroke)
                }
                canvas.drawCircle(12f, 19.5f, 1.8f, fill)
                if (kind == Kind.WIFI_OFF) canvas.drawLine(4f, 4f, 20f, 21f, stroke)
            }
            Kind.CHEF_HAT -> {
                // Toque de cuisinier : trois bosses et un bandeau.
                p.moveTo(7f, 14f)
                p.cubicTo(2.5f, 13.5f, 2.5f, 6.5f, 7.5f, 7f)
                p.cubicTo(8.5f, 2.5f, 15.5f, 2.5f, 16.5f, 7f)
                p.cubicTo(21.5f, 6.5f, 21.5f, 13.5f, 17f, 14f)
                p.lineTo(17f, 19f)
                p.lineTo(7f, 19f)
                p.close()
                canvas.drawPath(p, stroke)
                canvas.drawLine(7f, 16f, 17f, 16f, stroke)
            }
            Kind.DROP -> {
                p.moveTo(12f, 2.5f)
                p.cubicTo(12f, 2.5f, 4.5f, 11f, 4.5f, 15f)
                p.cubicTo(4.5f, 19.2f, 7.8f, 22f, 12f, 22f)
                p.cubicTo(16.2f, 22f, 19.5f, 19.2f, 19.5f, 15f)
                p.cubicTo(19.5f, 11f, 12f, 2.5f, 12f, 2.5f)
                p.close()
                canvas.drawPath(p, fill)
            }
            Kind.HEART -> {
                p.moveTo(12f, 20.5f)
                p.cubicTo(5f, 15.5f, 2.5f, 12f, 2.5f, 8.5f)
                p.cubicTo(2.5f, 5.5f, 4.8f, 3.5f, 7.5f, 3.5f)
                p.cubicTo(9.5f, 3.5f, 11f, 4.6f, 12f, 6.3f)
                p.cubicTo(13f, 4.6f, 14.5f, 3.5f, 16.5f, 3.5f)
                p.cubicTo(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f)
                p.cubicTo(21.5f, 12f, 19f, 15.5f, 12f, 20.5f)
                p.close()
                canvas.drawPath(p, stroke)
            }
        }
        canvas.restore()
    }
}
