package app.toque.ui.screens

import android.content.Intent
import android.content.pm.ResolveInfo
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import app.toque.core.i18n.tr
import app.toque.core.machine.RootSerialTransport
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Applications du système (Toque-Smart) : toutes les applis qui ont une icône de lancement,
 * pour ouvrir le magasin d'applis, le navigateur, etc. quand Toque est l'écran d'accueil.
 * Le bouton flottant « Retour à l'accueil » s'affiche par-dessus pour en revenir.
 *
 * L'appli d'origine et l'appli de test usine n'y figurent pas : elles ouvrent le port série
 * du moteur. L'appli d'origine s'ouvre par Réglages › Recettes Lidl (Toque libère alors le port).
 */
class AppsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Applications")
    private lateinit var grid: GridLayout

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), Ui.u(20))
        grid = GridLayout(c).apply { columnCount = 6 }
        root.addView(Ui.scroll(c, grid), Ui.lp(Ui.MATCH, Ui.MATCH))
        return root
    }

    override fun onShow() = load()

    // Édition Ratatouille : Toque Smart (installé à côté pendant les essais) pilote aussi le moteur.
    private fun hidden(pkg: String): Boolean = pkg == a.packageName || pkg == "app.toque.smart" ||
        pkg == a.prefs.vendorPackage || pkg == RootSerialTransport.SMART_VENDOR || pkg == RootSerialTransport.SMART_FACTORY_APP

    private fun load() {
        grid.removeAllViews()
        val pm = a.packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).filter { !hidden(it.activityInfo.packageName) }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (apps.isEmpty()) {
            grid.addView(Ui.text(c, tr("Aucune application trouvée."), 18f, Ui.TEXT2))
            return
        }
        for (info in apps) {
            val cell = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
            cell.setPadding(Ui.u(8), Ui.u(12), Ui.u(8), Ui.u(12))
            val icon = ImageView(c).apply { setImageDrawable(info.loadIcon(pm)) }
            cell.addView(icon, LinearLayout.LayoutParams(Ui.u(72), Ui.u(72)))
            val label = Ui.ellipsize(Ui.text(c, info.loadLabel(pm), 15f, Ui.TEXT)).apply { gravity = Gravity.CENTER }
            cell.addView(label, Ui.lp(Ui.u(140), Ui.WRAP, t = 6))
            cell.isClickable = true
            cell.setOnClickListener {
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                a.openExternal(launch)
            }
            grid.addView(cell)
        }
    }
}
