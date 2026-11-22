package app.ratatouille.core

import app.ratatouille.core.musique.Musique
import app.ratatouille.core.musique.Musique.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusiqueTest {
    private val moi = "app.ratatouille.smart"

    @Test fun playingSessionWins() {
        val s = listOf(
            Session("org.schabi.newpipe", false, "Vieille vidéo", null, majMs = 50),
            Session("com.spotify.music", true, "Titre", "Artiste", majMs = 10),
        )
        assertEquals("com.spotify.music", Musique.choisit(s, moi)?.paquet)
    }

    @Test fun otherwiseLatestPausedWithATitle() {
        val s = listOf(
            Session("org.videolan.vlc", false, "A", null, majMs = 10),
            Session("com.spotify.music", false, "B", null, majMs = 20),
            Session("net.oqee.androidmobile", false, null, null, majMs = 30),
        )
        assertEquals("com.spotify.music", Musique.choisit(s, moi)?.paquet)
    }

    @Test fun neverItselfAndNothingWhenEmpty() {
        assertNull(Musique.choisit(listOf(Session(moi, true, "x", null)), moi))
        assertNull(Musique.choisit(emptyList(), moi))
    }

    /** Extrait de `dumpsys media_session` relevé sur le robot (Android 8.1), titre changé. */
    private val dump = """
        |  Media button session is com.spotify.music/spotify-media-session (userId=0)
        |  Sessions Stack - have 2 sessions:
        |    spotify-media-session com.spotify.music/spotify-media-session (userId=0)
        |      package=com.spotify.music
        |      active=true
        |      state=PlaybackState {state=3, position=430, buffered position=0, speed=1.0, updated=48176871, actions=142259}
        |      metadata:size=18, description=Chanson, Artiste A, Artiste B, Album
        |    play_movies_media com.google.android.videos/play_movies_media (userId=0)
        |      package=com.google.android.videos
        |      state=null
        |      metadata: null
        |
        |Audio playback (lastly played comes first)
        |  uid=10054 packages=com.spotify.music
    """.trimMargin()

    @Test fun parsesDumpsys() {
        val s = Musique.depuisDumpsys(dump)
        assertEquals(2, s.size)
        val sp = s[0]
        assertEquals("com.spotify.music", sp.paquet)
        assertEquals(true, sp.joue)
        assertEquals("Chanson", sp.titre)
        assertEquals("Artiste A, Artiste B", sp.artiste)
        assertEquals(48176871L, sp.majMs)
        assertEquals(false, s[1].joue)
        assertNull(s[1].titre)
        assertEquals("com.spotify.music", Musique.choisit(s, moi)?.paquet)
        assertEquals(emptyList<Musique.Session>(), Musique.depuisDumpsys("rien"))
    }

    @Test fun text() {
        assertEquals("Titre — Artiste", Musique.texte(Session("p", true, "Titre", " Artiste "), "Spotify"))
        assertEquals("Titre", Musique.texte(Session("p", true, "Titre", ""), "Spotify"))
        assertEquals("Spotify", Musique.texte(Session("p", true, null, null), "Spotify"))
    }
}
