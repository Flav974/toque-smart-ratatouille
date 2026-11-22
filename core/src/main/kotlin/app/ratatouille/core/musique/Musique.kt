package app.ratatouille.core.musique

/**
 * Bulle du lecteur de musique de l'accueil (demande de l'utilisateur du 2026-11-22) : quelle
 * session média montrer, et quel texte. Les sessions viennent de `MediaSessionManager`.
 */
object Musique {

    /** Une session média d'une autre appli (Spotify, NewPipe, VLC…). */
    data class Session(
        val paquet: String,
        val joue: Boolean,
        val titre: String?,
        val artiste: String?,
        /** Dernier changement d'état connu (ms), pour départager. */
        val majMs: Long = 0,
    )

    /**
     * La session à montrer : d'abord celle qui joue, sinon la dernière en pause qui a un titre.
     * Jamais l'appli elle-même ([moi]). Null : pas de bulle.
     */
    fun choisit(sessions: List<Session>, moi: String): Session? =
        sessions.asSequence()
            .filter { it.paquet != moi && (it.joue || !it.titre.isNullOrBlank()) }
            .sortedWith(compareByDescending<Session> { it.joue }.thenByDescending { it.majMs })
            .firstOrNull()

    /**
     * Sessions lues dans `dumpsys media_session` (par root). Sur le robot « petite mémoire », c'est le
     * seul moyen : Android 8.1 y refuse l'accès aux notifications. La description du morceau est
     * « titre, sous-titre, description » (titre, artiste, album) : titre avant la première virgule,
     * album après la dernière, artiste entre les deux.
     */
    fun depuisDumpsys(texte: String): List<Session> {
        val out = ArrayList<Session>()
        var paquet: String? = null
        var joue = false
        var titre: String? = null
        var artiste: String? = null
        var maj = 0L
        fun fin() {
            paquet?.let { out += Session(it, joue, titre, artiste, maj) }
            paquet = null; joue = false; titre = null; artiste = null; maj = 0
        }
        for (brut in texte.lineSequence()) {
            val l = brut.trim()
            when {
                l.startsWith("package=") -> { fin(); paquet = l.removePrefix("package=").trim().ifEmpty { null } }
                paquet == null -> {}
                l.startsWith("state=PlaybackState") -> {
                    joue = Regex("""\{state=(\d+)""").find(l)?.groupValues?.get(1) == "3"
                    maj = Regex("""updated=(\d+)""").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                }
                l.startsWith("metadata:") -> {
                    val d = l.substringAfter("description=", "").trim()
                    if (d.isNotEmpty() && d != "null") {
                        val parts = d.split(", ")
                        titre = parts.first().takeIf { it.isNotBlank() && it != "null" }
                        artiste = if (parts.size >= 3) parts.subList(1, parts.size - 1).joinToString(", ").takeIf { it != "null" }
                            else parts.getOrNull(1)?.takeIf { it != "null" }
                    }
                }
            }
        }
        fin()
        return out
    }

    /** « Titre — Artiste », ou [nomAppli] si le morceau n'a pas de titre. */
    fun texte(s: Session, nomAppli: String): String {
        val t = s.titre?.trim().orEmpty()
        val a = s.artiste?.trim().orEmpty()
        return when {
            t.isNotEmpty() && a.isNotEmpty() -> "$t — $a"
            t.isNotEmpty() -> t
            else -> nomAppli
        }
    }
}
