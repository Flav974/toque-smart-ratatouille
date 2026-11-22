package app.ratatouille.musique

import android.app.Notification
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper

/**
 * Sessions média vues dans les notifications de lecture (Spotify, NewPipe…), par le service
 * d'accessibilité « Molette = volume ». Le robot est un appareil « petite mémoire »
 * (`ro.config.low_ram=true`) : Android 8.1 y refuse l'accès aux notifications, donc
 * `MediaSessionManager.getActiveSessions` est impossible. Le jeton de session porté par la
 * notification suffit pour créer un `MediaController`, sans autre autorisation.
 */
object Jetons {
    private val main = Handler(Looper.getMainLooper())
    private val jetons = LinkedHashMap<String, MediaSession.Token>()
    private val ecouteurs = ArrayList<() -> Unit>()

    /** Notification reçue par le service d'accessibilité : garde son jeton de session média, s'il en a un. */
    fun notification(paquet: String, n: Notification) {
        @Suppress("DEPRECATION")
        val t = n.extras?.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION) ?: return
        main.post {
            if (jetons[paquet] == t) return@post
            jetons[paquet] = t
            ecouteurs.toList().forEach { it() }
        }
    }

    /** Session terminée : on oublie son jeton (fil principal). */
    fun retire(paquet: String) {
        if (jetons.remove(paquet) != null) ecouteurs.toList().forEach { it() }
    }

    fun tous(): Map<String, MediaSession.Token> = LinkedHashMap(jetons)

    fun ecoute(f: () -> Unit): () -> Unit {
        ecouteurs += f
        return { ecouteurs -= f }
    }
}
