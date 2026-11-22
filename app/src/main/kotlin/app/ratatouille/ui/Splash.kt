package app.ratatouille.ui

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.ratatouille.core.home.Home
import app.ratatouille.core.pack.PackImage
import app.ratatouille.pack.PackImages
import app.toque.ui.Ui

/**
 * Écran de démarrage (EX-11), d'après l'image de référence 1 : affiché par-dessus l'accueil pendant
 * le chargement, 3,5 s (demande de l'utilisateur) ; un appui le ferme. Sans pack : fond bois et logo dessiné.
 */
class Splash(private val host: FrameLayout, private val onGone: (Long) -> Unit) {
    /** Compté à partir du premier affichage réel (au démarrage du robot, l'appli s'ouvre parfois avant l'écran). */
    private var started = SystemClock.elapsedRealtime()
    private val view = FrameLayout(host.context)
    private var closing = false
    private val poll = object : Runnable {
        override fun run() {
            if (closing) return
            if (Home.splashDone(SystemClock.elapsedRealtime() - started)) close() else view.postDelayed(this, 100)
        }
    }

    fun show() {
        val c = host.context
        val dm = c.resources.displayMetrics
        val bmp = PackImages(c).bitmap(PackImage.DEMARRAGE, maxOf(dm.widthPixels, dm.heightPixels), minOf(dm.widthPixels, dm.heightPixels))
        if (bmp != null) {
            view.setBackgroundColor(RatTheme.WOOD_DARK)
            view.addView(ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(bmp) }, Ui.flp())
        } else {
            view.background = RatTheme.woodBackground()
            view.addView(RatHomeScreen.textLogo(c, 2.2f), Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.CENTER))
        }
        view.isClickable = true
        view.setOnClickListener { close() }
        host.addView(view, Ui.flp())
        view.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                view.viewTreeObserver.removeOnPreDrawListener(this)
                started = SystemClock.elapsedRealtime()
                view.postDelayed(poll, 100)
                return true
            }
        })
    }

    fun close() {
        if (closing) return
        closing = true
        view.removeCallbacks(poll)
        onGone(SystemClock.elapsedRealtime() - started)
        view.animate().alpha(0f).setDuration(250).withEndAction {
            host.removeView(view)
            // Libère tout de suite l'image du démarrage (environ 4 Mo).
            (view.getChildAt(0) as? ImageView)?.setImageDrawable(null)
            view.removeAllViews()
        }.start()
    }

    val showing: Boolean get() = !closing
}
