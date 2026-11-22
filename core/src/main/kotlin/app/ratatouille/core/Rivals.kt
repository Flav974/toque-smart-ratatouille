package app.ratatouille.core

/**
 * Édition Ratatouille : une seule appli pilote le port série à la fois (cahier des charges, EX-02).
 *
 * Toque Smart (`app.toque.smart`) peut rester installé à côté. Son pont série tue tout pont
 * « cat /dev/ttyMT1 » qu'il trouve en ouvrant le port, et le nôtre ferait pareil. Pour éviter que
 * les deux applis se volent le port, on **refuse** d'ouvrir le port tant que le processus machine
 * de Toque tourne en mode « Machine réelle ». On ne tue jamais Toque : il cuit peut-être quelque chose.
 *
 * Logique pure, testable sans Android. La commande est lancée en root par l'appli.
 */
object Rivals {
    const val TOQUE_SMART = "app.toque.smart"

    private const val PID = "PID="
    private const val PREFS = "PREFS="

    /** Commande root : pid du processus machine du rival, puis son mode enregistré (fichier de réglages de Toque). */
    fun probeCommand(pkg: String): String {
        require(pkg.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "nom de paquet invalide : $pkg" }
        val prefs = "/data/data/$pkg/shared_prefs/toque.xml"
        return "echo \"$PID\$(pidof $pkg:machine)\"; " +
            "if [ -f $prefs ]; then echo \"$PREFS\$(grep -o '<string name=\"mode\">[a-z]*</string>' $prefs)\"; fi"
    }

    /**
     * Motif de refus, ou `null` si le port peut être ouvert.
     * Prudent : une vérification illisible (root refusé, commande absente) est un refus.
     */
    fun refusal(pkg: String, probeOutput: String): String? {
        val lines = probeOutput.lines().map { it.trim() }
        val pidLine = lines.firstOrNull { it.startsWith(PID) }
            ?: return "Vérification impossible : $pkg pilote peut-être déjà le moteur"
        val pids = pidLine.substring(PID.length).trim()
        if (pids.isEmpty()) return null // le processus machine du rival ne tourne pas
        if (!pids.split(' ').all { p -> p.isNotEmpty() && p.all { it.isDigit() } })
            return "Vérification impossible : $pkg pilote peut-être déjà le moteur"
        val prefsLine = lines.firstOrNull { it.startsWith(PREFS) } ?: return null // jamais réglé : mode Démo
        val mode = Regex("<string name=\"mode\">([a-z]*)</string>").find(prefsLine)?.groupValues?.get(1)
        if (mode == null) return null // clé absente : mode Démo par défaut dans Toque
        return if (mode == "hardware") "$pkg pilote déjà le moteur : arrêtez-le d'abord" else null
    }
}
