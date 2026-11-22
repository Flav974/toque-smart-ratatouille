package app.ratatouille.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.ratatouille.core.cuisine.Fonctions
import app.ratatouille.core.cuisine.Fonctions.Etat
import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.ratatouille.core.cuisine.Fonctions.Sorte
import app.ratatouille.core.pack.RatPose
import app.ratatouille.pack.PackImages
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.Snapshot
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.ValueCard
import app.toque.ui.screens.ManualScreen
import app.toque.ui.screens.ScaleScreen

/**
 * Réglage prérempli d'une fonction de l'appli d'origine (phase R2, EX-20 et EX-21) : durée,
 * température, vitesse et sens déjà proposés. Pour un programme automatique, seule la durée se
 * règle (la trame est celle de la capture) ; la cuisson personnalisée passe par [ManualScreen].
 * Une fonction pas encore capturée affiche ses réglages d'origine et pourquoi elle est refusée.
 */
class FonctionScreen(a: MainActivity, private var f: Fonction) : Screen(a) {
    override val title = tr(f.label)

    private var seconds = f.dureeProposee
    private lateinit var timeCard: ValueCard
    private lateinit var tempCard: ValueCard
    private lateinit var speedCard: ValueCard
    private lateinit var variants: LinearLayout
    private lateinit var origin: TextView
    private lateinit var hint: TextView
    private lateinit var startBtn: LinearLayout
    private var turbo: HoldButton? = null
    private lateinit var rat: RatView
    private lateinit var bubble: View

