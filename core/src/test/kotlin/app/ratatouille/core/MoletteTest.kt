package app.ratatouille.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoletteTest {
    private val moi = "app.ratatouille.smart"

    @Test fun inOtherAppsTheKnobChangesTheVolume() {
        // relevé sur le robot : droite = F6 (monter), gauche = F7 (baisser)
        assertEquals(1, Molette.volume(Molette.F6, "org.schabi.newpipe", moi))
        assertEquals(-1, Molette.volume(Molette.F7, "org.schabi.newpipe", moi))
    }

    @Test fun inRatatouilleTheKnobKeepsItsRole() {
        assertEquals(0, Molette.volume(Molette.F6, moi, moi))
        assertEquals(0, Molette.volume(Molette.F7, moi, moi))
        assertEquals(0, Molette.volume(Molette.F7, null, moi))
        assertEquals(0, Molette.volume(Molette.F7, "com.android.systemui", moi))
    }

    @Test fun inOtherAppsThePressMutes() {
        assertEquals(Molette.MUET, Molette.volume(Molette.F5, "org.schabi.newpipe", moi))
        assertEquals(0, Molette.volume(Molette.F5, moi, moi)) // dans Ratatouille : Turbo, validation
        assertEquals(Molette.MUET, Molette.volume(23, "x", moi, appui = 23))
        assertEquals(0, Molette.volume(Molette.F5, "x", moi, appui = 23))
    }

    @Test fun otherKeysAreNeverTaken() {
        for (k in listOf(66, 23, 4, 24, 25)) assertEquals(0, Molette.volume(k, "org.schabi.newpipe", moi))
    }

    @Test fun calibratedKeysWin() {
        assertEquals(-1, Molette.volume(21, "x", moi, gauche = 21, droite = 22))
        assertEquals(1, Molette.volume(22, "x", moi, gauche = 21, droite = 22))
        assertEquals(0, Molette.volume(Molette.F6, "x", moi, gauche = 21, droite = 22))
    }
}
