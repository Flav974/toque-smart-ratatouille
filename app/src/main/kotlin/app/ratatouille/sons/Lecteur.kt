package app.ratatouille.sons

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Environment
import app.ratatouille.core.sons.Son
import app.toque.core.i18n.trKey
import java.io.File

/**
 * Sons choisis par l'utilisateur (Réglages › Sons). Le fichier choisi est copié dans le dossier
 * privé de l'appli (`files/sons/<clé>.<ext>`) : il reste même si l'original est effacé.
 * Lecture sur le flux des notifications, comme les bips de Toque.
 */
class Lecteur(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "sons")
    private var player: MediaPlayer? = null
    /** Nom d'origine des fichiers choisis, pour l'affichage. */
    private val noms = app.getSharedPreferences("sons", Context.MODE_PRIVATE)

    /** Nom du fichier choisi pour ce son (tel qu'il s'appelait), ou null. */
    fun nom(son: Son): String? = if (fichier(son) == null) null else noms.getString(son.cle, null)

    /** Fichier choisi pour ce son, ou null (son par défaut). */
    fun fichier(son: Son): File? =
        dir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith(son.cle + ".") && Son.extension(it.name) != null }

    /** Copie [source] comme son de [son]. Faux si le fichier n'est pas un son acceptable. */
    fun choisit(son: Son, source: File): Boolean {
        val ext = Son.extension(source.name) ?: return false
        if (!source.isFile || source.length() !in 1..Son.MAX_OCTETS) return false
        dir.mkdirs()
        val tmp = File(dir, ".copie")
        return try {
            source.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            retire(son)
            tmp.renameTo(File(dir, Son.fichier(son, ext))).also {
                if (it) noms.edit().putString(son.cle, source.nameWithoutExtension.replace('_', ' ')).apply()
            }
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /** Revient au son par défaut. */
    fun retire(son: Son) {
        dir.listFiles()?.filter { it.name.startsWith(son.cle + ".") }?.forEach { it.delete() }
    }

    /** Joue un fichier (son choisi ou essai). Faux si la lecture est impossible. */
    @Suppress("DEPRECATION")
    fun joue(f: File): Boolean {
        stop()
        return try {
            val p = MediaPlayer()
            p.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
            p.setDataSource(f.path)
            p.setOnCompletionListener { it.release(); if (player === it) player = null }
            p.setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; true }
            p.prepare()
            p.start()
            player = p
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
    }

    companion object {
        /** Dossiers où chercher des sons (nom à traduire avec `tr`) : ceux du système, celui de l'appli (dépôt par adb), puis ceux du stockage. */
        fun dossiers(context: Context): List<Pair<String, File>> {
            val ext = Environment.getExternalStorageDirectory()
            return listOfNotNull(
                context.getExternalFilesDir("sons")?.let { "Ratatouille" to it },
                trKey("Musique") to File(ext, Environment.DIRECTORY_MUSIC),
                trKey("Téléchargements") to File(ext, Environment.DIRECTORY_DOWNLOADS),
                trKey("Notifications") to File(ext, Environment.DIRECTORY_NOTIFICATIONS),
                trKey("Sonneries") to File(ext, Environment.DIRECTORY_RINGTONES),
                trKey("Alarmes") to File(ext, Environment.DIRECTORY_ALARMS),
            ) + listOf("notifications" to trKey("Système · notifications"), "alarms" to trKey("Système · alarmes"),
                "ui" to trKey("Système · interface")).map { (d, nom) ->
                // Android 8.1 (robot) : /system/media/audio ; Android 10 et plus : /product/media/audio.
                nom to (listOf("/system/media/audio/$d", "/product/media/audio/$d").map { File(it) }.firstOrNull { it.isDirectory }
                    ?: File("/system/media/audio/$d"))
            }
        }

        /** Sons d'un dossier (pas de sous-dossiers), triés par nom. */
        fun sons(dossier: File): List<File> =
            (dossier.listFiles() ?: emptyArray()).filter { it.isFile && Son.extension(it.name) != null && it.length() in 1..Son.MAX_OCTETS }
                .sortedBy { it.name.lowercase() }
    }
}