    override fun build(): View {
        // Disposition R3 (demande de l'utilisateur) : cartes compactes en haut, puis une bande
        // horizontale (ce que fait l'appli d'origine, Démarrer) ; à droite, la bulle et le rat.
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(20), Ui.u(8), Ui.u(12), Ui.u(12))
        val left = Ui.vertical(c)
        variants = Ui.horizontal(c)
        left.addView(variants, Ui.lp(Ui.MATCH, Ui.WRAP, b = 14))
        val cards = Ui.horizontal(c, Gravity.TOP)
        timeCard = ValueCard(c, tr("Durée"), Icon.Kind.CLOCK)
        tempCard = ValueCard(c, tr("Température"), Icon.Kind.THERMO)
        speedCard = ValueCard(c, tr("Vitesse"), Icon.Kind.SPEED)
        timeCard.onMinus = { bump(-1) }
        timeCard.onPlus = { bump(1) }
        for ((i, card) in listOf(timeCard, tempCard, speedCard).withIndex())
            cards.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < 2) rightMargin = Ui.u(14) })
        timeCard.select(true)
        tempCard.setEditable(false)
        speedCard.setEditable(false)
        // Les cartes prennent la hauteur libre : les commandes vont jusqu'en bas de l'écran.
        left.addView(cards, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val strip = Ui.horizontal(c)
        strip.background = Ui.round(Ui.SURFACE, 26f, Ui.STROKE, 1f)
        strip.setPadding(Ui.u(18), Ui.u(12), Ui.u(14), Ui.u(12))
        origin = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.12f) }
        strip.addView(origin, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(14) })
        val actions = Ui.vertical(c)
        startBtn = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 80, textSize = 24f) { start() }
        actions.addView(startBtn, Ui.lp(Ui.MATCH, Ui.WRAP))
        if (f.sorte == Sorte.TURBO) {
            startBtn.visibility = View.GONE
            val t = HoldButton(c, tr("Maintenir pour Turbo"), onDown = { a.turboDown(Accessory.BLADE) }, onUp = { a.machine.turboUp() })
            turbo = t
            actions.addView(t, Ui.lp(Ui.MATCH, Ui.u(80)))
        }
        // Mijoter, Fermenter : en attendant la capture, la cuisson personnalisée reste possible.
        if (f.etat == Etat.A_CAPTURER && f.celsius > 0 && f.groupe != Fonctions.Groupe.NETTOYER) {
            val alt = Ui.button(c, tr("Cuisson personnalisée à %d °C", f.celsius), Ui.Style.SECONDARY, Icon.Kind.THERMO, height = 64) {
                a.push(ManualScreen(a, app.toque.core.recipes.StepMachine(
                    seconds = 30 * 60, speed = f.speed, tempC = f.celsius, reverse = f.reverse)))
            }
            actions.addView(alt, Ui.lp(Ui.MATCH, Ui.WRAP, t = 10))
        }
        strip.addView(actions, Ui.lp(Ui.u(260), Ui.WRAP))
        left.addView(strip, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(10) })

        // Colonne de droite (R3) : la bulle du rat (conseils, alertes), puis le rat qui fait l'action.
        val right = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        hint = Ui.text(c, "", 16f, Ui.WARN).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.12f) }
        bubble = hint.also {
            it.setPadding(Ui.u(14), Ui.u(10), Ui.u(14), Ui.u(10))
            it.background = Ui.round(Ui.SURFACE, 22f, Ui.STROKE, 1.5f)
        }
        right.addView(bubble, Ui.lp(Ui.MATCH, Ui.WRAP))
        rat = RatView(c, PackImages(c), Ui.u(220), Ui.u(380))
        right.addView(rat, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(8) })
        root.addView(right, LinearLayout.LayoutParams(Ui.u(225), Ui.MATCH))
        refresh()
        return root
    }

    private fun bump(dir: Int) {
        if (!f.dureeReglable) return
        val step = when {
            dir > 0 && seconds < 60 || dir < 0 && seconds <= 60 -> 5
            dir > 0 && seconds < 600 || dir < 0 && seconds <= 600 -> 30
            dir > 0 && seconds < 3600 || dir < 0 && seconds <= 3600 -> 60
            else -> 15 * 60
        }
        seconds = (seconds + dir * step).coerceIn(maxOf(1, f.minS), f.maxEffectifS)
        refresh()
    }

    private fun select(v: Fonction) {
        f = v
        seconds = v.dureeProposee
        refresh()
    }

    private fun refresh() {
        variants.removeAllViews()
        val list = Fonctions.variantes(f)
        variants.visibility = if (list.size > 1) View.VISIBLE else View.GONE
        for ((i, v) in list.withIndex())
            variants.addView(Ui.toggleChip(c, tr(v.label), v == f) { select(v) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = if (i < list.size - 1) 10 else 0))

        val noTime = f.sorte == Sorte.TURBO
        val (clock, unit) = Fonctions.horloge(seconds)
        timeCard.set(if (noTime) "${f.maxS}" else clock, if (noTime) "s" else unit,
            if (noTime) tr("par appui") else if (f.dureeReglable) tr("de %s à %s", Fonctions.texteDuree(maxOf(1, f.minS)), Fonctions.texteDuree(f.maxEffectifS)) else "")
        timeCard.setEditable(f.dureeReglable && f.etat == Etat.PRETE)
        if (f.celsius == 0) tempCard.set("—", "", tr("Sans chauffe"), Ui.TEXT2)
        else tempCard.set("${f.celsius}", "°C", tr("Réglée par le programme"), Ui.tempColor(f.celsius))
        speedCard.set("${f.speed}", "", (if (f.reverse) tr("Sens inverse") else tr("Sens normal")) + " · " + tr("fixe"))
        origin.text = tr(f.origine)

        rat.follow(RatPose.pourFonction(f), a.snapshot)
        hint.setTextColor(if (f.etat == Etat.PRETE) Ui.TEXT2 else Ui.WARN)
        hint.text = when (f.etat) {
            Etat.PRETE -> if (f.speed >= 4 && f.sorte != Sorte.TURBO) tr("Vitesse %d : la confirmation « contenu chaud » sera demandée (ce robot ne mesure pas toujours la température).", f.speed) else ""
            Etat.A_CAPTURER -> tr(f.raison)
        }
        bubble.visibility = if (hint.text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        Ui.setButtonEnabled(startBtn, f.demarrable && !a.snapshot.busy)
        (startBtn.getChildAt(1) as TextView).text = when (f.etat) {
            Etat.PRETE -> tr("Démarrer")
            Etat.A_CAPTURER -> tr("À capturer d'abord")
        }
    }

    private fun start() {
        if (!f.demarrable || a.snapshot.busy) return
        val job = Fonctions.job(f, seconds) ?: return
        a.startJob(job)
    }

    override fun onSnapshot(s: Snapshot) {
        rat.follow(RatPose.pourFonction(f), s)
        Ui.setButtonEnabled(startBtn, f.demarrable && !s.busy)
        turbo?.let {
            it.isEnabled = !s.busy || s.turboActive
            it.alpha = if (it.isEnabled) 1f else 0.35f
        }
    }

    override fun onKnob(delta: Int): Boolean {
        bump(delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        start()
        return true
    }

    companion object {
        /** Ouvre l'écran d'une fonction : cuisson personnalisée, balance, ou réglage prérempli. */
        fun open(a: MainActivity, f: Fonction) {
            when {
                f.sorte == Sorte.BALANCE -> a.push(ScaleScreen(a))
                f.toutReglable -> a.push(ManualScreen(a, fonction = f))
                else -> a.push(FonctionScreen(a, f))
            }
        }
    }
}
