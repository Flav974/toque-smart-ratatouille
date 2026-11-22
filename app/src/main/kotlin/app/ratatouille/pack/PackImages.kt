package app.ratatouille.pack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import app.ratatouille.core.pack.PackImage
import app.ratatouille.core.pack.RatPack
import app.toque.ui.Icon
import java.io.File

/**
 * Chargement du pack privé sur le robot (EX-40), avec repli sur une icône neutre (EX-41).
 * Dossier : `/sdcard/Android/data/app.ratatouille.smart/files/pack/` (aucune permission nécessaire).
 */
class PackImages(context: Context) {
    val pack = RatPack(dir(context))

    /** Image du pack réduite à la taille voulue, ou `null` (absente, illisible, mémoire). */
    fun bitmap(image: PackImage, maxW: Int, maxH: Int): Bitmap? {
        val f = pack.find(image) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) { // OutOfMemoryError compris : l'appli continue avec l'image neutre
            null
        }
    }

    /** Vue prête à afficher : l'image du pack, ou l'icône neutre. */
    fun view(context: Context, image: PackImage, maxW: Int, maxH: Int, color: Int): View {
        val b = bitmap(image, maxW, maxH)
        if (b == null) return Icon(context, neutral(image), color)
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(b)
        }
    }

    companion object {
        fun dir(context: Context): File? = try {
            context.getExternalFilesDir(RatPack.DIR)
        } catch (_: Throwable) {
            null
        }

        /** Icône neutre de chaque image (le `when` complet oblige à couvrir toute nouvelle image). */
        fun neutral(image: PackImage): Icon.Kind = when (image) {
            PackImage.DEMARRAGE, PackImage.LOGO, PackImage.ACCUEIL_FOND -> Icon.Kind.HOME
            PackImage.RAT_ACCUEIL -> Icon.Kind.POT
            PackImage.TUILE_HACHER -> Icon.Kind.SPEED
            PackImage.TUILE_MIXER -> Icon.Kind.WHISK
            PackImage.TUILE_CUIRE -> Icon.Kind.FLAME
            PackImage.TUILE_MIJOTER -> Icon.Kind.POT
            PackImage.TUILE_PETRIR -> Icon.Kind.KNEAD
            PackImage.TUILE_VAPEUR -> Icon.Kind.STEAM
            PackImage.HACHE -> Icon.Kind.SPEED
            PackImage.MIXE -> Icon.Kind.WHISK
            PackImage.CHAUFFE, PackImage.SAUTE -> Icon.Kind.FLAME
            PackImage.VAPEUR -> Icon.Kind.STEAM
            PackImage.MIJOTE -> Icon.Kind.POT
            PackImage.PETRISSAGE -> Icon.Kind.KNEAD
            PackImage.LAVAGE, PackImage.PRELAVAGE -> Icon.Kind.SYNC
            PackImage.VEILLE -> Icon.Kind.CLOCK
            PackImage.FOND_ECRAN, PackImage.FOND_NUIT, PackImage.FOND_COUCHER -> Icon.Kind.HOME
            PackImage.RAT_HACHER, PackImage.RAT_TURBO -> Icon.Kind.SPEED
            PackImage.RAT_MIXER -> Icon.Kind.WHISK
            PackImage.RAT_CUIRE, PackImage.RAT_SAISIR, PackImage.RAT_OEUFS, PackImage.RAT_RIZ -> Icon.Kind.FLAME
            PackImage.RAT_MIJOTER, PackImage.RAT_PRET, PackImage.RAT_BONJOUR -> Icon.Kind.POT
            PackImage.RAT_PETRIR -> Icon.Kind.KNEAD
            PackImage.RAT_VAPEUR, PackImage.RAT_EAU -> Icon.Kind.STEAM
            PackImage.RAT_SOUS_VIDE, PackImage.RAT_FERMENTER -> Icon.Kind.THERMO
            PackImage.RAT_BALANCE -> Icon.Kind.SCALE
            PackImage.RAT_NETTOYAGE -> Icon.Kind.SYNC
            PackImage.RAT_VEILLE, PackImage.RAT_ATTENTE -> Icon.Kind.CLOCK
            PackImage.RAT_ATTENTION -> Icon.Kind.WARNING
            PackImage.RAT_RECETTE, PackImage.RAT_IA -> Icon.Kind.BOOK
            PackImage.RAT_REGLAGES -> Icon.Kind.SETTINGS
        }
    }
}
