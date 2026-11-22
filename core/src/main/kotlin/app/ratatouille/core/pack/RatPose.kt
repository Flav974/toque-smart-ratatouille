package app.ratatouille.core.pack

import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.toque.core.machine.JobState
import app.toque.core.machine.PauseReason
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram

/**
 * Quel rat montrer, et lequel à sa place s'il manque (phase R3, LISTE-IMAGES-THEME.md, parties B et C).
 * Sans Android, pour être testé sur le PC. L'interface prend la première image présente de [choix].
 */
object RatPose {
    /** État du robot vu par le rat. */
    enum class Humeur { REPOS, EN_COURS, ATTENTE, ATTENTION, PRET }

    /** Le rat qui fait l'action de la fonction. */
    fun pourFonction(f: Fonction): PackImage = when (f) {
        Fonction.HACHER, Fonction.TRANCHEUSE -> PackImage.RAT_HACHER
        Fonction.MIXER, Fonction.SMOOTHIE -> PackImage.RAT_MIXER
        Fonction.MALAXER_FERME, Fonction.MALAXER_MOLLE, Fonction.MALAXER_LIQUIDE -> PackImage.RAT_PETRIR
        Fonction.CUISSON_PERSO, Fonction.CUIRE -> PackImage.RAT_CUIRE
        Fonction.VAPEUR -> PackImage.RAT_VAPEUR
        Fonction.SAISIR -> PackImage.RAT_SAISIR
        Fonction.MIJOTER -> PackImage.RAT_MIJOTER
        Fonction.OEUFS -> PackImage.RAT_OEUFS
        Fonction.RIZ -> PackImage.RAT_RIZ
        Fonction.EAU -> PackImage.RAT_EAU
        Fonction.SOUS_VIDE -> PackImage.RAT_SOUS_VIDE
        Fonction.FERMENTER -> PackImage.RAT_FERMENTER
        Fonction.TURBO -> PackImage.RAT_TURBO
        Fonction.BALANCE -> PackImage.RAT_BALANCE
        Fonction.PRELAVAGE, Fonction.NETTOYAGE_LONG -> PackImage.RAT_NETTOYAGE
    }

    /** Le rat d'une étape de recette, d'après son réglage machine. */
    fun pourEtape(m: StepMachine?): PackImage = when {
        m == null -> PackImage.RAT_RECETTE
        m.program == StepProgram.KNEAD -> PackImage.RAT_PETRIR
        m.program == StepProgram.STEAM -> PackImage.RAT_VAPEUR
        m.program == StepProgram.TOAST -> PackImage.RAT_SAISIR
        m.program == StepProgram.TURBO -> PackImage.RAT_TURBO
        m.tempC > 0 -> PackImage.RAT_CUIRE
        m.speed >= 7 -> PackImage.RAT_MIXER
        m.speed >= 4 -> PackImage.RAT_HACHER
        else -> PackImage.RAT_RECETTE
    }

    /** Humeur du rat d'après l'état de la machine. */
    fun humeur(s: Snapshot): Humeur = when {
        s.hasFault -> Humeur.ATTENTION
        s.state == JobState.PAUSED ->
            if (s.pauseReason == PauseReason.USER.name || s.pauseReason.isEmpty()) Humeur.ATTENTE else Humeur.ATTENTION
        s.hasSample && s.lidOpen -> Humeur.ATTENTION
        s.running -> Humeur.EN_COURS
        s.state == JobState.FINISHED && !s.hasFault -> Humeur.PRET
        else -> Humeur.REPOS
    }

    /**
     * Images à essayer, dans l'ordre : celle de l'humeur, puis l'action, puis une image proche
     * (EX-41 : une image absente est remplacée par la plus proche, puis par une icône neutre).
     */
    fun choix(action: PackImage, humeur: Humeur = Humeur.REPOS): List<PackImage> {
        val etat = when (humeur) {
            Humeur.PRET -> listOf(PackImage.RAT_PRET, PackImage.RAT_BONJOUR)
            Humeur.ATTENTION -> listOf(PackImage.RAT_ATTENTION)
            Humeur.ATTENTE -> listOf(PackImage.RAT_ATTENTE)
            Humeur.REPOS, Humeur.EN_COURS -> emptyList()
        }
        return (etat + action + proche(action) + PackImage.RAT_ACCUEIL).distinct()
    }

    /** Image proche d'une image absente. */
    private fun proche(i: PackImage): List<PackImage> = when (i) {
        PackImage.RAT_SAISIR, PackImage.RAT_OEUFS, PackImage.RAT_RIZ, PackImage.RAT_EAU,
        PackImage.RAT_SOUS_VIDE -> listOf(PackImage.RAT_CUIRE)
        PackImage.RAT_FERMENTER -> listOf(PackImage.RAT_MIJOTER)
        PackImage.RAT_TURBO -> listOf(PackImage.RAT_MIXER)
        PackImage.RAT_VAPEUR, PackImage.RAT_MIJOTER -> listOf(PackImage.RAT_CUIRE)
        PackImage.RAT_PRET, PackImage.RAT_BONJOUR, PackImage.RAT_IA -> listOf(PackImage.RAT_RECETTE)
        else -> emptyList()
    }
}
