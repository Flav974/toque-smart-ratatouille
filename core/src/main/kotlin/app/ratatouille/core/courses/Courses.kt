package app.ratatouille.core.courses

import app.toque.core.recipes.Recipe

/**
 * Liste de courses d'une recette, en texte simple : c'est ce que le téléphone lit dans le QR code
 * (l'appareil photo affiche le texte, à copier dans une note). Rien ne passe par Internet.
 */
object Courses {
    /** Au-delà, le QR code devient trop dense pour être lu sur l'écran du robot. */
    const val MAX_OCTETS = 1200

    fun texte(r: Recipe): String = buildString {
        append("Courses : ").append(r.title.trim())
        if (r.servings > 0) append(" (").append(r.servings).append(" pers.)")
        for (g in r.ingredients) {
            if (g.title.isNotBlank()) append("\n\n").append(g.title.trim()).append(" :")
            for (i in g.items) append("\n- ").append(i.display.trim())
        }
    }.let { coupe(it) }

    /** Coupe proprement une liste trop longue pour un QR code lisible. */
    private fun coupe(t: String): String {
        if (t.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return t
        val lignes = t.split('\n')
        val out = StringBuilder()
        for (l in lignes) {
            if ((out.toString() + "\n" + l + "\n…").toByteArray(Charsets.UTF_8).size > MAX_OCTETS) break
            if (out.isNotEmpty()) out.append('\n')
            out.append(l)
        }
        return out.append("\n…").toString()
    }
}
