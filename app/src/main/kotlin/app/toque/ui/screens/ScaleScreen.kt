package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.ratatouille.core.pack.PackImage
import app.ratatouille.pack.PackImages
import app.ratatouille.ui.RatView
import android.widget.TextView
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/** Balance intégrée : grand affichage, tare au doigt ou par appui sur le bouton rotatif. */
class ScaleScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Balance")
    private lateinit var value: TextView
    private lateinit var state: LinearLayout
    private var simulated = 0

    override fun build(): View {
        val root = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        root.setPadding(Ui.u(24), Ui.u(10), Ui.u(24), Ui.u(24))
        root.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
        val row = Ui.horizontal(c, Gravity.BOTTOM)
        value = Ui.text(c, "—", 200f, Ui.TEXT, Ui.THIN)
        row.addView(value)
        row.addView(Ui.text(c, "g", 60f, Ui.TEXT2, Ui.THIN), Ui.lp(Ui.WRAP, Ui.WRAP, l = 12, b = 34))
        root.addView(row, Ui.lp(Ui.WRAP, Ui.WRAP))
        state = Ui.chip(c, "", Icon.Kind.SCALE, Ui.TEXT2)
        root.addView(state, Ui.lp(Ui.WRAP, Ui.WRAP, t = 6))
        root.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
        val buttons = Ui.horizontal(c, Gravity.CENTER)
        buttons.addView(Ui.button(c, tr("Tare (remise à zéro)"), Ui.Style.PRIMARY, Icon.Kind.SCALE, height = 76, textSize = 23f) {
            a.machine.tare()
        })
        if (a.prefs.mode == Prefs.Mode.DEMO) {
            buttons.addView(Ui.spacer(c, w = 30))
            for (g in intArrayOf(100, 250)) {
                buttons.addView(Ui.button(c, "+$g g", Ui.Style.GHOST, height = 64) {
                    simulated += g
                    a.machine.simulate(weight = simulated)
                }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
            }
            buttons.addView(Ui.button(c, tr("Vider"), Ui.Style.GHOST, height = 64) {
                simulated = 0
                a.machine.simulate(weight = 0)
            })
        }
        root.addView(buttons)
        // Édition Ratatouille (R3) : le rat pèse une pomme, en bas à droite.
        val frame = FrameLayout(c)
        frame.addView(root, Ui.flp())
        rat = RatView(c, PackImages(c), Ui.u(280), Ui.u(380))
        frame.addView(rat, Ui.flp(Ui.u(280), Ui.u(380), Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.u(16); bottomMargin = Ui.u(12) })
        rat.follow(PackImage.RAT_BALANCE, a.snapshot)
        return frame
    }

    private lateinit var rat: RatView

    override fun onSnapshot(s: Snapshot) {
        rat.follow(PackImage.RAT_BALANCE, s)
        if (!s.hasSample) {
            value.text = "—"
            Ui.chipText(state).text = tr("Base non connectée")
            return
        }
        value.text = s.weightG.toString()
        val (txt, col) = when {
            s.scaleState == "overload" -> tr("Surcharge") to Ui.DANGER
            s.scaleStable -> tr("Stable") to Ui.OK
            else -> tr("Mesure…") to Ui.TEXT2
        }
        Ui.chipText(state).text = txt
        Ui.chipText(state).setTextColor(col)
        (state.getChildAt(0) as Icon).color = col
    }

    override fun onKnobPress(): Boolean {
        a.machine.tare()
        return true
    }
}
