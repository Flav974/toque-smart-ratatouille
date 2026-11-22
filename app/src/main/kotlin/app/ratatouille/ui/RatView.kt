package app.ratatouille.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.ratatouille.core.pack.PackImage
import app.ratatouille.core.pack.RatPose
import app.ratatouille.pack.PackImages
import app.toque.core.machine.Snapshot
import app.toque.ui.Ui

/**
 * Le rat dans un coin de l'écran (phase R3). Montre la première image présente d'une liste
 * ([RatPose.choix]) ; sans pack, une toque dessinée (EX-41). Les images déjà lues restent en mémoire
 * tant que la vue existe (quelques images de 1 à 2 Mo au plus).
 */
class RatView(c: Context, private val pack: PackImages, private val maxW: Int, private val maxH: Int) : FrameLayout(c) {
    private val img = ImageView(c).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
    }
    private val hat = RatGlyph(c, RatGlyph.Kind.CHEF_HAT, Ui.TEXT3)
    private val cache = HashMap<PackImage, Bitmap?>()
    private var lastChoices: List<PackImage>? = null

    /** Rat posé en bas de sa zone (pieds en bas), ou centré. */
    var bottom: Boolean = true
        set(v) {
            field = v
            img.scaleType = if (v) ImageView.ScaleType.FIT_END else ImageView.ScaleType.FIT_CENTER
        }

    init {
        addView(img, Ui.flp())
        addView(hat, Ui.flp(Ui.u(120), Ui.u(120), Gravity.CENTER))
        img.scaleType = ImageView.ScaleType.FIT_END
        hat.visibility = View.GONE
    }

    /** Montre la première image présente de [choices]. Ne relit rien si la liste n'a pas changé. */
    fun show(choices: List<PackImage>) {
        if (choices == lastChoices) return
        lastChoices = choices
        val p = pack.pack.first(choices)
        val b = p?.let { cache.getOrPut(it) { pack.bitmap(it, maxW, maxH) } }
        if (b == null) {
            img.setImageDrawable(null)
            img.visibility = View.GONE
            hat.visibility = View.VISIBLE
        } else {
            img.setImageBitmap(b)
            img.visibility = View.VISIBLE
            hat.visibility = View.GONE
        }
    }

    /** Rat de [action], selon l'état de la machine (attention, attente, prêt…). */
    fun follow(action: PackImage, s: Snapshot) = show(RatPose.choix(action, RatPose.humeur(s)))

    companion object {
        /** Décor clair et flou derrière tous les écrans (sauf l'accueil, qui a le sien). */
        fun installDecor(c: Context, root: FrameLayout) {
            val b = PackImages(c).bitmap(PackImage.FOND_ECRAN, 1280, 800) ?: return
            root.addView(ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(b)
            }, 0, Ui.flp())
        }
    }
}
