package app.ratatouille.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import app.ratatouille.core.cuisine.Fonctions
import app.ratatouille.core.cuisine.Fonctions.Etat
import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.ratatouille.core.cuisine.Fonctions.Sorte
import app.toque.core.i18n.tr
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Toutes les fonctions de l'appli d'origine (EX-21), rangées par groupe. Chaque carte ouvre son
 * réglage prérempli ; une fonction pas encore capturée est grisée et dit pourquoi.
 */
class FonctionsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Toutes les fonctions")

    override fun build(): View {
        val col = Ui.vertical(c)
        col.setPadding(Ui.u(24), Ui.u(4), Ui.u(24), Ui.u(24))
        for (g in Fonctions.Groupe.values()) {
            val list = Fonction.values().filter { it.groupe == g }
            if (list.isEmpty()) continue
            col.addView(Ui.text(c, tr(g.label).uppercase(), 15f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.08f }, Ui.lp(t = 14, b = 8))
            var row: LinearLayout? = null
            for ((i, f) in list.withIndex()) {
                if (i % 4 == 0) {
                    row = Ui.horizontal(c, Gravity.TOP)
                    col.addView(row, Ui.lp(Ui.MATCH, Ui.u(112), b = 12))
                }
                row!!.addView(card(f), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i % 4 < 3) rightMargin = Ui.u(12) })
            }
            // Dernière rangée incomplète : cases vides pour garder la même largeur de carte.
            val rest = (4 - list.size % 4) % 4
            for (k in 0 until rest) row!!.addView(Ui.spacer(c), LinearLayout.LayoutParams(0, 1, 1f).apply { if (k < rest - 1) rightMargin = Ui.u(12) })
        }
        return Ui.scroll(c, col)
    }

    private fun card(f: Fonction): View {
        val card = Ui.vertical(c)
        card.setPadding(Ui.u(16), Ui.u(12), Ui.u(14), Ui.u(12))
        val ready = f.etat == Etat.PRETE
        card.background = Ui.pressable(Ui.round(Ui.SURFACE, 20f, Ui.STROKE, 1f), 20f)
        val head = Ui.horizontal(c)
        head.addView(Icon(c, icon(f), if (ready) Ui.ACCENT else Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(26), Ui.u(26)).apply { rightMargin = Ui.u(8) })
        head.addView(Ui.ellipsize(Ui.text(c, tr(f.label), 19f, if (ready) Ui.TEXT else Ui.TEXT3, Ui.MEDIUM)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(head)
        card.addView(Ui.ellipsize(Ui.text(c, resume(f), 15f, if (ready) Ui.TEXT2 else Ui.WARN), 2), Ui.lp(t = 6))
        card.isClickable = true
        card.setOnClickListener { FonctionScreen.open(a, f) }
        return card
    }

    /** Une ligne : réglage prérempli, ou l'état de la fonction. */
    private fun resume(f: Fonction): String = when {
        f.etat == Etat.A_CAPTURER -> tr("À capturer avec l'appli d'origine")
        f.sorte == Sorte.BALANCE -> tr("Pesée et tare")
        f.sorte == Sorte.TURBO -> tr("Tant qu'on appuie")
        else -> buildString {
            append(Fonctions.texteDuree(f.dureeProposee))
            if (f.celsius > 0) append(" · ").append(f.celsius).append(" °C")
            append(" · ").append(tr("vitesse %d", f.speed))
            if (f.reverse) append(" · ").append(tr("inverse"))
        }
    }

    private fun icon(f: Fonction): Icon.Kind = when (f.sorte) {
        Sorte.MALAXER -> Icon.Kind.KNEAD
        Sorte.VAPEUR -> Icon.Kind.STEAM
        Sorte.SAISIR -> Icon.Kind.FLAME
        Sorte.TURBO -> Icon.Kind.BOLT
        Sorte.BALANCE -> Icon.Kind.SCALE
        else -> when {
            f.groupe == Fonctions.Groupe.NETTOYER -> Icon.Kind.SYNC
            f.celsius > 0 -> Icon.Kind.POT
            f.speed >= 8 -> Icon.Kind.WHISK
            else -> Icon.Kind.SPEED
        }
    }
}
