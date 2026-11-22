package app.ratatouille.ia

import android.content.Context
import app.ratatouille.core.ia.CleApi
import app.toque.core.i18n.trKey
import java.io.File

/**
 * Où vit la clé API Claude (EX-74) : un fichier de réglages privé de l'appli (`ia.xml`, dans
 * /data/data/app.ratatouille.smart/), lisible seulement par elle. Sauvegarde Android désactivée
 * (`allowBackup="false"`). Jamais écrite dans le journal.
 */
class CleStore(context: Context) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences("ia", Context.MODE_PRIVATE)

    /** La clé enregistrée, ou null. */
    val cle: String? get() = p.getString(KEY, null)?.takeIf { it.isNotEmpty() }

    fun enregistre(brut: String): CleApi.Resultat {
        val r = CleApi.nettoie(brut)
        if (r is CleApi.Resultat.Ok) p.edit().putString(KEY, r.cle).commit()
        return r
    }

    fun efface() {
        p.edit().remove(KEY).commit()
    }

    /**
     * Fichier déposé depuis le PC pour éviter de taper la clé à l'écran :
     * `adb push cle-claude.txt /sdcard/Android/data/app.ratatouille.smart/files/`.
     */
    fun fichierDepose(): File? = app.getExternalFilesDir(null)?.let { File(it, FICHIER) }

    /** Lit la clé du fichier déposé, l'enregistre, puis efface le fichier dans tous les cas. */
    fun importeFichier(): CleApi.Resultat? {
        val f = fichierDepose() ?: return null
        if (!f.isFile) return null
        return try {
            enregistre(f.readText())
        } catch (_: Exception) {
            CleApi.Resultat.Refus(trKey("Fichier de clé illisible."))
        } finally {
            f.delete()
        }
    }

    companion object {
        private const val KEY = "cle"
        const val FICHIER = "cle-claude.txt"
    }
}
