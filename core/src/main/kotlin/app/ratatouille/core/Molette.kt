package app.ratatouille.core

/**
 * Molette du robot et volume (demande de l'utilisateur, R2) : quand une autre appli est devant
 * (NewPipe, vidéos, musique), les crans de la molette règlent le volume de la musique. Dans
 * l'Édition Ratatouille, la molette garde son rôle (réglages, démarrage, Turbo).
 *
 * L'appli d'origine reçoit la molette comme des touches (`oa.b` de com.tecpal.device.mc30) :
 * 136 = F6 et 137 = F7 pour la rotation, 135 = F5, 66 = Entrée ou le code matériel 28 pour l'appui.
 * Relevé sur le robot le 2026-11-21 (getevent, clavier `mtk-kpd`) : vers la droite = F6, vers la
 * gauche = F7 (comme le réglage de Toque Smart). Droite = monter le son. Appui (F5) = couper ou
 * remettre le son (demande de l'utilisateur, 2026-11-21). Un réglage fait dans
 * Réglages › Outils › Bouton rotatif passe avant.
 */
object Molette {
    const val F5 = 135
    const val F6 = 136
    const val F7 = 137

    /** Effet [volume] : couper ou remettre le son. */
    const val MUET = 2

    /** Applis devant lesquelles la molette ne règle jamais le volume. */
    private val SANS_VOLUME = setOf("com.android.systemui")

    /**
     * Effet d'un appui de touche : -1 = baisser, +1 = monter, [MUET] = couper ou remettre le son,
     * 0 = ne rien faire (la touche suit son chemin normal). [gauche]/[droite]/[appui] = codes réglés
     * par l'utilisateur, ou -1. Sans réglage, seul F5 compte comme appui (Entrée reste au clavier).
     */
    fun volume(keyCode: Int, premierPlan: String?, notrePaquet: String, gauche: Int = -1, droite: Int = -1, appui: Int = -1): Int {
        if (premierPlan.isNullOrEmpty() || premierPlan == notrePaquet || premierPlan in SANS_VOLUME) return 0
        val g = if (gauche >= 0) gauche else F7
        val d = if (droite >= 0) droite else F6
        val a = if (appui >= 0) appui else F5
        return when (keyCode) {
            g -> -1
            d -> 1
            a -> MUET
            else -> 0 // toutes les autres touches : inchangées
        }
    }
}
