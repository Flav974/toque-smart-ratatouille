package app.ratatouille.machine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.ratatouille.core.Molette
import app.toque.Prefs

/**
 * Service d'accessibilité : la molette règle le volume quand une autre appli est devant, l'appui coupe
 * ou remet le son (voir
 * [Molette]). Il ne lit pas le contenu des écrans : seulement le nom de l'appli au premier plan
 * (changements de fenêtre) et les touches. Activé par root, réversible (CHANGEMENTS.md) :
 * `settings put secure enabled_accessibility_services app.ratatouille.smart/app.ratatouille.machine.MoletteVolumeService`.
 */
class MoletteVolumeService : AccessibilityService() {
    @Volatile private var premierPlan: String? = null

    override fun onServiceConnected() {
        // Filtre des touches demandé aussi ici (en plus du XML), pour être sûr qu'Android l'active.
        val info = serviceInfo
        if (info != null) {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }
        android.util.Log.i("RatMolette", "service connecté, drapeaux ${serviceInfo?.flags}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Bulle de musique de l'accueil : seulement le jeton de session média des notifications de lecture.
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val n = event.parcelableData as? android.app.Notification ?: return
            app.ratatouille.musique.Jetons.notification(event.packageName?.toString() ?: return, n)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Le clavier et la barre système ne changent pas l'appli « devant ».
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod", ignoreCase = true) || pkg.endsWith("latin")) return
        premierPlan = pkg
        android.util.Log.i("RatMolette", "premier plan : $pkg")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = try { Prefs(this) } catch (_: Exception) { null }
        val sens = Molette.volume(event.keyCode, premierPlan, packageName, prefs?.knobLeft ?: -1, prefs?.knobRight ?: -1, prefs?.knobPress ?: -1)
        if (sens == 0) return false
        // Un seul cran par appui ; le relâchement est aussi gardé pour ne pas arriver à l'appli.
        if (event.action == KeyEvent.ACTION_DOWN) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val effet = when {
                sens == Molette.MUET -> AudioManager.ADJUST_TOGGLE_MUTE // appui : couper ou remettre le son
                sens > 0 -> AudioManager.ADJUST_RAISE // monter remet aussi le son s'il était coupé
                else -> AudioManager.ADJUST_LOWER
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, effet, AudioManager.FLAG_SHOW_UI)
        }
        return true
    }

    override fun onInterrupt() {}
}
