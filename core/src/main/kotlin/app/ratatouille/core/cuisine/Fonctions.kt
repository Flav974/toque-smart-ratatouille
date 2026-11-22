package app.ratatouille.core.cuisine

import app.toque.core.i18n.trKey
import app.toque.core.machine.JobSpec
import app.toque.core.machine.Programs
import app.toque.core.protocol.Direction
import app.toque.core.protocol.NETTOYAGE_MAX_S
import app.toque.core.protocol.Smart
import app.toque.core.protocol.SmartProgram

/**
 * Fonctions de cuisine de l'Édition Ratatouille (phase R2, EX-20 à EX-22) : toutes celles de
 * l'appli d'origine du Smart, avec ses réglages par défaut (code `b3.d1` de
 * com.tecpal.device.mc30 2.21.0.0), et ce que l'écran de réglage laisse modifier.
 *
 * Chaque fonction qui démarre un programme envoie seulement des trames capturées avec l'appli
 * d'origine (`captures5/`) : vérifié octet pour octet par `FonctionsTest`. Une fonction sans
 * capture complète a l'état [Etat.A_CAPTURER] (jamais de trame inventée). Décongélation et
 * Maintien au chaud, absentes de l'appli d'origine (rien à capturer), ont été retirées le 2026-11-22.
 */
object Fonctions {

    enum class Etat { PRETE, A_CAPTURER }

    /** Comment la fonction démarre. */
    enum class Sorte {
        /** Cuisson personnalisée préremplie : tout se règle (écran manuel). */
        MANUEL,
        /** Programme automatique du Smart : une trame capturée, seule la durée se règle. */
        PROGRAMME,
        /** Malaxer : fonction 5, vitesse 4, sans chauffe, au rythme de l'appli d'origine ; seule la durée se règle. */
        MALAXER,
        /** Programme à étapes de l'appli d'origine (mijoter, fermenter, nettoyage) : trames capturées. */
        ETAPES,
        /** Cuisson vapeur : fonction 3, 120 °C, vitesse 1. */
        VAPEUR,
        /** Saisir : fonction 4, 130 °C, 5 s vitesse 1 inverse / 30 s repos. */
        SAISIR,
        /** Turbo : trame fixe tant qu'on appuie. */
        TURBO,
        /** Balance : tare et pesée, sans moteur. */
        BALANCE,
        /** Pas encore utilisable : voir [Fonction.raison]. */
        AUCUNE,
    }

    /** Groupes de l'écran « Toutes les fonctions », dans l'ordre d'affichage. */
    enum class Groupe(val label: String) {
        PREPARER(trKey("Préparer")),
        CUIRE(trKey("Cuire")),
        AUTOMATIQUE(trKey("Programmes")),
        OUTILS(trKey("Outils")),
        NETTOYER(trKey("Nettoyer")),
    }

