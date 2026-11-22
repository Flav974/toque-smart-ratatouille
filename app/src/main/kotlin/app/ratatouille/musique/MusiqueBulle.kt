package app.ratatouille.musique

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.ratatouille.core.musique.Musique
import app.ratatouille.ui.RatTheme
import app.toque.core.machine.RootShell
import app.toque.ui.Icon
import app.toque.ui.Ui

/**
 * Bulle de l'accueil, à côté du logo : le morceau joué par une autre appli (Spotify, NewPipe…),
 * avec précédent, lecture/pause et suivant. Un appui sur le texte rouvre l'appli.
 * Cachée sans accès aux notifications (donné par l'utilisateur) ou sans session média.
 */
class MusiqueBulle(c: Context) : LinearLayout(c) {
    private val handler = Handler(Looper.getMainLooper())
    private val texte: TextView = Ui.ellipsize(Ui.text(c, "", 17f, RatTheme.BROWN, RatTheme.CONDENSED_BOLD))
    private val lecture = Icon(c, Icon.Kind.PLAY, RatTheme.BROWN)
    private var msm: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var actif: MediaController? = null
    private var demarre = false
    private var finJetons: (() -> Unit)? = null
    /** Mode root (robot « petite mémoire ») : dernière session lue dans `dumpsys media_session`. */
    @Volatile private var rootChoix: Musique.Session? = null
    @Volatile private var rootLecture = false
    private val sondeRoot = object : Runnable {
        override fun run() {
            if (!demarre) return
            if (controllers.isEmpty() && !rootLecture) {
                rootLecture = true
                Thread({
                    val r = RootShell.run("dumpsys media_session", 5_000)
                    val choix = if (r.ok) Musique.choisit(Musique.depuisDumpsys(r.out), context.packageName) else null
                    handler.post { rootLecture = false; rootChoix = choix; if (controllers.isEmpty()) montre(choix) }
                }, "musique-root").start()
            }
            handler.postDelayed(this, 4_000)
        }
    }

    private val rappel = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = rafraichit()
        override fun onMetadataChanged(metadata: MediaMetadata?) = rafraichit()
        override fun onSessionDestroyed() {
            actif?.let { Jetons.retire(it.packageName) }
            relit()
        }
    }
    private val sessionsChangent = MediaSessionManager.OnActiveSessionsChangedListener { handler.post { relit() } }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.u(14), Ui.u(4), Ui.u(6), Ui.u(4))
        background = Ui.round(RatTheme.CREAM, 30f, RatTheme.CREAM_DARK, 2f)
        texte.setOnClickListener { ouvreAppli() }
        texte.setPadding(0, Ui.u(8), Ui.u(6), Ui.u(8))
        addView(texte, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(bouton(Icon(c, Icon.Kind.PREV, RatTheme.BROWN)) { actif?.transportControls?.skipToPrevious() ?: touche("previous") })
        addView(bouton(lecture) {
            val a = actif ?: return@bouton touche("play-pause")
            if (a.playbackState?.state == PlaybackState.STATE_PLAYING) a.transportControls.pause() else a.transportControls.play()
        })
        addView(bouton(Icon(c, Icon.Kind.NEXT, RatTheme.BROWN)) { actif?.transportControls?.skipToNext() ?: touche("next") })
        visibility = View.GONE
    }

    private fun bouton(i: Icon, action: () -> Unit): View {
        i.setPadding(Ui.u(9), Ui.u(9), Ui.u(9), Ui.u(9))
        i.background = Ui.pressable(Ui.round(0, 24f), 24f)
        i.setOnClickListener { try { action() } catch (_: Exception) {} }
        i.layoutParams = LayoutParams(Ui.u(44), Ui.u(44))
        return i
    }

    /** À l'affichage de l'accueil. */
    fun demarre() {
        if (demarre) return
        demarre = true
        // Robot « petite mémoire » : pas d'accès aux notifications, on passe par les jetons vus par
        // le service d'accessibilité (Jetons). Ailleurs, accès aux notifications si l'utilisateur l'a donné.
        finJetons = Jetons.ecoute { relit() }
        handler.postDelayed(sondeRoot, 300)
        if (!MusiqueEcoute.autorise(context)) { relit(); return }
        try {
            val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            m.addOnActiveSessionsChangedListener(sessionsChangent, MusiqueEcoute.composant(context), handler)
            msm = m
            relit()
        } catch (_: SecurityException) {
            visibility = View.GONE
        }
    }

    /** Quand l'accueil est caché. */
    fun arrete() {
        demarre = false
        handler.removeCallbacks(sondeRoot)
        finJetons?.invoke()
        finJetons = null
        try { msm?.removeOnActiveSessionsChangedListener(sessionsChangent) } catch (_: Exception) {}
        msm = null
        controllers.forEach { try { it.unregisterCallback(rappel) } catch (_: Exception) {} }
        controllers = emptyList()
        actif = null
    }

    private fun relit() {
        if (!demarre) return
        controllers.forEach { try { it.unregisterCallback(rappel) } catch (_: Exception) {} }
        val actives = msm?.let { m -> try { m.getActiveSessions(MusiqueEcoute.composant(context)) } catch (_: SecurityException) { null } }
        controllers = actives ?: Jetons.tous().values.mapNotNull { t -> try { MediaController(context, t) } catch (_: Exception) { null } }
        controllers.forEach { it.registerCallback(rappel, handler) }
        rafraichit()
    }

    private fun session(mc: MediaController): Musique.Session {
        val md = mc.metadata
        val st = mc.playbackState
        return Musique.Session(
            paquet = mc.packageName,
            joue = st?.state == PlaybackState.STATE_PLAYING,
            titre = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artiste = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            majMs = st?.lastPositionUpdateTime ?: 0,
        )
    }

    private fun rafraichit() {
        if (controllers.isEmpty()) { actif = null; montre(rootChoix); return }
        val paires = controllers.map { it to session(it) }
        val choix = Musique.choisit(paires.map { it.second }, context.packageName)
        actif = paires.firstOrNull { it.second == choix }?.first
        montre(choix)
    }

    /** Touche média envoyée par root (`media dispatch`), puis relecture rapide de l'état. */
    private fun touche(cle: String) {
        Thread({ RootShell.run("media dispatch $cle", 5_000) }, "musique-touche").start()
        handler.removeCallbacks(sondeRoot)
        handler.postDelayed(sondeRoot, 700)
    }

    private fun montre(choix: Musique.Session?) {
        if (choix == null) { visibility = View.GONE; return }
        texte.text = Musique.texte(choix, nomAppli(choix.paquet))
        lecture.kind = if (choix.joue) Icon.Kind.PAUSE else Icon.Kind.PLAY
        lecture.invalidate()
        visibility = View.VISIBLE
    }

    private fun nomAppli(paquet: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString()
    } catch (_: Exception) {
        paquet
    }

    private fun ouvreAppli() {
        val a = actif
        try {
            val pi = a?.sessionActivity
            if (pi != null) { pi.send(); return }
        } catch (_: Exception) {
        }
        val paquet = a?.packageName ?: rootChoix?.paquet ?: return
        context.packageManager.getLaunchIntentForPackage(paquet)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
