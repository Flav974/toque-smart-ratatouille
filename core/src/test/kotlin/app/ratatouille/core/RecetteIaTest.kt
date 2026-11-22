package app.ratatouille.core

import app.ratatouille.core.ia.RecetteIa
import app.toque.core.json.Json
import app.toque.core.recipes.StepProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecetteIaTest {

    private fun etape(text: String, machine: Map<String, Any?>? = null, grams: Int? = null) =
        linkedMapOf("text" to text, "machine" to machine, "weighGrams" to grams)

    private fun manuel(s: Int, speed: Int, temp: Int, reverse: Boolean = false) =
        linkedMapOf("program" to "manuel", "seconds" to s, "speed" to speed, "tempC" to temp, "reverse" to reverse, "pulses" to 0)

    private fun recette(vararg steps: Map<String, Any?>) = Json.write(linkedMapOf(
        "title" to "Soupe de potiron",
        "servings" to 4,
        "totalMinutes" to 35,
        "ingredients" to listOf(linkedMapOf("qty" to "800", "unit" to "g", "name" to "potiron")),
        "steps" to steps.toList(),
    ))

    @Test fun acceptsASafeRecipe() {
        val json = recette(
            etape("Mettre l'oignon dans le bol.", grams = 100),
            etape("Hacher l'oignon.", manuel(5, 5, 0)),
            etape("Faire revenir.", manuel(180, 1, 120, reverse = true)),
            etape("Cuire.", manuel(25 * 60, 1, 100, reverse = true)),
            etape("Laisser tiédir 10 min."),
            etape("Mixer.", manuel(60, 8, 0)),
            etape("Pétrir la pâte.", linkedMapOf("program" to "petrir", "seconds" to 90, "speed" to 0, "tempC" to 0, "reverse" to false, "pulses" to 0)),
            etape("Cuire à la vapeur.", manuel(1200, 1, 120)),
            etape("Concasser.", linkedMapOf("program" to "turbo", "seconds" to 0, "speed" to 10, "tempC" to 0, "reverse" to false, "pulses" to 3)),
        )
        val r = RecetteIa.versRecette(json, "ia-1")
        assertTrue((r as? RecetteIa.Resultat.Refus)?.raisons.toString(), r is RecetteIa.Resultat.Ok)
        val rec = (r as RecetteIa.Resultat.Ok).recette
        assertEquals("Soupe de potiron", rec.title)
        assertEquals(9, rec.steps.size)
        assertEquals(7, rec.machineSteps)
        assertEquals(100, rec.steps[0].weigh?.grams)
        assertEquals(StepProgram.TURBO, rec.steps[8].machine?.program)
        assertEquals("IA", rec.category)
    }

    /** EX-73 : vitesse 8 à 100 °C refusée à l'import, avec la raison. */
    @Test fun refusesSpeed8At100C() {
        val r = RecetteIa.versRecette(recette(etape("Mixer chaud.", manuel(60, 8, 100))), "ia-2")
        assertTrue(r is RecetteIa.Resultat.Refus)
        val raisons = (r as RecetteIa.Resultat.Refus).raisons
        assertTrue(raisons.toString(), raisons.any { it.startsWith("1 : ") && it.contains("vitesse 3") })
    }

    @Test fun refusesEveryUnsafeOrImpossibleSetting() {
        val mauvais = listOf(
            manuel(60, 5, 0, reverse = true),        // sens inverse au-dessus de 3
            manuel(60, 2, 42),                       // 42 °C absent de la table
            manuel(60, 11, 0),                       // vitesse hors plage
            manuel(20 * 60, 9, 0),                   // vitesse haute plus de 15 min
            manuel(0, 3, 0),                         // durée nulle
            manuel(60, 0, 0),                        // réglage sans effet
            manuel(100 * 60, 1, 100),                // plus de 99 min
            linkedMapOf("program" to "petrir", "seconds" to 600, "speed" to 0, "tempC" to 0, "reverse" to false, "pulses" to 0),
            linkedMapOf("program" to "turbo", "seconds" to 0, "speed" to 10, "tempC" to 100, "reverse" to false, "pulses" to 2),
            linkedMapOf("program" to "fondue", "seconds" to 60, "speed" to 1, "tempC" to 0, "reverse" to false, "pulses" to 0),
            // Programmes Toque dont les trames ne sont pas celles du Smart : jamais proposés à l'IA.
            linkedMapOf("program" to "vapeur", "seconds" to 1200, "speed" to 1, "tempC" to 120, "reverse" to false, "pulses" to 0),
            linkedMapOf("program" to "rissoler", "seconds" to 300, "speed" to 1, "tempC" to 130, "reverse" to true, "pulses" to 0),
        )
        for (m in mauvais) {
            val r = RecetteIa.versRecette(recette(etape("Étape.", m)), "ia-x")
            assertTrue("aurait dû être refusé : $m", r is RecetteIa.Resultat.Refus)
        }
    }

    /** EX-72 : réponse mal formée refusée, jamais d'exception. */
    @Test fun malformedAnswersAreRefusedWithoutCrash() {
        for (bad in listOf("", "pas du json", "[]", "{}", """{"title":"x","steps":[]}""", """{"title":"x","steps":"a"}""",
            """{"title":"x","steps":[{"text":""}]}""", """{"title":"x","steps":[{"text":"a","machine":{"program":"manuel","speed":"vite"}}]}"""))
            assertTrue(bad, RecetteIa.versRecette(bad, "ia-y") is RecetteIa.Resultat.Refus)
    }

    @Test fun schemaIsStrictAndListsTheRobotPrograms() {
        val s = Json.write(RecetteIa.SCHEMA)
        assertTrue(s.contains("\"additionalProperties\":false"))
        for (p in listOf("manuel", "petrir", "turbo")) assertTrue(p, s.contains("\"$p\""))
        for (p in listOf("vapeur", "rissoler")) assertFalse(p, s.contains("\"$p\""))
    }

    /** EX-71 : la consigne est un fichier texte modifiable, complété par les températures et les modèles. */
    @Test fun instructionsFileIsFilledIn() {
        val f = listOf(File("../app/src/main/assets/ia/consigne.txt"), File("app/src/main/assets/ia/consigne.txt")).first { it.isFile }
        val c = RecetteIa.consigne(f.readText(), emptyList())
        assertFalse(c.contains("{TEMPERATURES}") || c.contains("{PROGRAMMES}"))
        assertTrue(c.contains("37, 45, 50"))
        assertTrue(c.contains("130"))
        val modele = (RecetteIa.versRecette(recette(etape("Hacher.", manuel(5, 5, 0))), "m") as RecetteIa.Resultat.Ok).recette
        val avec = RecetteIa.consigne(f.readText(), listOf(modele))
        assertTrue(avec.contains("\"program\":\"manuel\""))
        // Le modèle relu donne la même recette : même format que la réponse attendue.
        assertTrue(RecetteIa.versRecette(RecetteIa.modele(modele), "m2") is RecetteIa.Resultat.Ok)
    }

    @Test fun fridgeRequestListsTheIngredients() {
        val d = RecetteIa.demandeFrigo("  2 courgettes, 3 œufs ")
        assertTrue(d.contains("2 courgettes, 3 œufs."))
        assertTrue(d.contains("sans rien d'autre à acheter"))
    }
}
