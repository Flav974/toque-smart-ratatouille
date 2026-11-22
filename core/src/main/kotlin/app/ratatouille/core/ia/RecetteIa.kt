package app.ratatouille.core.ia

import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.machine.Accessory
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Operation
import app.toque.core.protocol.SmartCodec
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.Weigh

/**
 * « Cuisiner avec l'IA » (EX-70 à EX-73) : format strict de la réponse de Claude, et passage de
 * cette réponse au modèle de recette de Toque.
 *
 * Sécurité (EX-73) : l'IA ne pilote jamais le moteur. Elle propose une recette ; chaque réglage
 * machine passe ici par les mêmes contrôles que le reste de l'appli : [StepMachine.violations]
 * (`SafetyPolicy`) puis `SmartCodec.accepts` pour chaque phase. Un seul réglage refusé, et toute la
 * recette est refusée, avec la raison : rien n'est corrigé en silence. La recette acceptée se suit
 * ensuite dans l'écran de recette habituel, où chaque étape est lancée par l'utilisateur.
 */
object RecetteIa {

    const val MAX_ETAPES = 40
    const val MAX_INGREDIENTS = 40

    /**
     * Programmes qu'une étape IA peut demander (clés de [StepProgram]). Pas « vapeur » ni
     * « rissoler » : leurs réglages Toque (vitesse 0) sont refusés par `SmartCodec` ; la cuisson
     * vapeur et le rissolage se font en « manuel » (120 ou 130 °C, vitesse 1).
     */
    val PROGRAMMES: List<String> = listOf(StepProgram.MANUAL, StepProgram.KNEAD, StepProgram.TURBO).map { it.key }

    /** Températures du robot en °C (table de l'appli d'origine), 0 = sans chauffe. */
    val TEMPERATURES: List<Int> = Mcc.TEMPERATURE_LEVELS.toList()

    private fun nullable(schema: Map<String, Any?>): Map<String, Any?> =
        linkedMapOf("anyOf" to listOf(schema, linkedMapOf("type" to "null")))

