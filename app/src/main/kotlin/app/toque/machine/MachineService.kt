package app.toque.machine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import app.toque.Logbook
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.ControllerConfig
import app.toque.core.machine.JobState
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.MachineLoop
import app.toque.core.machine.MonotonicClock
import app.toque.core.machine.RootSerialTransport
import app.toque.core.machine.RootShell
import app.toque.core.machine.SimBoard
import app.toque.core.machine.SimTransport
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TcpTransport
import app.toque.core.machine.Transport
import app.toque.core.machine.Verdict
import app.toque.core.protocol.Direction
import app.toque.ui.MainActivity
import app.ratatouille.core.Rivals
import app.ratatouille.machine.ExclusiveTransport

/**
 * Service de pilotage, dans son propre processus (« :machine ») : un plantage de l'interface
 * n'interrompt jamais un programme en cours, qui se termine et s'arrête normalement.
 */
class MachineService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val clients = ArrayList<Messenger>()
    private var loop: MachineLoop? = null
    private var sim: SimBoard? = null
    private var lastSent: Snapshot? = null
    private var lastSentAt = 0L
    private var lastNotified = ""
    private lateinit var prefs: Prefs
    private lateinit var log: Logbook
    private var ring: RingLight? = null
    /**
     * Édition Ratatouille (2026-11-22) : écran éteint, Android endormait le processeur ; la boucle
     * n'envoyait plus ses trames et la carte du Smart levait l'erreur 15 (~3 min après la veille).
     * Comme l'appli d'origine, on garde le processeur éveillé en Machine réelle (écran éteint).
     */
    private var eveil: android.os.PowerManager.WakeLock? = null

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        try {
            handle(msg)
        } catch (t: Throwable) {
            log.write("machine", "message ${msg.what} en échec : $t")
        }
        true
    })

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        log = Logbook(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                loop?.emergencyStop()
                log.crash("machine", error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        startForeground(NOTIFICATION_ID, notification(tr("Base moteur"), tr("Démarrage…")))
        createLoop()
        if (prefs.mode == Prefs.Mode.HARDWARE) {
            ring = RingLight { line -> log.write("machine", line) }.also { it.register(this) }
            eveil = (getSystemService(POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ratatouille:liaison-moteur")
                .also { it.setReferenceCounted(false); it.acquire() }
            Thread({ screenTimeout() }, "screen-timeout").apply { isDaemon = true }.start()
        }
    }

    /**
     * Veille automatique : l'appli d'origine gère sa propre veille et laisse le délai système à 0
     * (jamais). Toque met 5 min, seulement si le délai est encore à 0 ou absent, pour ne pas écraser
     * un choix fait ensuite dans les Paramètres d'Android.
     */
    private fun screenTimeout() {
        val r = RootShell.run(
            "t=\$(settings get system screen_off_timeout); " +
                "case \"\$t\" in ''|null|0|-*) settings put system screen_off_timeout $SCREEN_TIMEOUT_MS; echo set;; *) echo \$t;; esac",
            10_000,
        )
        log.write("machine", "veille écran : ${r.out.trim()} ${r.err.trim()}".trim())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        ring?.unregister(this)
        ring = null
        eveil?.let { if (it.isHeld) it.release() }
        eveil = null
        loop?.shutdown()
        loop = null
        super.onDestroy()
    }

    private fun createLoop() {
        loop?.shutdown()
        prefs = Prefs(this) // relit le fichier écrit par l'interface
        val mode = prefs.mode
        sim = null
        val factory: () -> Transport = when (mode) {
            Prefs.Mode.DEMO -> {
                val board = SimBoard(MonotonicClock)
                sim = board
                ({ SimTransport(board) })
            }
            Prefs.Mode.TWIN -> {
                val host = prefs.twinHost
                val port = prefs.twinPort
                ({ TcpTransport(host, port) })
            }
            Prefs.Mode.HARDWARE -> {
                val dev = prefs.serialDevice
                val vendor = prefs.vendorPackage
                // Toque-Smart : carte du Monsieur Cuisine Smart (/dev/ttyMT1, 9600 bauds, protocole Smart).
                // Édition Ratatouille : refus d'ouvrir le port si Toque Smart pilote déjà le moteur (EX-02).
                ({ ExclusiveTransport(RootSerialTransport.forSmart(dev, vendor), listOf(Rivals.TOQUE_SMART)) })
            }
        }
        val l = MachineLoop(
            transportFactory = factory,
            config = ControllerConfig(requireBowl = prefs.requireBowl, hotRamp = prefs.hotRamp),
            // Smart : une trame toutes les 200 ms, comme l'appli d'origine (la carte répond en ~100 ms).
            periodMs = if (mode == Prefs.Mode.HARDWARE) 200L else 100L,
            onSnapshot = { s -> main.post { publish(s) } },
            onEvent = { e -> main.post { broadcastEvent(e) } },
            log = { line -> log.write("machine", line) },
        )
        loop = l
        l.start()
        log.write("machine", "boucle démarrée en mode ${mode.label}")
    }

    private fun publish(s: Snapshot) {
        val now = System.currentTimeMillis()
        val prev = lastSent
        val changed = prev == null || prev.copy(sampleAgeMs = 0, txFrames = 0, rxFrames = 0, txHex = "") !=
            s.copy(sampleAgeMs = 0, txFrames = 0, rxFrames = 0, txHex = "")
        if (!changed && now - lastSentAt < 1_000) return
        lastSent = s
        lastSentAt = now
        val m = Message.obtain(null, Ipc.SNAPSHOT)
        m.data = Ipc.snapshotToBundle(s)
        send(m)
        updateNotification(s)
    }

    private fun updateNotification(s: Snapshot) {
        val text = when {
            s.hasFault -> tr("Alerte : %s", s.faultMessage)
            s.state == JobState.RUNNING -> tr("%s · reste %s", s.jobLabel, formatClock(s.remainingMs))
            s.state == JobState.PAUSED -> tr("%s en pause", s.jobLabel)
            else -> s.link.label + if (s.transportName.isNotEmpty()) " · ${s.transportName}" else ""
        }
        if (text == lastNotified) return
        lastNotified = text
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification(tr("Base moteur"), text))
    }

    private fun broadcastEvent(e: MachineEvent) {
        val m = Message.obtain(null, Ipc.EVENT)
        m.data = Bundle().apply {
            putString("type", e.type.name)
            putString("message", e.message)
        }
        send(m)
        log.write("évènement", "${e.type} ${e.message}")
    }

    private fun send(template: Message) {
        val dead = ArrayList<Messenger>()
        for (c in clients) {
            try {
                val m = Message.obtain(template)
                c.send(m)
            } catch (_: RemoteException) {
                dead += c
            }
        }
        clients.removeAll(dead)
        template.recycle()
    }

    private fun reply(to: Messenger?, id: Int, v: Verdict) {
        if (to == null) return
        val m = Message.obtain(null, Ipc.REPLY, id, 0)
        m.data = Ipc.verdictToBundle(v)
        try {
            to.send(m)
        } catch (_: RemoteException) {
        }
    }

    private fun handle(msg: Message) {
        val l = loop ?: return
        val to = msg.replyTo
        val id = msg.arg1
        val data = msg.data
        data.getString(Ipc.LANG)?.let { if (it != I18n.code) I18n.select(it) }
        when (msg.what) {
            Ipc.REGISTER -> {
                if (to != null && clients.none { it.binder == to.binder }) clients += to
                lastSent = null
            }
            Ipc.UNREGISTER -> clients.removeAll { it.binder == to?.binder }
            Ipc.START_JOB -> {
                val spec = Ipc.bundleToJob(data)
                if (spec == null) reply(to, id, Verdict.refuse("ipc", tr("Programme illisible")))
                else l.post { c -> reply(to, id, c.startJob(spec)) }
            }
            Ipc.PAUSE -> l.post { c -> reply(to, id, c.pause()) }
            Ipc.RESUME -> {
                val hot = data.getBoolean("hot")
                l.post { c -> reply(to, id, c.resume(hot)) }
            }
            Ipc.STOP -> l.post { c -> reply(to, id, c.stop()) }
            Ipc.CLEAR -> l.post { c -> c.clearFinished(); reply(to, id, Verdict.OK) }
            Ipc.ADJUST -> {
                val speed = if (data.containsKey("speed")) data.getInt("speed") else null
                val temp = if (data.containsKey("temp")) data.getInt("temp") else null
                val dir = if (data.containsKey("reverse"))
                    (if (data.getBoolean("reverse")) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE) else null
                val total = if (data.containsKey("totalMs")) data.getLong("totalMs") else null
                val hot = data.getBoolean("hot")
                val smartLevel = if (data.containsKey("smartLevel")) data.getInt("smartLevel") else null
                l.post { c -> reply(to, id, c.adjust(speed, temp, dir, total, hot, smartLevel)) }
            }
            Ipc.TURBO_DOWN -> {
                val acc = try { Accessory.valueOf(data.getString("accessory") ?: "") } catch (_: Exception) { Accessory.BLADE }
                l.post { c -> reply(to, id, c.turboPress(acc)) }
            }
            Ipc.TURBO_UP -> l.post { c -> c.turboRelease() }
            Ipc.CONFIRM_COLD -> l.post { c -> reply(to, id, c.confirmCold()) }
            Ipc.TARE -> l.post { c -> c.tare() }
            Ipc.ACK_FAULT -> l.post { c -> reply(to, id, c.acknowledgeFault()) }
            Ipc.RECONFIGURE -> Thread {
                val busy = try { l.call { it.busy } } catch (_: Exception) { true }
                main.post {
                    if (busy) reply(to, id, Verdict.refuse("busy", tr("Impossible pendant un programme")))
                    else {
                        createLoop()
                        reply(to, id, Verdict.OK)
                    }
                }
            }.start()
            Ipc.SUSPEND_PORT -> Thread {
                val ok = try { l.suspendPort() } catch (_: Exception) { false }
                reply(to, id, if (ok) Verdict.OK else Verdict.refuse("busy", tr("Arrêtez d'abord le programme en cours")))
            }.start()
            Ipc.RESUME_PORT -> {
                l.resumePort()
                reply(to, id, Verdict.OK)
            }
            Ipc.SIM -> {
                val b = sim
                if (b != null) {
                    if (data.containsKey("lid")) b.lidOpen = data.getBoolean("lid")
                    if (data.containsKey("error")) b.errorCode = data.getInt("error")
                    if (data.containsKey("weight")) b.contentWeightG = data.getInt("weight")
                }
            }
        }
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) {
            ensureChannel()
            Notification.Builder::class.java.getConstructor(Context::class.java, String::class.java).newInstance(this, CHANNEL)
        } else Notification.Builder(this)
        return b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    /** Canal de notification (Android 8+), créé par réflexion pour rester compatible Android 6. */
    private fun ensureChannel() {
        try {
            val cls = Class.forName("android.app.NotificationChannel")
            val channel = cls.getConstructor(String::class.java, CharSequence::class.java, Int::class.javaPrimitiveType)
                .newInstance(CHANNEL, tr("Base moteur"), 2)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE)
            nm.javaClass.getMethod("createNotificationChannel", cls).invoke(nm, channel)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val CHANNEL = "machine"

        const val SCREEN_TIMEOUT_MS = 300_000

        fun formatClock(ms: Long): String {
            val s = (ms + 999) / 1000
            return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
            else String.format("%02d:%02d", s / 60, s % 60)
        }
    }
}
