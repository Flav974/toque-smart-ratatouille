package app.ratatouille.core

import app.ratatouille.core.minuteur.Minuteur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteurTest {
    @Test fun countsDownAndRingsOnce() {
        val m = Minuteur(1)
        m.regle(60_000)
        assertTrue(m.lance(1_000))
        assertEquals(30_000, m.restant(31_000))
        assertFalse(m.aSonner(31_000))
        assertTrue(m.aSonner(61_000))
        assertFalse(m.aSonner(62_000))
        m.arrete()
        assertFalse(m.tourne)
        assertEquals(60_000, m.restant(99_000))
    }

    @Test fun pauseAndResume() {
        val m = Minuteur(1)
        m.regle(10_000)
        m.lance(0)
        m.pause(4_000)
        assertEquals(6_000, m.restant(50_000))
        m.lance(50_000)
        assertEquals(1_000, m.restant(55_000))
    }

    @Test fun addTimeWhileRunningOrStopped() {
        val m = Minuteur(2)
        m.ajoute(5 * 60_000L, 0)
        assertEquals(300_000, m.dureeMs)
        m.lance(0)
        m.ajoute(60_000, 10_000)
        assertEquals(350_000, m.restant(10_000))
        m.ajoute(-10 * 60_000L, 10_000)
        assertEquals(0, m.restant(10_000))
        assertFalse(Minuteur(3).lance(0))
    }

    @Test fun text() {
        assertEquals("4:32", Minuteur.texte(272_000))
        assertEquals("0:07", Minuteur.texte(6_200))
        assertEquals("1:05:09", Minuteur.texte(3_909_000))
        assertEquals("0:00", Minuteur.texte(0))
    }
}
