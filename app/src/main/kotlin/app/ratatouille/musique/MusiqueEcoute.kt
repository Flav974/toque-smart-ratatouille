package app.ratatouille.musique

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Service vide : sa seule présence, une fois autorisé par l'utilisateur (accès aux notifications),
 * permet à l'accueil de lire les sessions média des autres applis (bulle du lecteur de musique).
 * Il ne lit ni ne garde aucune notification.
 */
class MusiqueEcoute : NotificationListenerService() {
    companion object {
        fun composant(c: Context) = ComponentName(c, MusiqueEcoute::class.java)

        /** L'utilisateur a-t-il donné l'accès aux notifications à Ratatouille ? */
        fun autorise(c: Context): Boolean {
            val liste = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            return liste.split(':').any { ComponentName.unflattenFromString(it) == composant(c) }
        }
    }
}
