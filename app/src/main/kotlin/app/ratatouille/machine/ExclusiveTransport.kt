package app.ratatouille.machine

import app.ratatouille.core.Rivals
import app.toque.core.i18n.tr
import app.toque.core.machine.RootShell
import app.toque.core.machine.Transport
import java.io.IOException

/**
 * Port série partagé avec Toque Smart installé à côté (EX-02) : avant d'ouvrir, vérifie qu'aucun rival
 * ne pilote déjà le moteur. Sinon, refus clair ; la boucle machine réessaiera plus tard.
 * Le moteur de Toque ([inner]) n'est pas modifié.
 */
class ExclusiveTransport(private val inner: Transport, private val rivals: List<String>) : Transport by inner {
    override fun open() {
        for (pkg in rivals) {
            val r = RootShell.run(Rivals.probeCommand(pkg), 10_000)
            val why = Rivals.refusal(pkg, r.out)
            if (why != null) throw IOException(tr(why))
        }
        inner.open()
    }
}
