package app.ratatouille.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.ratatouille.core.home.Home
import app.ratatouille.core.pack.PackImage
import app.ratatouille.musique.MusiqueBulle
import app.ratatouille.pack.PackImages
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.screens.AppsScreen
import app.toque.ui.screens.CookScreen
import app.toque.ui.screens.RecipesScreen
import app.toque.ui.screens.ScaleScreen
import app.toque.ui.screens.SettingsScreen

/**
 * Accueil « Édition Ratatouille » (phase R1, EX-12 à EX-14), d'après l'image de référence 3 adaptée
 * au 1280x800 du robot : logo et barre d'état en haut, rat et sa bulle à gauche, 6 tuiles,
 * 5 boutons en bas. Les images viennent du pack privé ; sans pack, des images neutres (EX-41).
 */
class RatHomeScreen(a: MainActivity) : Screen(a) {
    override val title = "Monsieur Cuisine"

    private val pack = PackImages(a)
    private lateinit var clock: TextView
    private lateinit var wifi: RatGlyph
    private lateinit var status: TextView
    private lateinit var hello: TextView
    private lateinit var bubbleText: TextView
    private lateinit var resumeLink: TextView
    private lateinit var musique: MusiqueBulle
    private lateinit var minuteurTexte: TextView
    private val tickMinuteur = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtime()
            val m = a.minuteurs.filter { it.tourne }.minByOrNull { it.restant(now) }
            minuteurTexte.text = m?.let { app.ratatouille.core.minuteur.Minuteur.texte(it.restant(now)) } ?: ""
            minuteurTexte.visibility = if (m != null) View.VISIBLE else View.GONE
            view.postDelayed(this, 1_000)
        }
    }
    private var resumeId = ""
    private var unsub: (() -> Unit)? = null
    private var adminTaps = 0
    private var adminFirst = 0L
    private val tick = object : Runnable {
        override fun run() {
            refreshClock()
            view.postDelayed(this, 10_000)
        }
    }

    /** Moment de la journée affiché (décor jour, coucher de soleil ou nuit, EX-51). */
    private var moment = currentMoment()
    private var bgView: ImageView? = null
    private var ratImage: ImageView? = null

    private fun currentMoment(): Home.Moment {
        val cal = java.util.Calendar.getInstance()
        return Home.moment(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MONTH) + 1)
    }

    private fun firstImage(list: List<PackImage>, w: Int, h: Int): Bitmap? =
        pack.pack.first(list)?.let { image(it, w, h) }

    /** Change le décor et le rat quand le moment de la journée change. */
    private fun refreshMoment() {
        val m = currentMoment()
        if (m == moment) return
        moment = m
        firstImage(m.decor, 1280, 800)?.let { bgView?.setImageBitmap(it) }
        firstImage(m.rat, Ui.u(330), Ui.u(470))?.let { ratImage?.setImageBitmap(it) }
    }

    override fun build(): View {
        val root = FrameLayout(c)
        // Décor : image du pack selon l'heure, ou dégradé de bois.
        val bg = firstImage(moment.decor, 1280, 800)
        if (bg != null) {
            root.addView(ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(bg)
                bgView = this
            }, Ui.flp())
            // Voile sombre en haut : l'heure et le logo restent lisibles sur un décor clair.
            root.addView(View(c).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x88000000.toInt(), 0x00000000))
            }, Ui.flp(Ui.MATCH, Ui.u(120), Gravity.TOP))
        } else root.background = RatTheme.woodBackground()

        val col = Ui.vertical(c)
        col.setPadding(Ui.u(22), Ui.u(10), Ui.u(22), Ui.u(18))
        col.addView(header(), Ui.lp(Ui.MATCH, Ui.u(72)))

        val body = Ui.horizontal(c, Gravity.TOP)
        body.addView(ratColumn(), LinearLayout.LayoutParams(Ui.u(330), Ui.MATCH).apply { rightMargin = Ui.u(18) })
        val right = Ui.vertical(c)
        right.addView(tiles(), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        right.addView(buttons(), LinearLayout.LayoutParams(Ui.MATCH, Ui.u(96)).apply { topMargin = Ui.u(16) })
        body.addView(right, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        col.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(4) })
        root.addView(col, Ui.flp())
        return root
    }

    private fun image(img: PackImage, w: Int, h: Int): Bitmap? = pack.bitmap(img, w, h)

    private fun shadowed(t: TextView): TextView {
        t.setShadowLayer(Ui.uf(6f), 0f, Ui.uf(1.5f), RatTheme.SHADOW)
        return t
    }

    // ------------------------------------------------------------------ barre du haut

    private fun header(): View {
        val row = Ui.horizontal(c)
        val logo = image(PackImage.LOGO, Ui.u(330), Ui.u(72))
        if (logo != null) {
            // Taille calée sur les proportions de l'image : pas de marge vide à gauche.
            val h = Ui.u(66)
            val w = minOf(Ui.u(330), h * logo.width / maxOf(1, logo.height))
            val v = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_XY; setImageBitmap(logo) }
            row.addView(v, LinearLayout.LayoutParams(w, h))
            RatTheme.clipRound(v, Ui.uf(12f))
        } else row.addView(textLogo(c, 0.8f), Ui.lp(Ui.WRAP, Ui.WRAP))
        // Bulle du lecteur de musique (2026-11-22) : visible seulement quand une autre appli joue.
        musique = MusiqueBulle(c)
        row.addView(musique, LinearLayout.LayoutParams(Ui.u(400), Ui.WRAP).apply { leftMargin = Ui.u(16) })
        row.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))

        // État utile seulement quand il y a quelque chose à dire (couvercle, démo, liaison).
        status = shadowed(Ui.text(c, "", 17f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD))
        status.setPadding(Ui.u(14), Ui.u(6), Ui.u(14), Ui.u(6))
        status.background = Ui.round(RatTheme.CREAM, 30f)
        status.setShadowLayer(0f, 0f, 0f, 0)
        status.visibility = View.GONE
        row.addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, r = 18))

        wifi = RatGlyph(c, RatGlyph.Kind.WIFI, RatTheme.WHITE)
        row.addView(wifi, LinearLayout.LayoutParams(Ui.u(34), Ui.u(34)).apply { rightMargin = Ui.u(20) })
        clock = shadowed(Ui.text(c, "", 30f, RatTheme.WHITE, RatTheme.CONDENSED))
        // Accès caché aux paramètres Android, comme dans Toque : 5 appuis rapides sur l'heure.
        clock.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - adminFirst > 3_000) { adminFirst = now; adminTaps = 0 }
            if (++adminTaps >= 5) { adminTaps = 0; a.openAndroidSettings() }
        }
        row.addView(clock, Ui.lp(Ui.WRAP, Ui.WRAP, r = 14))
        // Minuteurs de cuisine (2026-11-22) : icône, et le temps du plus proche quand l'un tourne.
        minuteurTexte = shadowed(Ui.text(c, "", 22f, RatTheme.WHITE, RatTheme.CONDENSED_BOLD))
        minuteurTexte.visibility = View.GONE
        minuteurTexte.setOnClickListener { a.push(MinuteursScreen(a)) }
        row.addView(minuteurTexte, Ui.lp(Ui.WRAP, Ui.WRAP, r = 2))
        val timer = Icon(c, Icon.Kind.TIMER, RatTheme.WHITE)
        timer.setPadding(Ui.u(9), Ui.u(9), Ui.u(9), Ui.u(9))
        timer.background = Ui.pressable(Ui.round(0, 28f), 28f)
        timer.setOnClickListener { a.push(MinuteursScreen(a)) }
        row.addView(timer, LinearLayout.LayoutParams(Ui.u(54), Ui.u(54)).apply { rightMargin = Ui.u(4) })
        // Balance en accès direct (R2 : elle n'était plus sur l'accueil depuis R1).
        val scale = Icon(c, Icon.Kind.SCALE, RatTheme.WHITE)
        scale.setPadding(Ui.u(9), Ui.u(9), Ui.u(9), Ui.u(9))
        scale.background = Ui.pressable(Ui.round(0, 28f), 28f)
        scale.setOnClickListener { a.push(ScaleScreen(a)) }
        row.addView(scale, LinearLayout.LayoutParams(Ui.u(54), Ui.u(54)).apply { rightMargin = Ui.u(4) })
        val gear = Icon(c, Icon.Kind.SETTINGS, RatTheme.WHITE)
        gear.setPadding(Ui.u(8), Ui.u(8), Ui.u(8), Ui.u(8))
        gear.background = Ui.pressable(Ui.round(0, 28f), 28f)
        gear.setOnClickListener { a.push(SettingsScreen(a)) }
        row.addView(gear, LinearLayout.LayoutParams(Ui.u(54), Ui.u(54)))
        return row
    }

    // ------------------------------------------------------------------ rat et bulle

    private fun ratColumn(): View {
        val frame = FrameLayout(c)
        val rat = firstImage(moment.rat, Ui.u(330), Ui.u(470))
        if (rat != null) {
            val v = ImageView(c).apply {
                scaleType = ImageView.ScaleType.FIT_END
                setImageBitmap(rat)
            }
            ratImage = v
            frame.addView(v, Ui.flp(Ui.MATCH, Ui.MATCH))
            RatTheme.clipRound(v, Ui.uf(26f))
        } else {
            val hat = RatGlyph(c, RatGlyph.Kind.CHEF_HAT, RatTheme.CREAM)
            frame.addView(hat, Ui.flp(Ui.u(190), Ui.u(190), Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.u(70) })
        }

        val bubble = Ui.vertical(c)
        bubble.setPadding(Ui.u(22), Ui.u(16), Ui.u(20), Ui.u(18))
        bubble.background = Ui.pressable(Ui.round(RatTheme.CREAM, 26f, RatTheme.CREAM_DARK, 2f), 26f, 0x22000000)
        val top = Ui.horizontal(c)
        hello = Ui.text(c, "", 30f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD)
        top.addView(hello, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        top.addView(Icon(c, Icon.Kind.BOOK, RatTheme.BROWN), LinearLayout.LayoutParams(Ui.u(30), Ui.u(30)))
        bubble.addView(top)
        // Bulle du rat = bouton « Cuisiner avec l'IA » (2026-11-22, EX-70).
        bubbleText = Ui.text(c, "", 21f, RatTheme.BROWN, RatTheme.CONDENSED).apply { setLineSpacing(0f, 1.1f) }
        bubble.addView(bubbleText, Ui.lp(t = 6))
        // Recette guidée en cours : lien séparé pour la reprendre (comme la carte de Toque).
        resumeLink = Ui.text(c, "", 18f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD).apply {
            setPadding(0, Ui.u(8), 0, Ui.u(2))
            isClickable = true
            setOnClickListener { resume() }
            visibility = View.GONE
        }
        bubble.addView(resumeLink)
        bubble.isClickable = true
        bubble.setOnClickListener { a.push(IaScreen(a)) }
        frame.addView(bubble, Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM))
        return frame
    }

    /** Une recette guidée en cours ? La bulle propose de la reprendre (comme la carte de Toque). */
    private fun resume() {
        if (resumeId.isEmpty()) return
        val r = a.app.recipes.get(resumeId)
        if (r != null) a.push(CookScreen(a, r, a.prefs.guidedStep)) else { a.prefs.guidedRecipe = ""; refreshBubble() }
    }

    // ------------------------------------------------------------------ tuiles

    private fun tiles(): View {
        val grid = Ui.vertical(c)
        val all = Home.Tile.values()
        for (r in 0 until 2) {
            val row = Ui.horizontal(c)
            for (i in 0 until 3) {
                val t = all[r * 3 + i]
                row.addView(tile(t), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < 2) rightMargin = Ui.u(16) })
            }
            grid.addView(row, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { if (r == 0) bottomMargin = Ui.u(16) })
        }
        return grid
    }

    private fun tile(t: Home.Tile): View {
        val card = Ui.vertical(c)
        card.background = Ui.pressable(Ui.round(RatTheme.CREAM, 24f), 24f, 0x33000000)
        val pic = image(t.image, Ui.u(220), Ui.u(160))
        if (pic != null) {
            val v = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(pic) }
            card.addView(v, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { setMargins(Ui.u(8), Ui.u(8), Ui.u(8), 0) })
            RatTheme.clipRound(v, Ui.uf(18f))
        } else {
            val box = FrameLayout(c)
            box.background = Ui.round(RatTheme.CREAM_DARK, 18f)
            box.addView(Icon(c, neutral(t), RatTheme.BROWN_SOFT), Ui.flp(Ui.u(64), Ui.u(64), Gravity.CENTER))
            card.addView(box, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { setMargins(Ui.u(8), Ui.u(8), Ui.u(8), 0) })
        }
        val label = Ui.horizontal(c, Gravity.CENTER)
        label.setPadding(Ui.u(12), 0, Ui.u(10), 0)
        // « Cuisson vapeur » est plus long : police un peu plus petite pour qu'il tienne en entier.
        val name = tr(t.label)
        label.addView(Ui.ellipsize(Ui.text(c, name, if (name.length > 10) 23f else 27f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD)).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        label.addView(Icon(c, Icon.Kind.CHEVRON, RatTheme.BROWN), LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)))
        card.addView(label, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(54)))
        card.isClickable = true
        card.setOnClickListener { FonctionScreen.open(a, t.fonction) }
        return card
    }

    private fun neutral(t: Home.Tile): Icon.Kind = when (t) {
        Home.Tile.HACHER -> Icon.Kind.SPEED
        Home.Tile.MIXER -> Icon.Kind.WHISK
        Home.Tile.CUIRE -> Icon.Kind.FLAME
        Home.Tile.MIJOTER -> Icon.Kind.POT
        Home.Tile.PETRIR -> Icon.Kind.KNEAD
        Home.Tile.VAPEUR -> Icon.Kind.STEAM
    }

    // ------------------------------------------------------------------ boutons du bas

    private fun buttons(): View {
        val row = Ui.horizontal(c)
        val all = Home.Button.values()
        for ((i, b) in all.withIndex()) {
            val cell = Ui.vertical(c, Gravity.CENTER)
            cell.background = Ui.pressable(Ui.round(RatTheme.CREAM, 22f), 22f, 0x33000000)
            cell.addView(buttonIcon(b), LinearLayout.LayoutParams(Ui.u(38), Ui.u(38)))
            cell.addView(Ui.ellipsize(Ui.text(c, tr(b.label), 18f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD)), Ui.lp(Ui.WRAP, Ui.WRAP, t = 6))
            cell.isClickable = true
            cell.setOnClickListener { open(b) }
            row.addView(cell, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < all.size - 1) rightMargin = Ui.u(12) })
        }
        return row
    }

    private fun buttonIcon(b: Home.Button): View = when (b) {
        Home.Button.FONCTIONS -> Icon(c, Icon.Kind.POT, RatTheme.BROWN)
        Home.Button.RECETTES -> RatGlyph(c, RatGlyph.Kind.CHEF_HAT, RatTheme.BROWN)
        Home.Button.FAVORIS -> Icon(c, Icon.Kind.STAR_FILLED, RatTheme.BROWN)
        Home.Button.NETTOYAGE -> RatGlyph(c, RatGlyph.Kind.DROP, RatTheme.WATER)
        Home.Button.REGLAGES -> Icon(c, Icon.Kind.SETTINGS, RatTheme.BROWN)
        Home.Button.APPLICATIONS -> Icon(c, Icon.Kind.APPS, RatTheme.BROWN)
    }

    private fun open(b: Home.Button) = when (b) {
        Home.Button.FONCTIONS -> a.push(FonctionsScreen(a))
        Home.Button.RECETTES -> a.push(RecipesScreen(a))
        Home.Button.FAVORIS -> a.push(RecipesScreen(a, favorites = true))
        Home.Button.NETTOYAGE -> a.push(CleaningScreen(a))
        Home.Button.REGLAGES -> a.push(SettingsScreen(a))
        Home.Button.APPLICATIONS -> a.push(AppsScreen(a))
    }

    // ------------------------------------------------------------------ mises à jour

    private fun refreshClock() {
        clock.text = MainActivity.now()
        wifi.set(if (wifiConnected()) RatGlyph.Kind.WIFI else RatGlyph.Kind.WIFI_OFF)
        wifi.alpha = if (wifiConnected()) 1f else 0.6f
        refreshMoment()
        refreshBubble()
    }

    @Suppress("DEPRECATION")
    private fun wifiConnected(): Boolean = try {
        val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetworkInfo
        n != null && n.isConnected && n.type == ConnectivityManager.TYPE_WIFI
    } catch (_: Exception) {
        false
    }

    private fun refreshBubble() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        hello.text = tr(Home.greeting(hour))
        val guided = a.prefs.guidedRecipe
        val title = if (guided.isEmpty()) null else a.app.recipes.index.entries.firstOrNull { it.id == guided }?.title
        resumeId = if (title != null) guided else ""
        bubbleText.text = tr("Une envie ? Dites-la-moi : l'IA écrit la recette pour le robot.")
        resumeLink.text = if (title != null) "› " + tr("Reprendre « %s » — étape %d ?", title, a.prefs.guidedStep + 1) else ""
        resumeLink.visibility = if (title != null) View.VISIBLE else View.GONE
    }

    override fun onShow() {
        refreshClock()
        unsub = a.app.recipes.onChange { refreshBubble() }
        musique.demarre()
        view.removeCallbacks(tickMinuteur)
        tickMinuteur.run()
        view.removeCallbacks(tick)
        view.postDelayed(tick, 10_000)
    }

    override fun onHide() {
        musique.arrete()
        view.removeCallbacks(tickMinuteur)
        unsub?.invoke()
        view.removeCallbacks(tick)
    }

    override fun onSnapshot(s: Snapshot) {
        val text = when {
            s.hasSample && s.lidOpen -> tr("Couvercle ouvert")
            a.prefs.mode == Prefs.Mode.DEMO -> tr("Démo")
            else -> null
        }
        status.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text != null) status.text = text
    }

    companion object {
        /** Logo dessiné quand le pack n'a pas d'image « logo » (EX-41). */
        fun textLogo(c: Context, size: Float): View {
            val row = Ui.horizontal(c)
            row.addView(RatGlyph(c, RatGlyph.Kind.CHEF_HAT, RatTheme.WHITE),
                LinearLayout.LayoutParams(Ui.u(58 * size), Ui.u(58 * size)).apply { rightMargin = Ui.u(10 * size) })
            val words = Ui.vertical(c)
            words.addView(Ui.text(c, "monsieur", 22f * size, RatTheme.WHITE, Ui.MEDIUM).apply { setShadowLayer(6f, 0f, 2f, RatTheme.SHADOW) })
            words.addView(Ui.text(c, "cuisine", 22f * size, RatTheme.WHITE, Ui.MEDIUM).apply { setShadowLayer(6f, 0f, 2f, RatTheme.SHADOW) })
            words.addView(Ui.text(c, "Édition Ratatouille", 22f * size, RatTheme.CREAM, RatTheme.SCRIPT).apply { setShadowLayer(6f, 0f, 2f, RatTheme.SHADOW) },
                Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
            row.addView(words)
            return row
        }
    }
}
