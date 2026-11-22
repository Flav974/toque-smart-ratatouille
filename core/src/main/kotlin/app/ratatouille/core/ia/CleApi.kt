package app.ratatouille.core.ia

import app.toque.core.i18n.trKey

/**
 * Clé API Claude de « Cuisiner avec l'IA » (EX-74) : contrôle du format et affichage masqué.
 *
 * La clé est saisie par l'utilisateur dans Réglages et gardée seulement dans les données privées de
 * l'appli, sur le robot. Elle n'est jamais dans le code, le dépôt ni les journaux : rien ici ne
 * l'affiche en entier, et [Resultat.Ok.toString] la masque, au cas où un objet finirait dans un journal.
 */
object CleApi {

    const val PREFIXE = "sk-ant-"

    sealed class Resultat {
        class Ok(val cle: String) : Resultat() {
            override fun toString() = "Ok(${masque(cle)})"
        }
        /** [raison] : texte à traduire avec `tr`. */
        class Refus(val raison: String) : Resultat() {
            override fun toString() = "Refus($raison)"
        }
    }

    /**
     * Nettoie une clé collée ou tapée (espaces et retours à la ligne enlevés) et vérifie sa forme.
     * Ne dit jamais si la clé est valable chez Anthropic : seul un appel à l'API le sait.
     */
    fun nettoie(brut: String): Resultat {
        val k = brut.filterNot { it.isWhitespace() }
        if (k.isEmpty()) return Resultat.Refus(trKey("Aucune clé saisie."))
        if (!k.startsWith(PREFIXE)) return Resultat.Refus(trKey("Une clé Claude commence par « sk-ant- »."))
        if (k.length !in 40..300) return Resultat.Refus(trKey("Cette clé n'a pas la bonne longueur."))
        if (!k.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' })
            return Resultat.Refus(trKey("Cette clé contient des caractères inattendus."))
        return Resultat.Ok(k)
    }

    /** Ce que l'écran montre d'une clé : le début commun et les 4 derniers caractères. */
    fun masque(cle: String): String = if (cle.length < 12) "…" else PREFIXE + "…" + cle.takeLast(4)
}
