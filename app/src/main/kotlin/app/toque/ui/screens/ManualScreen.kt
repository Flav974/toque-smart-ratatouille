package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.Accessory
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobState
import app.toque.core.machine.Limits
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Snapshot
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Smart
import app.ratatouille.core.cuisine.Fonctions
import app.ratatouille.core.pack.PackImage
import app.ratatouille.core.pack.RatPose
import app.ratatouille.pack.PackImages
import app.ratatouille.ui.RatView
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.TempRing
import app.toque.ui.Ui
import app.toque.ui.ValueCard

/**
 * Cuisson manuelle. Les réglages incompatibles sont corrigés à la saisie (vitesse limitée en
 * chauffe, en sens inverse ou avec le fouet) plutôt que refusés au démarrage.
 *
 * Édition Ratatouille (R2) : les 21 niveaux de température du Smart, 40 °C compris
 * ([smartTemp], index de [Smart.TEMPERATURE_LEVELS]) ; une [fonction] de l'accueil (Hacher, Cuire,
 * Cuisson personnalisée) préremplit l'écran.
 */
class ManualScreen(
    a: MainActivity,
    preset: app.toque.core.recipes.StepMachine? = null,
    private val fonction: Fonctions.Fonction? = null,
) : Screen(a) {
    override val title = if (fonction != null) tr(fonction.label) else JobKind.MANUAL.label

    private var seconds = fonction?.seconds ?: preset?.seconds?.takeIf { it > 0 } ?: 60
    /** Niveau de la table du Smart (0..20). */
    private var smartTemp = fonction?.smartLevel
        ?: preset?.let { p ->
            // Étape de recette : la température exacte si le Smart l'a (40 °C), sinon celle de Toque.
            val exact = Smart.TEMPERATURE_LEVELS.indexOf(p.tempC)
            if (p.tempC > 0 && exact > 0) exact
            else Smart.TEMPERATURE_LEVELS.indexOf(Mcc.celsiusForLevel(p.temperatureLevel)).coerceAtLeast(0)
        } ?: 0
    /** Niveau de Toque correspondant (règles de sécurité, réglage en direct). */
    private val tempLevel: Int get() = Smart.toqueLevelFor(smartTemp)
    private var speed = fonction?.speed?.coerceIn(1, 10) ?: preset?.speed?.coerceIn(1, 10) ?: 2
    private var reverse = fonction?.reverse ?: preset?.reverse ?: false
    private var accessory = preset?.accessory ?: Accessory.BLADE
    private var selected = 0

    private lateinit var timeCard: ValueCard
    private lateinit var tempCard: ValueCard
    private lateinit var speedCard: ValueCard
    private lateinit var dirRow: LinearLayout
    private lateinit var accRow: LinearLayout
    private lateinit var ring: TempRing
    private lateinit var weight: TextView
    private lateinit var startBtn: LinearLayout
    private lateinit var hint: TextView
    private lateinit var turbo: HoldButton
    private lateinit var rat: RatView
    /** Rat de la fonction ouverte ; sinon d'après le réglage (vitesse, chauffe). */
    private val ratAction: PackImage
        get() = fonction?.let { RatPose.pourFonction(it) }
            ?: RatPose.pourEtape(app.toque.core.recipes.StepMachine(seconds = seconds, speed = speed,
                tempC = if (smartTemp > 0) Smart.celsiusForLevel(smartTemp) else 0))
    private var live = false
    private var wasLive = false
    private var pendingEdit = false
    private var timeEdited = false
    private var configuredSeconds = seconds
    private val pushLive = Runnable { sendAdjust() }

    override fun build(): View {
        // Disposition R3 (demande de l'utilisateur) : cartes compactes en haut, puis une bande
        // horizontale (température mesurée, balance, Démarrer), puis le sens et l'accessoire ;
        // le rat occupe la colonne de droite, comme dans les autres écrans.
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(20), Ui.u(8), Ui.u(12), Ui.u(12))
        val left = Ui.vertical(c)
        val cards = Ui.horizontal(c, Gravity.TOP)
        timeCard = ValueCard(c, tr("Durée"), Icon.Kind.CLOCK)
        tempCard = ValueCard(c, tr("Température"), Icon.Kind.THERMO)
        speedCard = ValueCard(c, tr("Vitesse"), Icon.Kind.SPEED)
        val all = listOf(timeCard, tempCard, speedCard)
        for ((i, card) in all.withIndex()) {
            card.onSelect = { selected = i; refresh() }
            card.onMinus = { change(i, -1) }
            card.onPlus = { change(i, 1) }
            cards.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < 2) rightMargin = Ui.u(14) })
        }
        // Les cartes prennent la hauteur libre : les commandes vont jusqu'en bas de l'écran.
        left.addView(cards, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val strip = Ui.horizontal(c)
        strip.background = Ui.round(Ui.SURFACE, 26f, Ui.STROKE, 1f)
        strip.setPadding(Ui.u(12), Ui.u(10), Ui.u(14), Ui.u(10))
        ring = TempRing(c)
        strip.addView(ring, LinearLayout.LayoutParams(Ui.u(124), Ui.u(124)))
        val info = Ui.vertical(c)
        weight = Ui.text(c, "", 18f, Ui.TEXT2)
        info.addView(weight)
        hint = Ui.text(c, "", 16f, Ui.WARN).apply { setLineSpacing(0f, 1.1f) }
        info.addView(hint, Ui.lp(t = 6))
        strip.addView(info, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(14); rightMargin = Ui.u(12) })
        startBtn = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 80, textSize = 24f) { start() }
        strip.addView(startBtn, Ui.lp(Ui.u(240), Ui.WRAP))
        left.addView(strip, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))

        val options = Ui.horizontal(c)
        dirRow = Ui.horizontal(c)
        accRow = Ui.horizontal(c)
        options.addView(dirRow)
        options.addView(Ui.spacer(c, w = 18))
        options.addView(accRow)
        options.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))
        turbo = HoldButton(c, "Turbo", onDown = {
            a.turboDown(accessory)
        }, onUp = { a.machine.turboUp() })
        options.addView(turbo, Ui.lp(Ui.WRAP, Ui.u(64)))
        left.addView(options, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(10) })

        // Édition Ratatouille (R3) : le rat qui fait l'action, en pied dans la colonne de droite.
        rat = RatView(c, PackImages(c), Ui.u(220), Ui.u(400))
        root.addView(rat, LinearLayout.LayoutParams(Ui.u(215), Ui.MATCH))
        refresh()
        return root
    }

    /** Niveau du Smart à joindre au réglage, seulement s'il manque à la table de Toque (40 °C). */
    private val smartLevelOrNull: Int?
        get() = if (Mcc.celsiusForLevel(tempLevel) == Smart.celsiusForLevel(smartTemp)) null else smartTemp

    private fun nextSeconds(s: Int, dir: Int): Int {
        val step = when {
            dir > 0 && s < 60 || dir < 0 && s <= 60 -> 5
            dir > 0 && s < 600 || dir < 0 && s <= 600 -> 30
            else -> 60
        }
        val max = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        return (s + dir * step).coerceIn(5, max)
    }

    private fun change(card: Int, dir: Int) {
        val s = a.snapshot
        when (card) {
            0 -> seconds = nextSeconds(seconds, dir)
            1 -> {
                smartTemp = (smartTemp + dir).coerceIn(0, Smart.TEMPERATURE_LEVELS.size - 1)
                if (tempLevel > 0 && speed > Limits.MAX_SPEED_HEATED) {
                    speed = Limits.MAX_SPEED_HEATED
                    a.toast(tr("En chauffe, vitesse %d maximum", Limits.MAX_SPEED_HEATED))
                }
            }
            2 -> {
                var max = 10
                var why = trKey("Vitesse %d maximum")
                if (tempLevel > 0) { max = Limits.MAX_SPEED_HEATED; why = trKey("En chauffe, vitesse %d maximum") }
                if (reverse && max > Limits.MAX_SPEED_REVERSE) { max = Limits.MAX_SPEED_REVERSE; why = trKey("En sens inverse, vitesse %d maximum") }
                if (accessory.maxSpeed < max) { max = accessory.maxSpeed; why = trKey("Avec le fouet, vitesse %d maximum") }
                val next = (speed + dir).coerceIn(1, 10)
                if (next > max) a.toast(tr(why, max)) else speed = next
            }
        }
        val maxS = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        if (seconds > maxS) seconds = maxS
        refresh()
        if (live && s.kind?.name == "MANUAL") {
            if (card == 0) timeEdited = true
            pendingEdit = true
            view.removeCallbacks(pushLive)
            view.postDelayed(pushLive, 350)
        }
    }

    private fun setReverse(r: Boolean) {
        if (r && speed > Limits.MAX_SPEED_REVERSE) {
            speed = Limits.MAX_SPEED_REVERSE
            a.toast(tr("En sens inverse, vitesse %d maximum", Limits.MAX_SPEED_REVERSE))
        }
        reverse = r
        refresh()
        if (live) { pendingEdit = true; view.removeCallbacks(pushLive); view.postDelayed(pushLive, 200) }
    }

    private fun setAccessory(acc: Accessory) {
        if (live) return
        accessory = acc
        if (speed > acc.maxSpeed) {
            speed = acc.maxSpeed
            a.toast(tr("%s : vitesse %d maximum", acc.label, acc.maxSpeed))
        }
        refresh()
    }

    private fun spec(hot: Boolean = false) = Programs.manual(
        speed, tempLevel, if (reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE,
        seconds * 1000L, accessory, hot, label = title, smartLevel = smartLevelOrNull,
    )

    private fun start() {
        val s = a.snapshot
        if (s.state == JobState.PAUSED && s.kind?.name == "MANUAL") {
            a.machine.resume(s.hotBlendConfirmed) { a.refusal(it) }
            return
        }
        configuredSeconds = seconds
        a.startJob(spec())
    }

    private fun sendAdjust() {
        pendingEdit = false
        val s = a.snapshot
        if (!s.jobActive || s.kind?.name != "MANUAL") return
        // La durée n'est renvoyée que si elle a été modifiée : changer la vitesse ne relance pas le minuteur.
        val total: Long? = if (timeEdited) s.elapsedMs + seconds * 1000L else null
        timeEdited = false
        a.machine.adjust(speed, tempLevel, reverse, total, s.hotBlendConfirmed, smartLevelOrNull) { v ->
            if (!v.ok && v.has(SafetyPolicy.HOT_BLEND)) {
                a.confirm(a.hotTitle(), a.bowlTemperatureText() + " " + tr("Passer à la vitesse %d ?", speed), tr("Confirmer"), icon = Icon.Kind.THERMO) {
                    a.machine.adjust(speed, tempLevel, reverse, total, true, smartLevelOrNull) { a.refusal(it) }
                }
            } else a.refusal(v)
        }
    }

    private fun refresh() {
        if (::rat.isInitialized) rat.follow(ratAction, a.snapshot)
        val maxS = if (speed > 6) "max 15 min" else "max 99 min"
        timeCard.set(Ui.clock(seconds * 1000L), "", maxS)
        if (smartTemp == 0) tempCard.set("—", "", tr("Sans chauffe"), Ui.TEXT2)
        else {
            val t = Smart.celsiusForLevel(smartTemp)
            tempCard.set("$t", "°C", if (t >= 100) tr("Ébullition") else if (t >= 60) tr("Cuisson") else tr("Tiédir"), Ui.tempColor(t))
        }
        speedCard.set("$speed", "", when (speed) {
            1 -> tr("Mijoter")
            2, 3 -> tr("Mélanger")
            in 4..6 -> tr("Hacher")
            else -> tr("Mixer")
        } + if (reverse) " · " + tr("inverse") else "")
        timeCard.select(selected == 0)
        tempCard.select(selected == 1)
        speedCard.select(selected == 2)

        dirRow.removeAllViews()
        dirRow.addView(Ui.toggleChip(c, tr("Sens normal"), !reverse) { setReverse(false) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        dirRow.addView(Ui.toggleChip(c, tr("Inverse"), reverse) { setReverse(true) })
        accRow.removeAllViews()
        accRow.addView(Ui.toggleChip(c, Accessory.BLADE.label, accessory == Accessory.BLADE) { setAccessory(Accessory.BLADE) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        accRow.addView(Ui.toggleChip(c, Accessory.WHISK.label, accessory == Accessory.WHISK) { setAccessory(Accessory.WHISK) })

        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = v.firstOrNull()?.message ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
        Ui.setButtonEnabled(startBtn, v.isEmpty())
    }

    override fun onSnapshot(s: Snapshot) {
        rat.follow(ratAction, s)
        ring.set(if (s.hasSample) s.actualTempC else 0, if (s.jobActive && s.jobTempC > 0) s.jobTempC else
            if (smartTemp > 0) Smart.celsiusForLevel(smartTemp) else 0, s.speedLevel)
        weight.text = if (s.hasSample) tr("Balance : %d g", s.weightG) else tr("Base non connectée")
        val manualJob = s.kind?.name == "MANUAL" && (s.state == JobState.RUNNING || s.state == JobState.STARTING || s.state == JobState.PAUSED)
        live = manualJob && s.state != JobState.PAUSED
        tempCard.repeatEnabled = !live
        speedCard.repeatEnabled = !live
        if (live && !pendingEdit) {
            // Pendant la cuisson, la carte Durée affiche le temps restant.
            val remaining = ((s.remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
            if (remaining != seconds) {
                seconds = remaining
                timeCard.set(Ui.clock(seconds * 1000L), "", tr("restant"))
            }
        }
        if (wasLive && !live && !manualJob) {
            seconds = configuredSeconds
            refresh()
        }
        wasLive = live
        val busy = s.busy
        startBtn.visibility = if (busy && s.state != JobState.PAUSED) View.GONE else View.VISIBLE
        (startBtn.getChildAt(1) as TextView).text = if (s.state == JobState.PAUSED && manualJob) tr("Reprendre") else tr("Démarrer")
        turbo.isEnabled = !busy || s.turboActive
        turbo.alpha = if (turbo.isEnabled) 1f else 0.35f
        if (live) hint.text = tr("Réglages appliqués en direct")
        else if (!busy) refreshHintOnly()
    }

    private fun refreshHintOnly() {
        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = v.firstOrNull()?.message ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
    }

    override fun onKnob(delta: Int): Boolean {
        change(selected, delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        val s = a.snapshot
        when {
            s.state == JobState.RUNNING -> a.machine.pause { a.refusal(it) }
            else -> start()
        }
        return true
    }
}
