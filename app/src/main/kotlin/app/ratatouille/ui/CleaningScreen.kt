package app.ratatouille.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.ratatouille.core.cuisine.Fonctions
import app.ratatouille.core.cuisine.Fonctions.Etat
import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.toque.core.i18n.tr
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Nettoyage (EX-14, EX-21, EX-22). Les deux programmes de l'appli d'origine enchaînent plusieurs
 * étapes (vitesse 10, puis vitesse 4 en sens inverse ; le nettoyage long chauffe aussi à 40 °C).
 * Seule la première étape est capturée (`captures5/`, 19:32) : tant que le programme n'est pas
 * capturé en entier, il est refusé et cet écran n'envoie aucune trame. Chaque carte ouvre le
 * réglage de la fonction ([FonctionScreen]), qui démarrera dès que la capture sera ajoutée.
 */
class CleaningScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Nettoyage")

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(8), Ui.u(24), Ui.u(20))
        val row = Ui.horizontal(c, Gravity.TOP)
        val items = listOf(Fonction.PRELAVAGE, Fonction.NETTOYAGE_LONG)
        for ((i, f) in items.withIndex()) {
            val card = Ui.card(c, 24f)
            card.setPadding(Ui.u(24), Ui.u(22), Ui.u(24), Ui.u(22))
            val badge = FrameLayout(c)
            badge.background = Ui.round(0x332E8BE6, 28f)
            badge.addView(RatGlyph(c, RatGlyph.Kind.DROP, RatTheme.WATER), Ui.flp(Ui.u(30), Ui.u(30), Gravity.CENTER))
            card.addView(badge, LinearLayout.LayoutParams(Ui.u(56), Ui.u(56)))
            card.addView(Ui.text(c, tr(f.label), 28f, Ui.TEXT, Ui.MEDIUM), Ui.lp(t = 14))
            card.addView(Ui.text(c, Fonctions.horloge(f.seconds).first, 44f, Ui.TEXT, Ui.LIGHT), Ui.lp(t = 6))
            card.addView(Ui.text(c, tr(f.origine), 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 6))
            if (f.etat != Etat.PRETE)
                card.addView(Ui.text(c, tr(f.raison), 16f, Ui.WARN).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))
            card.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
            val ready = f.etat == Etat.PRETE
            val b = Ui.button(c, if (ready) tr("Régler") else tr("À capturer d'abord"),
                if (ready) Ui.Style.PRIMARY else Ui.Style.SECONDARY, if (ready) Icon.Kind.PLAY else Icon.Kind.CLOCK, height = 62) {
                FonctionScreen.open(a, f)
            }
            card.addView(b, Ui.lp(t = 14))
            row.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i == 0) rightMargin = Ui.u(16) })
        }
        // Le rat frotte le bol (R3), à droite des deux cartes.
        val rat = RatView(c, app.ratatouille.pack.PackImages(c), Ui.u(240), Ui.u(400))
        rat.show(app.ratatouille.core.pack.RatPose.choix(app.ratatouille.core.pack.PackImage.RAT_NETTOYAGE))
        row.addView(rat, LinearLayout.LayoutParams(Ui.u(240), Ui.MATCH).apply { leftMargin = Ui.u(12) })
        root.addView(row, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        return root
    }
}
