package app.ratatouille.core.home

import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.ratatouille.core.pack.PackImage
import app.toque.core.i18n.trKey

/**
 * Accueil de l'Édition Ratatouille (phase R1, cahier des charges § 3.2) : ce qui ne dépend pas
 * d'Android, pour être testé sur le PC.
 */
object Home {
    /**
     * Durée de l'écran de démarrage. Le cahier (EX-11) disait « 3 s au plus » ; le 2026-11-21,
     * l'utilisateur l'a trouvé trop court et a demandé 3 à 4 s : durée fixe de 3,5 s.
     * L'accueil se construit dessous pendant ce temps ; un appui ferme l'écran tout de suite.
     */
    const val SPLASH_MS = 3_500L

    /** Faut-il fermer l'écran de démarrage ? Un appui le ferme aussi (géré par l'interface). */
    fun splashDone(elapsedMs: Long): Boolean = elapsedMs >= SPLASH_MS

    /** Première ligne de la bulle du rat, selon l'heure (0 à 23). */
    fun greeting(hour: Int): String = when (hour) {
        in 5..17 -> trKey("Bonjour !")
        else -> trKey("Bonsoir !")
    }

    /** Moment de la journée pour le décor de l'accueil (EX-51, thème jour / nuit automatique). */
    enum class Moment(val decor: List<PackImage>, val rat: List<PackImage>) {
        JOUR(listOf(PackImage.ACCUEIL_FOND), listOf(PackImage.RAT_ACCUEIL)),
        COUCHER(listOf(PackImage.FOND_COUCHER, PackImage.ACCUEIL_FOND), listOf(PackImage.RAT_ACCUEIL)),
        NUIT(listOf(PackImage.FOND_NUIT, PackImage.ACCUEIL_FOND), listOf(PackImage.RAT_VEILLE, PackImage.RAT_ACCUEIL)),
    }

    /** Heure du coucher du soleil à Paris, heure légale arrondie, par mois (janvier = 1). */
    private val COUCHER_H = intArrayOf(17, 18, 19, 20, 21, 21, 21, 20, 19, 18, 17, 17)

    /**
     * Décor selon l'heure (0 à 23) et le mois (1 à 12) : coucher de soleil pendant les deux heures
     * autour du coucher, nuit ensuite et jusqu'à 7 h, jour le reste du temps.
     */
    fun moment(hour: Int, month: Int): Moment {
        val c = COUCHER_H[(month - 1).coerceIn(0, 11)]
        return when {
            hour < 7 || hour > c -> Moment.NUIT
            hour >= c - 1 -> Moment.COUCHER
            else -> Moment.JOUR
        }
    }

    /**
     * Les 6 tuiles, dans l'ordre de l'image de référence 3 (deux rangées de trois). Chacune ouvre le
     * réglage prérempli de sa fonction (phase R2, EX-20) ; Pétrir ouvre Malaxer (ferme, molle, liquide).
     */
    enum class Tile(val label: String, val image: PackImage, val fonction: Fonction) {
        HACHER(trKey("Hacher"), PackImage.TUILE_HACHER, Fonction.HACHER),
        MIXER(trKey("Mixer"), PackImage.TUILE_MIXER, Fonction.MIXER),
        CUIRE(trKey("Cuire"), PackImage.TUILE_CUIRE, Fonction.CUIRE),
        MIJOTER(trKey("Mijoter"), PackImage.TUILE_MIJOTER, Fonction.MIJOTER),
        PETRIR(trKey("Pétrir"), PackImage.TUILE_PETRIR, Fonction.MALAXER_FERME),
        VAPEUR(trKey("Cuisson vapeur"), PackImage.TUILE_VAPEUR, Fonction.VAPEUR),
    }

    /** Boutons du bas : les 4 de l'image 3, plus Applications (EX-13) et Fonctions (R2, EX-21). */
    enum class Button(val label: String) {
        FONCTIONS(trKey("Fonctions")),
        RECETTES(trKey("Mes recettes")),
        FAVORIS(trKey("Favoris")),
        NETTOYAGE(trKey("Nettoyage")),
        REGLAGES(trKey("Réglages")),
        APPLICATIONS(trKey("Applications")),
    }
}