    /**
     * Une fonction et son réglage prérempli. [celsius] = température affichée (0 sans chauffe),
     * [smartLevel] = niveau de la table du Smart envoyé en cuisson personnalisée.
     * [origine] dit d'où viennent les valeurs : appli d'origine, ou proposition (pas d'équivalent).
     */
    enum class Fonction(
        val label: String,
        val groupe: Groupe,
        val sorte: Sorte,
        val etat: Etat,
        val seconds: Int,
        val minS: Int,
        val maxS: Int,
        val speed: Int,
        val celsius: Int,
        val reverse: Boolean,
        val origine: String,
        val program: SmartProgram? = null,
        /** Pourquoi la fonction n'est pas encore utilisable (état [Etat.A_CAPTURER]). */
        val raison: String = "",
    ) {
        // ------------------------------------------------------------ préparer
        HACHER(trKey("Hacher"), Groupe.PREPARER, Sorte.MANUEL, Etat.PRETE, 10, 1, 15 * 60, 5, 0, false,
            trKey("Proposition (l'appli d'origine n'a pas de bouton Hacher) : cuisson personnalisée, vitesse 5, 10 s.")),
        MIXER(trKey("Mixer"), Groupe.PREPARER, Sorte.PROGRAMME, Etat.PRETE, 30, 30, 120, 8, 0, false,
            trKey("Appli d'origine : Mixer, vitesse 8, 30 s (2 min maximum)."), SmartProgram.MIXER),
        SMOOTHIE(trKey("Smoothie"), Groupe.PREPARER, Sorte.PROGRAMME, Etat.PRETE, 60, 30, 120, 10, 0, false,
            trKey("Appli d'origine : Smoothie, vitesse 10, 1 min (2 min maximum)."), SmartProgram.SMOOTHIE),
        TRANCHEUSE(trKey("Trancheuse-râpe"), Groupe.PREPARER, Sorte.MANUEL, Etat.PRETE, 30, 1, 5 * 60, 5, 0, false,
            trKey("Appli d'origine : Trancheuse-râpe, vitesse 5, 30 s (5 min maximum)."), raison = ""),
        MALAXER_FERME(trKey("Malaxer : pâte ferme"), Groupe.PREPARER, Sorte.MALAXER, Etat.PRETE, 120, 30, 120, 4, 0, false,
            trKey("Appli d'origine : Malaxer pâte ferme, vitesse 4, 2 min : 45 s, pause, 20 s en sens inverse, pause, 20 s, pause. 30 min d'attente avant le pétrissage suivant.")),
        MALAXER_MOLLE(trKey("Malaxer : pâte molle"), Groupe.PREPARER, Sorte.MALAXER, Etat.PRETE, 120, 30, 120, 4, 0, true,
            trKey("Appli d'origine : Malaxer pâte molle, vitesse 4, 2 min : 15 s en sens inverse, 35 s de pause, 70 s en sens normal. 30 min d'attente avant le pétrissage suivant.")),
        MALAXER_LIQUIDE(trKey("Malaxer : pâte liquide"), Groupe.PREPARER, Sorte.MALAXER, Etat.PRETE, 120, 30, 120, 4, 0, false,
            trKey("Appli d'origine : Malaxer pâte liquide, vitesse 4, 2 min. 30 min d'attente avant le pétrissage suivant.")),

        // ------------------------------------------------------------ cuire
        CUISSON_PERSO(trKey("Cuisson personnalisée"), Groupe.CUIRE, Sorte.MANUEL, Etat.PRETE, 60, 1, 99 * 60, 1, 0, false,
            trKey("Tous les réglages de l'appli d'origine : 37 à 130 °C (40 °C compris), vitesse 1 à 10, sens inverse.")),
        CUIRE(trKey("Cuire"), Groupe.CUIRE, Sorte.MANUEL, Etat.PRETE, 10 * 60, 1, 99 * 60, 1, 100, false,
            trKey("Proposition (pas de bouton Cuire dans l'appli d'origine) : cuisson personnalisée, 100 °C, vitesse 1, 10 min.")),
        VAPEUR(trKey("Cuisson vapeur"), Groupe.CUIRE, Sorte.VAPEUR, Etat.PRETE, 20 * 60, 60, 60 * 60, 1, 120, false,
            trKey("Appli d'origine : Cuisson vapeur, 120 °C, vitesse 1, 20 min (60 min maximum).")),
        SAISIR(trKey("Saisir"), Groupe.CUIRE, Sorte.SAISIR, Etat.PRETE, 7 * 60, 60, 14 * 60, 1, 130, true,
            trKey("Appli d'origine : Saisir, 130 °C, 7 min (14 min maximum) : 5 s vitesse 1 en sens inverse, puis 30 s de pause, en boucle.")),
        MIJOTER(trKey("Mijoter"), Groupe.CUIRE, Sorte.ETAPES, Etat.PRETE, 4 * 3600, 15 * 60, 8 * 3600, 1, 90, true,
            trKey("Appli d'origine : Mijoter, 90 °C, 4 h : brasse 30 s (vitesse 1, sens inverse) toutes les 2 min 30 pendant 10 min, puis lame à l'arrêt.")),
        OEUFS(trKey("Œufs"), Groupe.CUIRE, Sorte.PROGRAMME, Etat.PRETE, 8 * 60, 60, 20 * 60, 1, 130, false,
            trKey("Appli d'origine : Cuire des œufs, 130 °C, vitesse 1, 8 min (20 min maximum)."), SmartProgram.EGGS),
        RIZ(trKey("Riz"), Groupe.CUIRE, Sorte.PROGRAMME, Etat.PRETE, 30 * 60, 20 * 60, 40 * 60, 1, 100, true,
            trKey("Appli d'origine : Cuire du riz, 100 °C, vitesse 1 en sens inverse, 30 min (20 à 40 min)."), SmartProgram.RICE),
        EAU(trKey("Chauffer l'eau"), Groupe.CUIRE, Sorte.PROGRAMME, Etat.PRETE, 10 * 60, 60, 20 * 60, 1, 100, true,
            trKey("Appli d'origine : Chauffer l'eau, 100 °C, vitesse 1 en sens inverse, 10 min (20 min maximum)."), SmartProgram.KETTLE),

        // ------------------------------------------------------------ programmes longs
        SOUS_VIDE(trKey("Sous-vide"), Groupe.AUTOMATIQUE, Sorte.PROGRAMME, Etat.PRETE, 6 * 3600, 15 * 60, 12 * 3600, 1, 60, true,
            trKey("Appli d'origine : Sous-vide, 60 °C, vitesse 1 en sens inverse, 6 h (15 min à 12 h)."), SmartProgram.SOUS_VIDE),
        FERMENTER(trKey("Fermenter"), Groupe.AUTOMATIQUE, Sorte.ETAPES, Etat.PRETE, 8 * 3600, 30 * 60, 12 * 3600, 1, 40, true,
            trKey("Appli d'origine : Fermenter, 40 °C, 8 h : brasse 5 min (vitesse 1, sens inverse), puis lame à l'arrêt.")),

        // ------------------------------------------------------------ outils
        TURBO(trKey("Turbo"), Groupe.OUTILS, Sorte.TURBO, Etat.PRETE, 3, 0, 3, 10, 0, false,
            trKey("Appli d'origine : Turbo, impulsions à vitesse maximale tant qu'on appuie (3 s au plus par appui), sans chauffe.")),
        BALANCE(trKey("Balance"), Groupe.OUTILS, Sorte.BALANCE, Etat.PRETE, 0, 0, 0, 0, 0, false,
            trKey("Appli d'origine : Balance, avec remise à zéro (tare).")),

        // ------------------------------------------------------------ nettoyer
        PRELAVAGE(trKey("Prélavage rapide"), Groupe.NETTOYER, Sorte.ETAPES, Etat.PRETE, 90, 90, 90, 10, 37, false,
            trKey("Appli d'origine : Prélavage, 1 min 30, eau à 37 °C : 30 s vitesse 10, 30 s vitesse 4 en sens inverse, 30 s vitesse 10.")),
        NETTOYAGE_LONG(trKey("Nettoyage long"), Groupe.NETTOYER, Sorte.ETAPES, Etat.PRETE, NETTOYAGE_MAX_S, NETTOYAGE_MAX_S, NETTOYAGE_MAX_S, 10, 40, false,
            trKey("Appli d'origine : Nettoyage long, 17 min au plus : 30 s vitesse 10, chauffe de l'eau à 40 °C (15 min au plus, finit dès que la température est atteinte), puis 1 min 45 de lavage (vitesse 10 et vitesse 4 en sens inverse).")),
        ;

        val direction: Direction get() = if (reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE

        /**
         * Durée maximale permise ici : celle de l'appli d'origine, sans dépasser la limite de Toque
         * (99 min). Le sous-vide (12 h dans l'appli d'origine) attend l'essai « la carte s'arrête
         * seule si Android se tait ».
         */
        val maxEffectifS: Int get() = minOf(maxS, TOQUE_MAX_S)

        /** Durée proposée : celle de l'appli d'origine, ramenée à [maxEffectifS]. */
        val dureeProposee: Int get() = minOf(seconds, maxEffectifS)

        /** La durée se règle-t-elle ? */
        val dureeReglable: Boolean get() = maxEffectifS > minS

        /** Cuisson personnalisée : tout se règle ; programmes : seulement la durée. */
        val toutReglable: Boolean get() = sorte == Sorte.MANUEL && this != TRANCHEUSE

        /** Niveau de la table du Smart pour [celsius] (cuisson personnalisée). */
        val smartLevel: Int get() = Smart.TEMPERATURE_LEVELS.indexOf(celsius).also { check(it >= 0) }

        /** Peut-on la démarrer depuis son écran de réglage ? */
        val demarrable: Boolean get() = etat == Etat.PRETE && sorte in setOf(Sorte.MANUEL, Sorte.PROGRAMME, Sorte.MALAXER, Sorte.ETAPES, Sorte.VAPEUR, Sorte.SAISIR)
    }

    /**
     * Travail à envoyer à la machine pour [f], avec une durée réglée en secondes, ou null si la
     * fonction ne démarre pas de programme (Turbo, Balance, fonctions pas encore utilisables).
     * Les fonctions MANUEL passent par [manuel] (vitesse, température et sens réglés à l'écran).
     */
    fun job(f: Fonction, seconds: Int, label: String = f.label, hotBlendConfirmed: Boolean = false): JobSpec? {
        if (!f.demarrable) return null
        val s = seconds.coerceIn(maxOf(1, f.minS), maxOf(1, f.maxEffectifS))
        val ms = s * 1000L
        return when (f.sorte) {
            Sorte.MANUEL -> manuel(f.speed, f.smartLevel, f.reverse, s, label, hotBlendConfirmed)
            Sorte.PROGRAMME -> Programs.smartProgram(f.program!!, ms, label, hotBlendConfirmed)
            Sorte.MALAXER -> when (f) {
                Fonction.MALAXER_FERME -> Programs.smartMalaxerFerme(ms, label)
                Fonction.MALAXER_MOLLE -> Programs.smartMalaxerMolle(ms, label)
                // Pâte liquide : seuls 4 s capturés, une trame vitesse 4 en sens normal.
                else -> Programs.smartKnead(f.reverse, ms, label)
            }.copy(hotBlendConfirmed = hotBlendConfirmed, cooldown = true)
            Sorte.ETAPES -> when (f) {
                Fonction.MIJOTER -> Programs.smartMijoter(ms, label)
                Fonction.FERMENTER -> Programs.smartFermenter(ms, label)
                Fonction.PRELAVAGE -> Programs.smartPrelavage(label)
                Fonction.NETTOYAGE_LONG -> Programs.smartNettoyageLong(label)
                else -> return null
            }.copy(hotBlendConfirmed = hotBlendConfirmed)
            Sorte.VAPEUR -> Programs.steam(ms).copy(label = label)
            Sorte.SAISIR -> Programs.smartSaisir(ms, label)
            else -> null
        }
    }

    /**
     * Cuisson personnalisée : vitesse 1..10, niveau du Smart 0..20 (40 °C compris), sens. Le niveau
     * de Toque juste en dessous sert aux règles de sécurité ; le niveau du Smart est envoyé tel quel.
     */
    fun manuel(speed: Int, smartLevel: Int, reverse: Boolean, seconds: Int, label: String, hotBlendConfirmed: Boolean = false): JobSpec {
        val toque = Smart.toqueLevelFor(smartLevel)
        val exact = app.toque.core.protocol.Mcc.celsiusForLevel(toque) == Smart.celsiusForLevel(smartLevel)
        return Programs.manual(
            speed, toque, if (reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE, seconds * 1000L,
            hotBlendConfirmed = hotBlendConfirmed, label = label,
            // Le niveau du Smart n'est joint que s'il manque à la table de Toque (40 °C).
            smartLevel = if (exact) null else smartLevel,
        )
    }

    /** Limite de durée de Toque (Limits.MAX_MS_DEFAULT), en secondes. */
    const val TOQUE_MAX_S = 99 * 60

    /** Les 6 tuiles de l'accueil (EX-20) : la fonction ouverte par chacune. */
    val TUILES: List<Fonction> = listOf(Fonction.HACHER, Fonction.MIXER, Fonction.CUIRE, Fonction.MIJOTER, Fonction.MALAXER_FERME, Fonction.VAPEUR)

    /** Variantes proposées sur le même écran (Malaxer : ferme, molle, liquide). */
    fun variantes(f: Fonction): List<Fonction> = when (f) {
        Fonction.MALAXER_FERME, Fonction.MALAXER_MOLLE, Fonction.MALAXER_LIQUIDE ->
            listOf(Fonction.MALAXER_FERME, Fonction.MALAXER_MOLLE, Fonction.MALAXER_LIQUIDE)
        else -> listOf(f)
    }

    /** Durée lisible : « 45 s », « 2 min 30 s », « 6 h », « 6 h 15 ». */
    fun texteDuree(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"
        seconds % 3600 == 0 -> "${seconds / 3600} h"
        else -> String.format("%d h %02d", seconds / 3600, seconds / 60 % 60)
    }

    /** Grande valeur de la carte Durée et son unité : « 02:30 » ou, à partir d'une heure, « 6:15 » + « h ». */
    fun horloge(seconds: Int): Pair<String, String> =
        if (seconds < 3600) String.format("%02d:%02d", seconds / 60, seconds % 60) to ""
        else String.format("%d:%02d", seconds / 3600, seconds / 60 % 60) to "h"

    /** Températures de la cuisson personnalisée, dans l'ordre (0 = sans chauffe) : les 21 niveaux du Smart. */
    val TEMPERATURES: List<Int> get() = Smart.TEMPERATURE_LEVELS.toList()
}
