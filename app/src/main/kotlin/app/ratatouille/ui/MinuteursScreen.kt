package app.ratatouille.ui

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.ratatouille.core.minuteur.Minuteur
import app.toque.core.i18n.tr
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Minuteurs de cuisine (2026-11-22) : trois décomptes indépendants du robot (four, repos d'une
 * pâte…). Ils continuent quand on quitte l'écran ; la sonnerie vient de [MainActivity.minuteurs].
 */
class MinuteursScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Minuteurs")
    private val vues = ArrayList<Triple<Minuteur, TextView, TextView>>()
    private val tick = object : Runnable {
        override fun run() {
            rafraichit()
            view.postDelayed(this, 500)
        }
    }

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(8), Ui.u(24), Ui.u(20))
        for ((i, m) in a.minuteurs.withIndex()) {
            val card = Ui.card(c, 24f)
            card.setPadding(Ui.u(20), Ui.u(16), Ui.u(20), Ui.u(16))
            card.gravity = Gravity.CENTER_HORIZONTAL
            card.addView(Ui.text(c, tr("Minuteur %d", m.numero), 22f, Ui.TEXT, Ui.MEDIUM))
            val temps = Ui.text(c, "", 60f, Ui.TEXT, Ui.LIGHT).apply { gravity = Gravity.CENTER }
            card.addView(temps, Ui.lp(Ui.MATCH, Ui.WRAP, t = 6))
            // Réglage rapide : des durées toutes faites (3 par ligne, même largeur), puis −1 / +1 min.
            fun ligne(vararg boutons: View) = Ui.horizontal(c, Gravity.CENTER).apply {
                for ((k, b) in boutons.withIndex()) addView(b, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (k > 0) leftMargin = Ui.u(8) })
            }
            fun pastille(label: String, plein: Boolean, action: () -> Unit): View = Ui.text(c, label, 19f, Ui.TEXT, Ui.MEDIUM).apply {
                gravity = Gravity.CENTER
                setSingleLine()
                minHeight = Ui.u(54)
                background = Ui.pressable(if (plein) Ui.round(Ui.SURFACE3, 28f) else Ui.round(0, 28f, Ui.STROKE, 2f), 28f)
                setOnClickListener { action(); rafraichit() }
            }
            fun duree(min: Int) = pastille("$min min", true) { m.regle(min * 60_000L) }
            card.addView(ligne(duree(1), duree(3), duree(5)), Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
            card.addView(ligne(duree(10), duree(15), duree(30)), Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
            card.addView(ligne(
                pastille("−1 min", false) { m.ajoute(-60_000, SystemClock.elapsedRealtime()) },
                pastille("+1 min", false) { m.ajoute(60_000, SystemClock.elapsedRealtime()) },
                pastille("↺", false) { m.remet() },
            ), Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
            card.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
            val action = Ui.text(c, "", 20f, 0xFF1A0E05.toInt(), Ui.MEDIUM).apply {
                gravity = Gravity.CENTER
                background = Ui.pressable(Ui.round(Ui.ACCENT, 32f), 32f)
                setOnClickListener {
                    val now = SystemClock.elapsedRealtime()
                    when {
                        m.fini(now) -> a.arreteMinuteur(m)
                        m.tourne -> m.pause(now)
                        else -> m.lance(now)
                    }
                    rafraichit()
                }
            }
            card.addView(action, Ui.lp(Ui.MATCH, Ui.u(60), t = 10))
            root.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i > 0) leftMargin = Ui.u(14) })
            vues += Triple(m, temps, action)
        }
        return root
    }

    private fun rafraichit() {
        val now = SystemClock.elapsedRealtime()
        for ((m, temps, action) in vues) {
            temps.text = Minuteur.texte(m.restant(now))
            temps.setTextColor(if (m.fini(now)) Ui.WARN else Ui.TEXT)
            action.text = when {
                m.fini(now) -> tr("Arrêter la sonnerie")
                m.tourne -> tr("Pause")
                m.restant(now) > 0 && m.restant(now) < m.dureeMs -> tr("Reprendre")
                else -> tr("Démarrer")
            }
            action.alpha = if (m.tourne || m.restant(now) > 0) 1f else 0.5f
        }
    }

    override fun onShow() {
        view.removeCallbacks(tick)
        tick.run()
    }

    override fun onHide() {
        view.removeCallbacks(tick)
    }
}
