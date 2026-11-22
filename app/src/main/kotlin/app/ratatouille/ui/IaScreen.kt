package app.ratatouille.ui

import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.ratatouille.core.ia.RecetteIa
import app.ratatouille.core.pack.PackImage
import app.ratatouille.core.pack.RatPose
import app.ratatouille.ia.ClaudeRecette
import app.ratatouille.ia.CleStore
import app.ratatouille.pack.PackImages
import app.toque.core.i18n.tr
import app.toque.core.recipes.Recipe
import app.toque.recipes.RecipeRepo
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.screens.RecipeScreen
import app.toque.ui.screens.SettingsScreen

/**
 * « Cuisiner avec l'IA » (EX-70 à EX-77). On écrit son envie ; Claude Haiku écrit une recette au
 * format strict ; [RecetteIa] la contrôle réglage par réglage (SafetyPolicy, SmartCodec). Une recette
 * acceptée s'ouvre dans l'écran de recette habituel : chaque étape y est lancée par l'utilisateur,
 * l'IA ne pilote jamais le moteur. Sans réseau, sans clé ou en cas d'erreur : un message simple.
 */
class IaScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Cuisiner avec l'IA")
    private lateinit var demande: EditText
    private lateinit var go: View
    private lateinit var etat: TextView
    private lateinit var resultat: LinearLayout
    private lateinit var resTitre: TextView
    private lateinit var resResume: TextView
    private lateinit var keep: View
    private var recette: Recipe? = null
    @Volatile private var enCours = false
    private var frigo = false

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(8), Ui.u(24), Ui.u(20))
        val col = Ui.vertical(c)

        val card = Ui.card(c, 24f)
        card.setPadding(Ui.u(24), Ui.u(20), Ui.u(24), Ui.u(20))
        card.addView(Ui.text(c, tr("Que voulez-vous cuisiner ?"), 26f, Ui.TEXT, Ui.MEDIUM))
        card.addView(Ui.text(c, tr("Écrivez votre envie : l'IA vous propose une recette pour ce robot. Vous lancez ensuite chaque étape vous-même."),
            16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 4))
        demande = EditText(c).apply {
            hint = tr("Par exemple : un risotto aux champignons pour 4")
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(21f))
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT3)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            maxLines = 4
            background = Ui.round(Ui.SURFACE2, 16f, Ui.STROKE, 1f)
            setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
        }
        card.addView(demande, Ui.lp(Ui.MATCH, Ui.WRAP, t = 12))
        // Deux façons de demander : une envie, ou ce qu'il reste dans le frigo.
        val modes = Ui.horizontal(c)
        lateinit var envie: TextView
        lateinit var frigoChip: TextView
        fun peint() {
            for ((t, sel) in listOf(envie to !frigo, frigoChip to frigo)) {
                t.setTextColor(if (sel) 0xFF1A0E05.toInt() else Ui.TEXT)
                t.background = Ui.pressable(if (sel) Ui.round(Ui.ACCENT, 40f) else Ui.round(Ui.SURFACE2, 40f, Ui.STROKE, 1f), 40f)
            }
            demande.hint = if (frigo) tr("Par exemple : 2 courgettes, 3 œufs, du parmesan, un reste de riz")
                else tr("Par exemple : un risotto aux champignons pour 4")
        }
        envie = Ui.toggleChip(c, tr("Une envie"), true) { frigo = false; peint() }
        frigoChip = Ui.toggleChip(c, tr("Avec ce qu'il me reste"), false) { frigo = true; peint() }
        modes.addView(envie, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        modes.addView(frigoChip, Ui.lp(Ui.WRAP, Ui.WRAP))
        card.addView(modes, Ui.lp(t = 10))
        val row = Ui.horizontal(c)
        go = Ui.button(c, tr("Écrire la recette"), Ui.Style.PRIMARY, Icon.Kind.BOOK, height = 60) { lance() }
        row.addView(go, Ui.lp(Ui.WRAP, Ui.WRAP, r = 14))
        etat = Ui.text(c, "", 17f, Ui.TEXT2).apply { setLineSpacing(0f, 1.1f) }
        row.addView(etat, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(row, Ui.lp(t = 14))
        col.addView(card)

        resultat = Ui.card(c, 24f)
        resultat.setPadding(Ui.u(24), Ui.u(18), Ui.u(24), Ui.u(18))
        resTitre = Ui.ellipsize(Ui.text(c, "", 24f, Ui.TEXT, Ui.MEDIUM))
        resultat.addView(resTitre)
        resResume = Ui.text(c, "", 16f, Ui.TEXT2)
        resultat.addView(resResume, Ui.lp(t = 4))
        val r2 = Ui.horizontal(c)
        r2.addView(Ui.button(c, tr("Ouvrir la recette"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 56) {
            recette?.let { a.push(RecipeScreen(a, it)) }
        }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        keep = Ui.button(c, tr("Garder dans Mes recettes"), Ui.Style.SECONDARY, Icon.Kind.CHECK, height = 56) { garde() }
        r2.addView(keep)
        resultat.addView(r2, Ui.lp(t = 12))
        resultat.visibility = View.GONE
        col.addView(resultat, Ui.lp(t = 16))

        root.addView(Ui.scroll(c, col), LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        val rat = RatView(c, PackImages(c), Ui.u(300), Ui.u(520))
        rat.show(RatPose.choix(PackImage.RAT_IA))
        root.addView(rat, LinearLayout.LayoutParams(Ui.u(300), Ui.MATCH).apply { leftMargin = Ui.u(16) })
        return root
    }

    override fun onShow() {
        if (!enCours && CleStore(a).cle == null) message(tr("Aucune clé API : ajoutez-la dans Réglages › IA."), true)
    }

    private fun message(t: String, alerte: Boolean = false) {
        etat.text = t
        etat.setTextColor(if (alerte) Ui.WARN else Ui.TEXT2)
    }

    private fun lance() {
        if (enCours) return
        val saisie = demande.text.toString().trim()
        val texte = if (frigo && saisie.isNotEmpty()) RecetteIa.demandeFrigo(saisie) else saisie
        if (texte.isEmpty()) { message(tr("Écrivez d'abord ce que vous voulez cuisiner."), true); return }
        if (Build.VERSION.SDK_INT < 26) { message(tr("Il faut Android 8.0 ou plus pour l'IA."), true); return }
        val cle = CleStore(a).cle
        if (cle == null) {
            message(tr("Aucune clé API : ajoutez-la dans Réglages › IA."), true)
            a.push(SettingsScreen(a))
            return
        }
        (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(demande.windowToken, 0)
        enCours = true
        Ui.setButtonEnabled(go, false)
        resultat.visibility = View.GONE
        message(tr("L'IA écrit la recette… (jusqu'à une minute)"))
        val repo = a.app.recipes
        Thread({
            var ok: Recipe? = null
            var erreur: String? = null
            try {
                val consigne = RecetteIa.consigne(lisConsigne(), modeles(repo, saisie))
                val client = ClaudeRecette(cle)
                val json = try { client.genere(consigne, texte) } finally { client.ferme() }
                when (val r = RecetteIa.versRecette(json, RecipeRepo.IA_PREFIX + System.currentTimeMillis())) {
                    is RecetteIa.Resultat.Ok -> ok = r.recette
                    is RecetteIa.Resultat.Refus -> {
                        a.app.log.write("ia", "recette refusée : " + r.raisons.joinToString(" ; "))
                        erreur = tr("Recette refusée, réglages non sûrs pour ce robot :") + "\n" + r.raisons.take(3).joinToString("\n")
                    }
                }
            } catch (e: ClaudeRecette.Echec) {
                erreur = e.message ?: tr("Erreur inattendue. Réessayez.")
            } catch (e: Exception) {
                a.app.log.write("ia", "erreur : " + e.javaClass.simpleName)
                erreur = tr("Erreur inattendue. Réessayez.")
            }
            a.runOnUiThread {
                enCours = false
                Ui.setButtonEnabled(go, true)
                val r = ok
                if (r != null) montre(r) else message(erreur ?: tr("Erreur inattendue. Réessayez."), true)
            }
        }, "ia-recette").start()
    }

    private fun montre(r: Recipe) {
        recette = r
        message(tr("Recette prête, contrôlée réglage par réglage."))
        a.son(app.ratatouille.core.sons.Son.IA)
        resTitre.text = r.title
        resResume.text = tr("%d portions · %d étapes, dont %d au robot", r.servings, r.steps.size, r.machineSteps)
        Ui.setButtonEnabled(keep, true)
        resultat.visibility = View.VISIBLE
    }

    private fun garde() {
        val r = recette ?: return
        a.app.recipes.upsertAll(listOf(r))
        Ui.setButtonEnabled(keep, false)
        a.toast(tr("Recette gardée dans Mes recettes."))
    }

    private fun lisConsigne(): String = a.assets.open("ia/consigne.txt").use { String(it.readBytes(), Charsets.UTF_8) }

    /** Deux recettes Lidl proches de la demande, sûres, comme modèles (EX-71). */
    private fun modeles(repo: RecipeRepo, texte: String): List<Recipe> = try {
        repo.index.search(texte, limit = 30).asSequence()
            .mapNotNull { repo.get(it.entry.id) }
            .filter { RecetteIa.modeleUtilisable(it) }
            .take(2).toList()
    } catch (_: Exception) {
        emptyList()
    }
}
