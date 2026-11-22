package app.ratatouille.core

import app.ratatouille.core.ia.CleApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleApiTest {
    // Fausse clé de test, de la bonne forme ; aucune vraie clé dans le dépôt.
    private val fausse = "sk-ant-api03-" + "x".repeat(80) + "AB12"

    @Test fun acceptsAPastedKeyWithSpaces() {
        val r = CleApi.nettoie("  ${fausse.substring(0, 30)}\n${fausse.substring(30)}  ")
        assertTrue(r is CleApi.Resultat.Ok)
        assertEquals(fausse, (r as CleApi.Resultat.Ok).cle)
    }

    @Test fun refusesWrongShapes() {
        for (bad in listOf("", "   ", "sk-proj-" + "x".repeat(60), "sk-ant-court", "sk-ant-" + "é".repeat(50), "sk-ant-" + "x".repeat(400)))
            assertTrue(bad, CleApi.nettoie(bad) is CleApi.Resultat.Refus)
    }

    @Test fun neverShowsTheWholeKey() {
        assertEquals("sk-ant-…AB12", CleApi.masque(fausse))
        val ok = CleApi.nettoie(fausse)
        assertFalse(ok.toString().contains("xxxx"))
        assertTrue(ok.toString().contains("AB12"))
    }
}