    private fun objet(vararg props: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(
        "type" to "object",
        "properties" to linkedMapOf(*props),
        "required" to props.map { it.first },
        "additionalProperties" to false,
    )

    /** Schéma JSON de la sortie structurée (`output_config.format`). */
    val SCHEMA: Map<String, Any?> = objet(
        "title" to linkedMapOf("type" to "string"),
        "servings" to linkedMapOf("type" to "integer"),
        "totalMinutes" to linkedMapOf("type" to "integer"),
        "ingredients" to linkedMapOf(
            "type" to "array",
            "items" to objet(
                "qty" to linkedMapOf("type" to "string"),
                "unit" to linkedMapOf("type" to "string"),
                "name" to linkedMapOf("type" to "string"),
            ),
        ),
        "steps" to linkedMapOf(
            "type" to "array",
            "items" to objet(
                "text" to linkedMapOf("type" to "string"),
                "machine" to nullable(objet(
                    "program" to linkedMapOf("type" to "string", "enum" to PROGRAMMES),
                    "seconds" to linkedMapOf("type" to "integer"),
                    "speed" to linkedMapOf("type" to "integer"),
                    "tempC" to linkedMapOf("type" to "integer"),
                    "reverse" to linkedMapOf("type" to "boolean"),
                    "pulses" to linkedMapOf("type" to "integer"),
                )),
                "weighGrams" to nullable(linkedMapOf("type" to "integer")),
            ),
        ),
    )

    sealed class Resultat {
        class Ok(val recette: Recipe) : Resultat()
        /** [raisons] : textes pour l'utilisateur, déjà traduits. */
        class Refus(val raisons: List<String>) : Resultat()
    }

    private val codec = SmartCodec()

    /**
     * Réponse JSON de Claude -> recette, ou refus motivé. Ne lève jamais d'exception : une réponse
     * mal formée est un refus (EX-72).
     */
    fun versRecette(json: String, id: String): Resultat {
        val m = try {
            Json.parse(json).obj()
        } catch (_: Exception) {
            return Resultat.Refus(listOf(tr("Réponse de l'IA illisible.")))
        }
        return try {
            lis(m, id)
        } catch (_: Exception) {
            Resultat.Refus(listOf(tr("Réponse de l'IA illisible.")))
        }
    }

    private fun lis(m: Map<String, Any?>, id: String): Resultat {
        val titre = m.str("title")?.trim().orEmpty()
        if (titre.isEmpty()) return Resultat.Refus(listOf(tr("La recette de l'IA n'a pas de titre.")))
        val ingredients = m["ingredients"].list().map { it.obj() }.mapNotNull { i ->
            val name = i.str("name")?.trim().orEmpty()
            if (name.isEmpty()) null else Ingredient(i.str("qty")?.trim().orEmpty(), i.str("unit")?.trim().orEmpty(), name)
        }
        val brutes = m["steps"].list().map { it.obj() }
        if (brutes.isEmpty()) return Resultat.Refus(listOf(tr("La recette de l'IA n'a aucune étape.")))
        if (brutes.size > MAX_ETAPES || ingredients.size > MAX_INGREDIENTS)
            return Resultat.Refus(listOf(tr("La recette de l'IA est trop longue.")))

        val raisons = ArrayList<String>()
        val etapes = ArrayList<Step>()
        for ((n, s) in brutes.withIndex()) {
            val texte = s.str("text")?.trim().orEmpty()
            if (texte.isEmpty()) { raisons += "${n + 1} : " + tr("étape sans texte"); continue }
            val machine = (s["machine"] as? Map<*, *>)?.let { mc ->
                val r = reglage(mc.obj())
                if (r == null) { raisons += "${n + 1} : " + tr("programme inconnu"); null } else r
            }
            if (machine != null) for (x in controle(machine)) raisons += "${n + 1} : $x"
            val g = s.int("weighGrams")
            val pesee = if (g != null && g > 0) Weigh(texte.take(40), g) else null
            etapes += Step(texte, machine, pesee)
        }
        if (raisons.isNotEmpty()) return Resultat.Refus(raisons)

        return Resultat.Ok(Recipe(
            id = id,
            title = titre,
            category = "IA",
            tags = listOf("IA"),
            servings = (m.int("servings") ?: 0).coerceIn(0, 50),
            totalMinutes = (m.int("totalMinutes") ?: 0).coerceIn(0, 24 * 60),
            ingredients = if (ingredients.isEmpty()) emptyList() else listOf(IngredientGroup("", ingredients)),
            steps = etapes,
            source = RecipeSource.USER,
            author = "Claude (IA)",
        ))
    }

    private fun reglage(mc: Map<String, Any?>): StepMachine? {
        val program = StepProgram.of(mc.str("program"))?.takeIf { it.key in PROGRAMMES } ?: return null
        return StepMachine(
            program = program,
            seconds = mc.int("seconds") ?: 0,
            speed = mc.int("speed") ?: 0,
            tempC = mc.int("tempC") ?: 0,
            reverse = mc.bool("reverse") ?: false,
            accessory = Accessory.BLADE,
            pulses = mc.int("pulses") ?: 0,
        )
    }

    /** Raisons de refuser un réglage proposé par l'IA (vide = accepté). */
    fun controle(m: StepMachine): List<String> {
        val v = ArrayList<String>()
        if (m.seconds < 0 || m.pulses < 0) v += tr("valeur négative")
        for (x in m.violations()) if (x.message !in v) v += x.message
        if (v.isNotEmpty()) return v
        if (m.program == StepProgram.TURBO) {
            if (m.tempC != 0 || m.speed !in listOf(0, 10)) v += tr("Turbo : vitesse 10, sans chauffe")
            else if (!codec.accepts(Operation.TURBO, 10, 0, Direction.CLOCKWISE)) v += tr("réglage refusé par le robot")
            return v
        }
        val job = m.toJob("IA") ?: return listOf(tr("réglage refusé par le robot"))
        for (p in job.phases) {
            val s = p.setting
            if (s.isRest) continue
            if (!codec.accepts(s.operation, s.speed, s.temperatureLevel, s.direction, s.program, s.smartLevel)) {
                v += tr("réglage refusé par le robot")
                break
            }
        }
        return v
    }

    /**
     * Une recette Lidl peut-elle servir de modèle ? Seulement si elle a des étapes machine et que
     * toutes passent les mêmes contrôles qu'une réponse de l'IA : pas de mauvais exemple.
     */
    fun modeleUtilisable(r: Recipe): Boolean =
        r.machineSteps > 0 && r.steps.size <= MAX_ETAPES &&
            r.steps.all { s -> s.machine == null || (s.machine!!.program.key in PROGRAMMES && controle(s.machine!!).isEmpty()) }

    /**
     * « Avec ce qu'il me reste » : la demande devient une liste d'ingrédients disponibles. Les
     * placards de base (sel, poivre, huile, beurre, eau, sucre, farine) sont supposés présents.
     */
    fun demandeFrigo(ingredients: String): String =
        "Je n'ai que ces ingrédients : ${ingredients.trim()}. J'ai aussi du sel, du poivre, de l'huile, du beurre, " +
            "de l'eau, du sucre et de la farine. Propose une recette pour le robot qui utilise surtout ces " +
            "ingrédients, sans rien d'autre à acheter. Tu peux ne pas tous les utiliser."

    /** Une recette en JSON compact, comme modèle dans la consigne (EX-71). */
    fun modele(r: Recipe): String = Json.write(linkedMapOf(
        "title" to r.title,
        "servings" to r.servings,
        "totalMinutes" to r.totalMinutes,
        "ingredients" to r.allIngredients.map { linkedMapOf("qty" to it.qty, "unit" to it.unit, "name" to it.name) },
        "steps" to r.steps.map { s ->
            linkedMapOf(
                "text" to s.text,
                "machine" to s.machine?.let { mc ->
                    linkedMapOf("program" to mc.program.key, "seconds" to mc.seconds, "speed" to mc.speed,
                        "tempC" to mc.tempC, "reverse" to mc.reverse, "pulses" to mc.pulses)
                },
                "weighGrams" to s.weigh?.grams,
            )
        },
    ))

    /**
     * Consigne complète : le texte modifiable (`assets/ia/consigne.txt`), où `{TEMPERATURES}` et
     * `{PROGRAMMES}` sont remplacés, puis les recettes Lidl modèles.
     */
    fun consigne(texte: String, modeles: List<Recipe>): String = buildString {
        append(texte.replace("{TEMPERATURES}", TEMPERATURES.filter { it > 0 }.joinToString(", "))
            .replace("{PROGRAMMES}", PROGRAMMES.joinToString(", ")))
        if (modeles.isNotEmpty()) {
            append("\n\n# Recettes du robot à prendre comme modèles (même format que ta réponse)\n")
            for (r in modeles) append('\n').append(modele(r)).append('\n')
        }
    }
}
