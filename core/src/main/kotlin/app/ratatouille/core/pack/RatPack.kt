package app.ratatouille.core.pack

import java.io.File
import java.io.IOException

/**
 * Images du **pack privé** de l'Édition Ratatouille (cahier des charges, EX-40 et EX-41).
 *
 * Le pack n'est jamais dans le dépôt ni dans l'APK : c'est un dossier sur le robot, rempli depuis le PC
 * (`ratatouille/pack-prive/` → `/sdcard/Android/data/app.ratatouille.smart/files/pack/`).
 * [path] est le nom du fichier sans extension, relatif au dossier du pack.
 */
enum class PackImage(val path: String) {
    DEMARRAGE("demarrage"),
    LOGO("logo"),
    ACCUEIL_FOND("accueil-fond"),
    RAT_ACCUEIL("rat-accueil"),

    // Illustrations des 6 tuiles de l'accueil (image de référence 3), ajoutées en phase R1.
    TUILE_HACHER("tuiles/hacher"),
    TUILE_MIXER("tuiles/mixer"),
    TUILE_CUIRE("tuiles/cuire"),
    TUILE_MIJOTER("tuiles/mijoter"),
    TUILE_PETRIR("tuiles/petrir"),
    TUILE_VAPEUR("tuiles/vapeur"),

    // Les 12 actions de la planche (image de référence 2).
    HACHE("actions/hache"),
    MIXE("actions/mixe"),
    CHAUFFE("actions/chauffe"),
    VAPEUR("actions/vapeur"),
    MIJOTE("actions/mijote"),
    PETRISSAGE("actions/petrissage"),
    SAUTE("actions/mijotage-saute"),
    LAVAGE("actions/lavage"),
    PRELAVAGE("actions/prelavage"),
    VEILLE("actions/veille"),

    // Thème clair partout (phase R3) : décors des autres écrans et de l'accueil selon l'heure.
    FOND_ECRAN("fond-ecran"),
    FOND_NUIT("nuit-fond"),
    FOND_COUCHER("coucher-fond"),

    // Le rat en action, un par fonction (LISTE-IMAGES-THEME.md, partie B).
    RAT_HACHER("rat/hacher"),
    RAT_MIXER("rat/mixer"),
    RAT_CUIRE("rat/cuire"),
    RAT_MIJOTER("rat/mijoter"),
    RAT_PETRIR("rat/petrir"),
    RAT_VAPEUR("rat/vapeur"),
    RAT_SAISIR("rat/saisir"),
    RAT_OEUFS("rat/oeufs"),
    RAT_RIZ("rat/riz"),
    RAT_EAU("rat/eau"),
    RAT_SOUS_VIDE("rat/sous-vide"),
    RAT_FERMENTER("rat/fermenter"),
    RAT_TURBO("rat/turbo"),
    RAT_BALANCE("rat/balance"),
    RAT_NETTOYAGE("rat/nettoyage"),

    // Le rat selon l'état du robot (partie C).
    RAT_VEILLE("rat/veille"),
    RAT_PRET("rat/pret"),
    RAT_ATTENTION("rat/attention"),
    RAT_ATTENTE("rat/attente"),
    RAT_BONJOUR("rat/bonjour"),
    RAT_RECETTE("rat/recette"),
    RAT_REGLAGES("rat/reglages"),
    /** « Cuisiner avec l'IA » (2026-11-22). */
    RAT_IA("rat/ia"),
}

/**
 * Recherche des images du pack. Une image absente, vide, trop grosse ou hors du dossier donne `null` :
 * l'appli affiche alors une image neutre. Rien ne doit planter sans pack.
 */
class RatPack(root: File?) {
    companion object {
        /** Nom du dossier du pack dans le stockage externe de l'appli. */
        const val DIR = "pack"

        /** Formats acceptés, par ordre de préférence. */
        val EXTENSIONS = listOf("webp", "png", "jpg")

        /** Au-delà, l'image est ignorée (2 Go de RAM sur le robot). */
        const val MAX_BYTES = 4L * 1024 * 1024
    }

    private val base: File? = root?.let { canonical(it) }?.takeIf { it.isDirectory }

    /** Fichier de l'image, ou `null` (image neutre). */
    fun find(image: PackImage): File? {
        val dir = base ?: return null
        for (ext in EXTENSIONS) {
            val f = canonical(File(dir, "${image.path}.$ext")) ?: continue
            // Pas de lien qui sortirait du dossier du pack.
            if (!f.path.startsWith(dir.path + File.separator)) continue
            if (f.isFile && f.length() in 1..MAX_BYTES && f.canRead()) return f
        }
        return null
    }

    /** Première image présente de la liste (image absente → la plus proche, EX-41), ou `null`. */
    fun first(images: List<PackImage>): PackImage? = images.firstOrNull { find(it) != null }

    fun present(): List<PackImage> = PackImage.values().filter { find(it) != null }

    fun missing(): List<PackImage> = PackImage.values().filter { find(it) == null }

    /** Résumé pour le journal : « pack : 3/47 images ». */
    fun summary(): String =
        if (base == null) "pack : absent (images neutres)" else "pack : ${present().size}/${PackImage.values().size} images"

    private fun canonical(f: File): File? = try {
        f.canonicalFile
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
