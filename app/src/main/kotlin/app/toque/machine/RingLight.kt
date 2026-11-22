package app.toque.machine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.toque.core.machine.RootShell
import java.util.concurrent.Executors

/**
 * Anneau lumineux autour du bouton rotatif (Monsieur Cuisine Smart).
 *
 * C'est la LED RGB du noyau (`/sys/class/leds/{red,green,blue}`, 0..255), pilotée par Android et
 * non par la carte moteur. L'appli d'origine l'éteint quand l'écran s'éteint (`p5.c.K()`) et la
 * rallume en bleu fixe (50) au réveil. Toque fait de même : à l'extinction, il note les valeurs
 * puis met tout à 0 ; au réveil, il remet les valeurs notées, ou le bleu 50 si elles étaient nulles.
 * L'écriture passe par `su`, car SELinux interdit ces fichiers aux applis ordinaires.
 */
class RingLight(private val log: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ring-light").apply { isDaemon = true } }
    private var saved: List<Int>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> worker.execute { off() }
                Intent.ACTION_SCREEN_ON -> worker.execute { on() }
            }
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        context.registerReceiver(receiver, filter)
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        worker.shutdown()
    }

    private fun off() {
        val r = RootShell.run(
            "cd $DIR && cat red/brightness green/brightness blue/brightness && " +
                "echo 0 > red/brightness && echo 0 > green/brightness && echo 0 > blue/brightness",
            5_000,
        )
        val values = r.out.lines().mapNotNull { it.trim().toIntOrNull() }
        if (values.size == 3 && values.any { it > 0 }) saved = values
        log(if (r.ok) "anneau éteint (écran éteint), avant : $values" else "anneau : extinction en échec (${r.code}) ${r.err.trim()}")
    }

    private fun on() {
        val v = saved ?: IDLE
        val r = RootShell.run(
            "cd $DIR && echo ${v[0]} > red/brightness && echo ${v[1]} > green/brightness && echo ${v[2]} > blue/brightness",
            5_000,
        )
        log(if (r.ok) "anneau rallumé (écran allumé) : $v" else "anneau : rallumage en échec (${r.code}) ${r.err.trim()}")
    }

    private companion object {
        const val DIR = "/sys/class/leds"
        /** Repos de l'appli d'origine (`p5.c.B`) : canal « blue » à 50, blanc à l'œil sur ce robot. */
        val IDLE = listOf(0, 0, 50)
    }
}
