package app.ratatouille.core.minuteur

/**
 * Minuteur de cuisine, indépendant du robot (four, repos d'une pâte…). Aucune trame, aucun moteur :
 * seulement un décompte. Le temps est donné par l'appelant ([maintenant], en ms) pour être testable.
 */
class Minuteur(val numero: Int) {
    /** Durée réglée (ms). */
    var dureeMs: Long = 0
        private set
    /** Fin prévue quand il tourne, sinon null. */
    private var finMs: Long? = null
    /** Reste quand il est en pause (ou réglé et pas encore lancé). */
    private var resteMs: Long = 0
    /** Sonnerie déjà donnée pour ce décompte. */
    var sonne = false
        private set

    val tourne: Boolean get() = finMs != null

    fun restant(maintenant: Long): Long = finMs?.let { maxOf(0L, it - maintenant) } ?: resteMs

    fun fini(maintenant: Long): Boolean = tourne && restant(maintenant) == 0L

    /** Ajoute (ou retire, si négatif) du temps ; possible en marche comme à l'arrêt. */
    fun ajoute(ms: Long, maintenant: Long) {
        val f = finMs
        if (f != null) {
            finMs = maxOf(maintenant, f + ms)
        } else {
            resteMs = (resteMs + ms).coerceIn(0L, MAX_MS)
            dureeMs = resteMs
        }
        sonne = false
    }

    fun regle(ms: Long) {
        finMs = null
        resteMs = ms.coerceIn(0L, MAX_MS)
        dureeMs = resteMs
        sonne = false
    }

    /** Lance ou reprend. Sans temps réglé, ne fait rien. */
    fun lance(maintenant: Long): Boolean {
        if (tourne || resteMs <= 0) return false
        finMs = maintenant + resteMs
        sonne = false
        return true
    }

    fun pause(maintenant: Long) {
        val f = finMs ?: return
        resteMs = maxOf(0L, f - maintenant)
        finMs = null
    }

    /** Remet le temps réglé, à l'arrêt. */
    fun remet() {
        finMs = null
        resteMs = dureeMs
        sonne = false
    }

    /** À appeler à chaque seconde : vrai une seule fois, quand il faut sonner. */
    fun aSonner(maintenant: Long): Boolean {
        if (!fini(maintenant) || sonne) return false
        sonne = true
        return true
    }

    /** La sonnerie est vue : le minuteur revient à son temps réglé, arrêté. */
    fun arrete() = remet()

    companion object {
        const val MAX_MS = 12 * 3600_000L

        /** « 1:05:09 », « 4:32 », « 0:07 ». */
        fun texte(ms: Long): String {
            val s = (ms + 999) / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }
}
