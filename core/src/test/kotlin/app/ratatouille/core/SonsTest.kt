package app.ratatouille.core

import app.ratatouille.core.sons.Son
import app.toque.core.machine.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonsTest {
    @Test fun defaultsKeepTheToqueBeeps() {
        assertEquals(Son.Bip.DEUX, Son.FINI.defaut)
        assertEquals(Son.Bip.TROIS_GRAVES, Son.ERREUR.defaut)
        assertEquals(Son.Bip.UN, Son.RECETTE.defaut)
        assertEquals(Son.Bip.AUCUN, Son.APPLI.defaut)
    }

    @Test fun machineEventsMapToSounds() {
        assertEquals(Son.FINI, Son.pour(EventType.JOB_FINISHED))
        assertEquals(Son.ERREUR, Son.pour(EventType.FAULT))
        assertEquals(Son.LANCE, Son.pour(EventType.JOB_STARTED))
        assertEquals(Son.COUVERCLE_OUVERT, Son.pour(EventType.LID_OPENED))
        assertNull(Son.pour(EventType.PHASE_CHANGED))
    }

    @Test fun onlyAudioFilesAreAccepted() {
        assertEquals("mp3", Son.extension("Cloche.MP3"))
        assertEquals("ogg", Son.extension("Adara.ogg"))
        for (bad in listOf("photo.jpg", "son", ".mp3", "script.sh", "x.mp3.exe")) assertNull(bad, Son.extension(bad))
        assertEquals("fini.ogg", Son.fichier(Son.FINI, "ogg"))
    }

    @Test fun keysAreUniqueAndSafe() {
        val cles = Son.values().map { it.cle }
        assertEquals(cles.size, cles.toSet().size)
        for (k in cles) assert(k.matches(Regex("[a-z-]+"))) { k }
        assertEquals(Son.IA, Son.of("ia"))
    }
}
