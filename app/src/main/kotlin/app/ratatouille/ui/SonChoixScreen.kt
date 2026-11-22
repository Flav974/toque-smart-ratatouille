package app.ratatouille.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import app.ratatouille.core.sons.Son
import app.ratatouille.sons.Lecteur
import app.toque.core.i18n.tr
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import java.io.File

/**
 * Choix du fichier d'un son (Réglages › Sons) : sons du système, du dossier de l'appli (dépôt
 * depuis le PC) et du stockage du robot. Un appui sur ▶ fait écouter ; « Choisir » copie le fichier.
 */
class SonChoixScreen(a: MainActivity, private val son: Son) : Screen(a) {
    override val title = tr("Son : %s", tr(son.label))
    private lateinit var liste: LinearLayout

    override fun build(): View {
        val col = Ui.vertical(c)
        col.setPadding(Ui.u(24), Ui.u(8), Ui.u(24), Ui.u(24))
        val top = Ui.horizontal(c)
        val defaut = if (son.defaut.fois > 0) tr("Son par défaut (bips)") else tr("Aucun son")
        top.addView(Ui.button(c, defaut, Ui.Style.SECONDARY, Icon.Kind.SYNC, height = 56) {
            a.sons.retire(son)
            a.toast(tr("Son par défaut remis."))
            a.back()
        }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        col.addView(top)
        col.addView(Ui.text(c, tr("Pour ajouter vos sons depuis le PC : déposez-les dans Android/data/%s/files/sons/.", a.packageName),
            15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 8))
        liste = Ui.vertical(c)
        col.addView(liste, Ui.lp(t = 12))
        return Ui.scroll(c, col)
    }

    override fun onShow() = remplit()

    override fun onHide() = a.sons.stop()

    private fun accesStockage(): Boolean =
        Build.VERSION.SDK_INT < 23 || a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun remplit() {
        liste.removeAllViews()
        if (!accesStockage()) {
            liste.addView(Ui.button(c, tr("Autoriser l'accès aux fichiers du robot"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 56) {
                if (Build.VERSION.SDK_INT >= 23) a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7)
            }, Ui.lp(Ui.WRAP, Ui.WRAP, b = 12))
        }
        var vide = true
        for ((nom, dossier) in Lecteur.dossiers(a)) {
            val fichiers = Lecteur.sons(dossier)
            if (fichiers.isEmpty()) continue
            vide = false
            val card = Ui.card(c, 20f)
            card.setPadding(Ui.u(20), Ui.u(14), Ui.u(20), Ui.u(10))
            card.addView(Ui.text(c, tr(nom), 19f, Ui.TEXT, Ui.MEDIUM))
            for (f in fichiers) card.addView(ligne(f))
            liste.addView(card, Ui.lp(b = 12))
        }
        if (vide) liste.addView(Ui.text(c, tr("Aucun fichier son trouvé."), 17f, Ui.TEXT2))
    }

    private fun ligne(f: File): View {
        val row = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
        row.setPadding(0, Ui.u(4), 0, Ui.u(4))
        row.addView(Ui.ellipsize(Ui.text(c, f.nameWithoutExtension.replace('_', ' '), 17f, Ui.TEXT)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(Ui.button(c, "", Ui.Style.GHOST, Icon.Kind.PLAY, height = 46) {
            if (!a.sons.joue(f)) a.toast(tr("Lecture impossible."), Ui.WARN)
        }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        row.addView(Ui.button(c, tr("Choisir"), Ui.Style.SECONDARY, Icon.Kind.CHECK, height = 46) {
            if (a.sons.choisit(son, f)) { a.toast(tr("Son choisi : %s", f.nameWithoutExtension)); a.back() }
            else a.toast(tr("Fichier refusé (format ou taille)."), Ui.WARN)
        })
        return row
    }
}
