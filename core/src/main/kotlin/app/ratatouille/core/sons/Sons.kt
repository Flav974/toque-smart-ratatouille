package app.ratatouille.core.sons

import app.toque.core.i18n.trKey
import app.toque.core.machine.EventType

/**
 * Sons de l'Édition Ratatouille (demande de l'utilisateur du 2026-11-22, voir ratatouille/SONS.md).
 * Chaque son peut être remplacé par un fichier choisi dans Réglages › Sons ; sans fichier, il garde
 * son son par défaut (bips de Toque, ou rien).
 */
enum class Son(val cle: String, val label: String, val quand: String, val defaut: Bip) {
    APPLI("appli", trKey("Démarrage de l'appli"), trKey("à l'ouverture de l'accueil, au démarrage du robot"), Bip.AUCUN),
    LANCE("lance", trKey("Programme lancé"), trKey("le moteur démarre"), Bip.AUCUN),
    FINI("fini", trKey("Programme terminé"), trKey("fin d'une fonction ou d'une cuisson"), Bip.DEUX),
    RECETTE("recette", trKey("Recette terminée"), trKey("dernière étape d'une recette guidée"), Bip.UN),
    ERREUR("erreur", trKey("Erreur"), trKey("faute de la machine"), Bip.TROIS_GRAVES),
    AVERTISSEMENT("avertissement", trKey("Avertissement"), trKey("réglage refusé, pause imprévue"), Bip.AUCUN),
    COUVERCLE_OUVERT("couvercle-ouvert", trKey("Couvercle ouvert"), trKey("capteur du couvercle"), Bip.AUCUN),
    COUVERCLE_FERME("couvercle-ferme", trKey("Couvercle fermé"), trKey("capteur du couvercle"), Bip.AUCUN),
    IA("ia", trKey("Recette IA prête"), trKey("« Cuisiner avec l'IA » a fini"), Bip.AUCUN),
    MINUTEUR("minuteur", trKey("Minuteur terminé"), trKey("un minuteur de cuisine arrive à zéro"), Bip.DEUX);

    /** Bips du système (ToneGenerator) joués quand aucun fichier n'est choisi. */
    enum class Bip(val fois: Int, val grave: Boolean) {
        AUCUN(0, false), UN(1, false), DEUX(2, false), TROIS_GRAVES(3, true)
    }

    companion object {
        /** Formats acceptés pour un fichier son. */
        val EXTENSIONS = listOf("mp3", "ogg", "wav", "m4a", "aac", "flac")

        /** Au-delà, le fichier est refusé : un son d'alerte reste court. */
        const val MAX_OCTETS = 3L * 1024 * 1024

        fun of(cle: String?): Son? = values().firstOrNull { it.cle == cle }

        /** Son à jouer pour un évènement de la machine (null : aucun). */
        fun pour(type: EventType): Son? = when (type) {
            EventType.JOB_STARTED -> LANCE
            EventType.JOB_FINISHED -> FINI
            EventType.FAULT -> ERREUR
            EventType.LID_OPENED -> COUVERCLE_OUVERT
            EventType.LID_CLOSED -> COUVERCLE_FERME
            else -> null
        }

        /** Extension d'un fichier son acceptable (minuscules), sinon null. */
        fun extension(nom: String): String? =
            nom.substringAfterLast('.', "").lowercase().takeIf { it in EXTENSIONS && nom.length > it.length + 1 }

        /** Nom du fichier copié dans le dossier privé de l'appli pour ce son. */
        fun fichier(son: Son, ext: String): String = "${son.cle}.$ext"
    }
}
