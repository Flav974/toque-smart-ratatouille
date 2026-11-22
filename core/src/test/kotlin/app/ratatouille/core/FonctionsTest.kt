package app.ratatouille.core

import app.ratatouille.core.cuisine.Fonctions
import app.ratatouille.core.cuisine.Fonctions.Etat
import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.ratatouille.core.cuisine.Fonctions.Sorte
import app.toque.core.SmartBench
import app.toque.core.SmartProtocolTest
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.JobState
import app.toque.core.machine.Phase
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Setting
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.NETTOYAGE_MAX_S
import app.toque.core.protocol.Operation
import app.toque.core.protocol.Smart
import app.toque.core.protocol.SmartCodec
import app.toque.core.protocol.SmartProgram
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase R2 (EX-20 à EX-22) : chaque fonction de l'Édition Ratatouille envoie, octet pour octet,
 * la trame de l'appli d'origine capturée le 2026-11-20 (`captures5/`, copiées dans
 * `core/src/test/resources/smart/`).
 *
 * Deux vérifications par fonction :
 *  1. avec la durée de la capture, le codec reproduit la trame capturée à l'identique (38 octets,
 *     séquence et somme comprises) ;
 *  2. lancée avec son réglage prérempli sur le banc (contrôleur + carte simulée), la fonction
 *     envoie en régime établi cette même trame, à la minuterie près (octets 25-27 = durée réglée),
 *     puis STOP à la fin.
 */
class FonctionsTest {

    /** Capture de référence de chaque fonction prête : fichier et heure de la première trame active. */
    private val reference = mapOf(
        Fonction.HACHER to ("06-v5-4s" to ""),
        Fonction.MIXER to ("12-mixer" to ""),
        Fonction.SMOOTHIE to ("14-eau" to "19:28"),
        Fonction.TRANCHEUSE to ("13-trancheuse" to ""),
        Fonction.MALAXER_LIQUIDE to ("11-malaxer-liquide" to ""),
        Fonction.CUISSON_PERSO to ("03-rotation-v1-30s" to ""),
        Fonction.CUIRE to ("14-eau" to "19:17"),
        Fonction.VAPEUR to ("14-eau" to "19:20"),
        Fonction.OEUFS to ("14-eau" to "19:23"),
        Fonction.RIZ to ("14-eau" to "19:30"),
        Fonction.EAU to ("14-eau" to "19:31"),
        Fonction.SOUS_VIDE to ("14-eau" to "19:24"),
    )

    /**
     * Programmes à étapes (capture `16-etapes-mc30` du 2026-11-21) : début et fin de la fenêtre où
     * l'appli d'origine les a fait tourner. Mijoter, Fermenter et Saisir ont été arrêtés à la main.
     */
    private val etapes = mapOf(
        Fonction.MALAXER_FERME to Fenetre("16:39:00", "16:41:03", complet = true),
        Fonction.SAISIR to Fenetre("16:43:45", "16:45:08", complet = false),
        Fonction.MIJOTER to Fenetre("16:45:54", "16:57:50", complet = false),
        Fonction.FERMENTER to Fenetre("16:58:48", "17:05:05", complet = false),
        Fonction.PRELAVAGE to Fenetre("17:06:31", "17:08:03", complet = true),
        Fonction.NETTOYAGE_LONG to Fenetre("17:09:44", "17:12:25", complet = true),
        Fonction.MALAXER_MOLLE to Fenetre("17:20:06", "17:22:08", complet = true),
    )

    private class Fenetre(val de: String, val a: String, val complet: Boolean)

    /** Suite de trames identiques (hors séquence, minuterie et somme). */
    private class Etape(val key: List<Byte>, val debutMs: Long, var finMs: Long, var minuterieS: Int)

    /** Découpe des trames actives (STOP exclus) en étapes ; [t] = instant de chaque trame en ms. */
    private fun decoupe(trames: List<Pair<Long, ByteArray>>): List<Etape> {
        val out = ArrayList<Etape>()
        for ((t, f) in trames) {
            if (f.size != Smart.TX_SIZE) continue
            val c = Smart.decodeTx(f)
            if (c.function == 0 || c.cmd != Smart.CMD_SET) continue
            val k = key(f)
            val last = out.lastOrNull()
            if (last != null && last.key == k) { last.finMs = t + 200; last.minuterieS = durationS(f) }
            else out += Etape(k, t, t + 200, durationS(f))
        }
        return out
    }

    private fun ms(h: String): Long {
        val (hh, mm, ss) = h.split(':')
        return ((hh.toLong() * 60 + mm.toLong()) * 60) * 1000 + (ss.toDouble() * 1000).toLong()
    }

    private fun etapesCapturees(fen: Fenetre): List<Etape> = decoupe(
        SmartProtocolTest.capture("16-etapes-mc30")
            .filter { it.dir == "TX" && it.time >= fen.de && it.time <= fen.a }
            .map { ms(it.time) to it.raw })

    private fun firstActive(name: String, from: String): ByteArray =
        SmartProtocolTest.capture(name).first {
            it.dir == "TX" && it.time >= from && it.raw.size == Smart.TX_SIZE && Smart.decodeTx(it.raw).function != 0
        }.raw

    private fun durationS(f: ByteArray) = Smart.decodeTx(f).let { it.hours * 3600 + it.minutes * 60 + it.seconds }

    /** Réglage réellement envoyé pour [spec] (après mise au format du Smart, comme au démarrage). */
    private fun wireSetting(spec: JobSpec): Setting = Programs.forSmart(spec).phases.single { !it.setting.isRest }.setting

    /** Charge utile hors séquence, minuterie et somme (octets 8-24 et 28-36). */
    private fun key(f: ByteArray) = (8 until Smart.TX_L).filter { it !in 25..27 }.map { f[it] }

    @Test fun everyReadyFunctionHasAReferenceCapture() {
        assertTrue(reference.keys.none { it in etapes })
        for (f in Fonction.values()) {
            val ready = f.etat == Etat.PRETE && f.sorte !in setOf(Sorte.TURBO, Sorte.BALANCE)
            assertEquals("capture de référence de $f", ready, f in reference || f in etapes)
            if (f.etat != Etat.PRETE) {
                assertFalse(f.demarrable)
                assertNull("$f ne doit rien envoyer", Fonctions.job(f, f.seconds))
                assertTrue("$f : raison affichée", f.raison.isNotEmpty())
            }
        }
    }

    @Test fun codecRebuildsEachCapturedFrameByteForByte() {
        for ((f, ref) in reference) {
            val captured = firstActive(ref.first, ref.second)
            val spec = Fonctions.job(f, f.seconds, hotBlendConfirmed = true)!!
            val s = wireSetting(spec)
            val seq = captured[7].toInt() and 0xFF
            val codec = SmartCodec(initialSeq = if (seq == 103) 101 else (seq + 255) % 256)
            // Même réglage, durée de la capture : trame identique, octet pour octet.
            val frame = codec.encodeTx(s.operation, s.speed, s.temperatureLevel, s.direction, false, durationS(captured), s.program, s.smartLevel)
            assertEquals("$f", Mcc.hex(captured), Mcc.hex(frame))
        }
    }

    @Test fun eachFunctionRunsWithItsPresetAndSendsTheCapturedFrame() {
        for ((f, ref) in reference) {
            val captured = firstActive(ref.first, ref.second)
            val b = SmartBench().ready()
            val spec = Fonctions.job(f, f.dureeProposee, hotBlendConfirmed = true)!!
            val v = b.c.startJob(spec)
            assertTrue("$f : ${v.message}", v.ok)
            // Programmes longs : quelques secondes suffisent (la trame ne change pas).
            b.step(minOf(f.dureeProposee * 1000L, 6_000L))
            val active = b.raw.filter { it.size == Smart.TX_SIZE && Smart.decodeTx(it).function != 0 }
            assertTrue("$f : aucune trame active", active.isNotEmpty())
            // octets réellement émis par le contrôleur, comparés aux octets capturés
            val steady = active.last()
            assertEquals("$f : même trame que l'appli d'origine", key(captured), key(steady))
            assertEquals("$f : minuterie = durée réglée", f.dureeProposee, durationS(steady))
            // Programmes automatiques : une seule trame, renvoyée telle quelle (même séquence).
            if (f.sorte != Sorte.MANUEL) assertEquals("$f : une seule trame", 1, active.map { it[7] }.toSet().size)
        }
    }

    // ------------------------------------------------------------------ programmes à étapes (R2)

    @Test fun eachCapturedStepFrameIsRebuiltByteForByte() {
        // Chaque trame de SmartProgram, à l'heure de sa capture : 38 octets identiques
        // (séquence, minuterie et somme comprises).
        for (p in SmartProgram.values()) {
            val (fic, heure) = p.capture.split(' ')
            val captured = firstActive(fic, heure)
            val s = Programs.programSetting(p)
            val seq = captured[7].toInt() and 0xFF
            val codec = SmartCodec(initialSeq = if (seq == 103) 101 else (seq + 255) % 256)
            val frame = codec.encodeTx(s.operation, s.speed, s.temperatureLevel, s.direction, false, durationS(captured), s.program, s.smartLevel)
            assertEquals("$p (${p.capture})", Mcc.hex(captured), Mcc.hex(frame))
        }
    }

    /** Fait tourner [f] sur le banc pendant [ms] ; [chauffe] = instant (ms après le départ) du bit « température atteinte ». */
    private fun banc(f: Fonction, ms: Long, chauffe: Long? = null): Pair<SmartBench, List<Etape>> {
        val b = SmartBench().ready()
        val spec = Fonctions.job(f, f.dureeProposee, hotBlendConfirmed = true)!!
        val v = b.c.startJob(spec)
        assertTrue("$f : ${v.message}", v.ok)
        val debut = b.raw.size
        var t = 0L
        while (t < ms) {
            if (chauffe != null && t >= chauffe) b.board.aboveTarget = true
            b.step(200); t += 200
        }
        val trames = (debut until b.raw.size).map { b.tx[it].first to b.raw[it] }
        return b to decoupe(trames)
    }

    @Test fun stepProgramsFollowTheOriginalRhythm() {
        for ((f, fen) in etapes) {
            val cap = etapesCapturees(fen)
            val dureeCap = ms(fen.a) - ms(fen.de)
            // nettoyage long : la carte a signalé « température atteinte » 25 s après le début de la préchauffe
            val chauffe = if (f == Fonction.NETTOYAGE_LONG) 55_000L else null
            val (b, nous) = banc(f, if (fen.complet) dureeCap + 10_000 else dureeCap, chauffe)
            val n = cap.size
            assertTrue("$f : ${n} étapes capturées", n >= 2)
            // mêmes trames, dans le même ordre (STOP mis à part : voir plus bas)
            val nos = if (fen.complet) nous else nous.take(n)
            assertEquals("$f : suite des trames", cap.map { it.key }, nos.map { it.key })
            for (i in 0 until n) {
                // durée de chaque étape (la dernière d'un programme arrêté à la main n'est pas comparée)
                if (i < n - 1 || fen.complet) {
                    val dc = cap[i].finMs - cap[i].debutMs
                    val dn = nos[i].finMs - nos[i].debutMs
                    assertTrue("$f, étape ${i + 1} : ${dn} ms au lieu de ${dc} ms", kotlin.math.abs(dc - dn) <= 2_000)
                }
                // minuterie : même baisse depuis la première étape (temps restant, ou valeur fixe)
                val bc = cap[0].minuterieS - cap[i].minuterieS
                val bn = nos[0].minuterieS - nos[i].minuterieS
                assertTrue("$f, étape ${i + 1} : minuterie -$bn s au lieu de -$bc s", kotlin.math.abs(bc - bn) <= 2)
            }
            if (fen.complet) {
                assertEquals("$f : terminé", JobState.FINISHED.name, b.snap.jobState)
                assertEquals("$f : STOP à la fin", 0, b.last().function)
            }
        }
    }

    @Test fun aRestKeepsTheFunctionAndTheHeatInsteadOfStop() {
        // Mijoter : pendant le repos, la trame garde la fonction 10, le niveau et les 90 °C, vitesse 0.
        val (_, nous) = banc(Fonction.MIJOTER, 40_000)
        assertEquals(2, nous.size)
        val repos = nous[1].key
        val brasse = nous[0].key
        assertEquals(brasse.indices.filter { brasse[it] != repos[it] }, listOf(16 - 8))  // seul l'octet 16 (vitesse) change
        assertEquals(0.toByte(), repos[16 - 8])
        // Malaxer : du repos au sens inverse sans STOP (la lame est déjà arrêtée), comme l'appli d'origine
        val (b, _) = banc(Fonction.MALAXER_FERME, 125_000)
        val actives = b.tx.dropWhile { it.second.function == 0 }.takeWhile { it.second.function != 0 }
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertTrue("aucun STOP au milieu du malaxage", actives.size >= 590)
    }

    @Test fun aReversalThroughAShortRestWaitsForTheBlade() {
        // v4 inverse, repos de 200 ms déjà dans le sens normal, puis v4 : le repos dure jusqu'à l'arrêt de la lame
        val spec = Programs.smartMalaxerMolle(120_000).let { m ->
            m.copy(phases = listOf(m.phases[0], m.phases[0].copy(setting = Programs.programSetting(SmartProgram.MALAXER_REPOS), durationMs = 200), m.phases[2]),
                hotBlendConfirmed = true)
        }
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(spec).ok)
        b.step(15_000)
        val avant = b.tx.size
        b.step(6_000)
        val repos = b.tx.drop(avant).dropWhile { it.second.speedHalfSteps > 0 }.takeWhile { it.second.speedHalfSteps == 0 }
        assertTrue("repos prolongé jusqu'à l'arrêt confirmé (${repos.size} trames)", repos.size >= 8)
        assertTrue(repos.all { it.second.function == 5 })
        // le temps de repos prolongé ne raccourcit pas la dernière étape
        b.step(80_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        val v4 = b.tx.filter { it.second.function == 5 && it.second.speedHalfSteps == 8 && !it.second.reverse }
        assertTrue("${v4.size} trames v4", v4.size >= 345)
    }

    @Test fun cleaningPreheatEndsOnTheTemperatureBitOrAfterFifteenMinutes() {
        // bit jamais levé : préchauffe de 15 min au plus, puis lavage, puis STOP
        val (b, nous) = banc(Fonction.NETTOYAGE_LONG, (NETTOYAGE_MAX_S + 20) * 1000L)
        assertEquals(7, nous.size)
        val chauffe = nous[1]
        assertTrue(kotlin.math.abs(chauffe.finMs - chauffe.debutMs - 15 * 60_000L) <= 1_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        // bit levé : la préchauffe s'arrête dès la réponse qui le porte
        val (_, vite) = banc(Fonction.NETTOYAGE_LONG, 60_000, chauffe = 40_000)
        assertTrue(vite[1].finMs - vite[1].debutMs in 9_000L..11_500L)
        // la minuterie est fixe : 15 min 30 puis 1 min 45, comme l'appli d'origine
        assertEquals(listOf(930, 930, 105, 105, 105, 105, 105), nous.map { it.minuterieS })
    }

    @Test fun anotherKneadingWaitsThirtyMinutes() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Fonctions.job(Fonction.MALAXER_FERME, 120, hotBlendConfirmed = true)!!).ok)
        b.step(125_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        for (f in listOf(Fonction.MALAXER_FERME, Fonction.MALAXER_MOLLE, Fonction.MALAXER_LIQUIDE)) {
            val v = b.c.startJob(Fonctions.job(f, 60, hotBlendConfirmed = true)!!)
            assertTrue("$f", v.has(app.toque.core.machine.MachineController.KNEAD_COOLDOWN))
        }
        // les autres fonctions restent possibles
        assertTrue(b.c.startJob(Fonctions.job(Fonction.MIXER, 30, hotBlendConfirmed = true)!!).ok)
        b.c.stop(); b.step(3_000); b.c.clearFinished()
        b.step(30 * 60_000L)
        assertTrue(b.c.startJob(Fonctions.job(Fonction.MALAXER_MOLLE, 60, hotBlendConfirmed = true)!!).ok)
        b.step(65_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        b.c.clearFinished()
        // une étape « Pétrir » de recette n'attend pas (l'attente n'est posée que par l'écran Fonctions)
        val recette = b.c.startJob(Programs.knead(45_000).copy(hotBlendConfirmed = true))
        assertTrue(recette.message, recette.ok)
        b.step(48_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        // un malaxage arrêté avant 30 s ne compte pas
        val b2 = SmartBench().ready()
        assertTrue(b2.c.startJob(Fonctions.job(Fonction.MALAXER_FERME, 120, hotBlendConfirmed = true)!!).ok)
        b2.step(10_000); b2.c.stop(); b2.step(3_000); b2.c.clearFinished()
        assertTrue(b2.c.startJob(Fonctions.job(Fonction.MALAXER_FERME, 120, hotBlendConfirmed = true)!!).ok)
    }

    @Test fun kneadingAndCleaningAskTheHotContentConfirmation() {
        // Comme l'alerte « vitesse élevée + contenu très chaud » de l'appli d'origine (vitesse ≥ 4, 60 °C) :
        // température inconnue sur le Smart, la confirmation est demandée avant tout mouvement.
        for (f in listOf(Fonction.MALAXER_FERME, Fonction.MALAXER_MOLLE, Fonction.PRELAVAGE, Fonction.NETTOYAGE_LONG)) {
            val b = SmartBench().ready()
            val v = b.c.startJob(Fonctions.job(f, f.dureeProposee)!!)
            assertTrue("$f", v.has(SafetyPolicy.HOT_BLEND))
            b.step(1_000)
            assertTrue("$f : rien n'est envoyé avant la confirmation", b.tx.all { it.second.function == 0 })
        }
        // Mijoter, Fermenter, Saisir (vitesse 1) : pas de confirmation
        for (f in listOf(Fonction.MIJOTER, Fonction.FERMENTER, Fonction.SAISIR))
            assertTrue("$f", SmartBench().ready().c.startJob(Fonctions.job(f, f.dureeProposee)!!).ok)
    }

    @Test fun cleaningStepsNeverHeatAboveFortyDegrees() {
        for (p in SmartProgram.values().filter { it.nettoyage }) assertTrue("$p", Programs.programSetting(p).temperatureC <= 40)
        val hot = Programs.smartPrelavage("x").let { s ->
            s.copy(phases = s.phases.map { it.copy(setting = it.setting.copy(program = SmartProgram.MIJOTER_BRASSE)) })
        }
        assertFalse(SafetyPolicy.checkJob(hot, 20).isEmpty())
    }

    @Test fun shortFunctionsEndWithStop() {
        for (f in listOf(Fonction.HACHER, Fonction.MIXER, Fonction.TRANCHEUSE)) {
            val b = SmartBench().ready()
            assertTrue(b.c.startJob(Fonctions.job(f, f.seconds, hotBlendConfirmed = true)!!).ok)
            b.step(f.seconds * 1000L + 3_000L)
            assertEquals("$f", JobState.FINISHED.name, b.snap.jobState)
            assertEquals("$f", 0, b.last().function)
        }
    }

    @Test fun fortyDegreesAndReverseOfTheCustomCooking() {
        // 40 °C, vitesse 1, 4 s : niveau 2 du Smart (capture 14-eau, 19:19)
        val c40 = firstActive("14-eau", "19:19")
        val spec = Fonctions.manuel(1, 2, false, 4, "40 °C")
        val s = spec.phases.single().setting
        assertEquals(2, s.smartLevel)
        assertEquals("traité comme 37 °C par les règles de sécurité", 1, s.temperatureLevel)
        assertEquals(40, s.temperatureC)
        val seq = c40[7].toInt() and 0xFF
        val frame = SmartCodec(initialSeq = seq - 1).encodeTx(s.operation, s.speed, s.temperatureLevel, s.direction, false, 4, s.program, s.smartLevel)
        assertArrayEquals(c40, frame)
        // 70 °C : niveau commun aux deux tables, pas de niveau Smart joint (capture 19:18)
        val s70 = Fonctions.manuel(1, 8, false, 4, "70 °C").phases.single().setting
        assertNull(s70.smartLevel)
        val c70 = firstActive("14-eau", "19:18")
        assertArrayEquals(c70, SmartCodec(initialSeq = (c70[7].toInt() and 0xFF) - 1).encodeTx(s70.operation, s70.speed, s70.temperatureLevel, s70.direction, false, 4))
        // vitesse 2 en sens inverse (capture 07)
        val rev = Fonctions.manuel(2, 0, true, 4, "inverse").phases.single().setting
        val c07 = firstActive("07-v2-inverse", "")
        assertArrayEquals(c07, SmartCodec(initialSeq = (c07[7].toInt() and 0xFF) - 1).encodeTx(rev.operation, rev.speed, rev.temperatureLevel, rev.direction, false, 4))
        // sur le banc : la cuisson à 40 °C démarre et envoie le niveau 2
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(spec).ok)
        b.step(2_000)
        assertEquals(2, b.last().temperatureLevel)
        assertEquals(40, b.snap.jobTempC)
    }

    @Test fun everySmartTemperatureLevelIsReachableInCustomCooking() {
        for (level in Smart.TEMPERATURE_LEVELS.indices) {
            val speed = if (level == 0) 5 else 1
            val spec = Fonctions.manuel(speed, level, false, 60, "t")
            val b = SmartBench().ready()
            val v = b.c.startJob(spec.copy(hotBlendConfirmed = true))
            assertTrue("niveau $level : ${v.message}", v.ok)
            b.step(1_400)
            assertEquals("niveau $level", level, b.last().temperatureLevel)
            assertEquals(Smart.celsiusForLevel(level), if (level == 0) 0 else b.snap.jobTempC)
        }
    }

    @Test fun fortyDegreesCanBeChosenWhileCooking() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Fonctions.manuel(1, 0, false, 60, "t")).ok)
        b.step(1_000)
        assertTrue(b.c.adjust(temperatureLevel = 1, smartLevel = 2).ok)
        b.step(1_000)
        assertEquals(2, b.last().temperatureLevel)
        // retour à 70 °C : le niveau du Smart n'est plus joint
        assertTrue(b.c.adjust(temperatureLevel = Mcc.levelForCelsius(70)).ok)
        b.step(1_000)
        assertEquals(8, b.last().temperatureLevel)
        // incohérent : refusé, rien n'est corrigé en silence
        assertFalse(b.c.adjust(temperatureLevel = 5, smartLevel = 2).ok)
    }

    @Test fun tilesOpenTheirPreset() {
        assertEquals(6, Fonctions.TUILES.size)
        assertEquals(listOf(Fonction.HACHER, Fonction.MIXER, Fonction.CUIRE, Fonction.MIJOTER, Fonction.MALAXER_FERME, Fonction.VAPEUR), Fonctions.TUILES)
        for (f in Fonctions.TUILES) {
            assertTrue(f.label.isNotEmpty())
            assertTrue("$f : préréglage", f.seconds > 0 && f.origine.isNotEmpty())
        }
        assertEquals(3, Fonctions.variantes(Fonction.MALAXER_FERME).size)
    }

    @Test fun presetsAreTheOriginalAppValues() {
        // Valeurs de b3.d1 (appli d'origine 2.21.0.0) : durée, vitesse, °C, sens.
        data class P(val s: Int, val speed: Int, val c: Int, val rev: Boolean)
        fun Fonction.p() = P(seconds, speed, celsius, reverse)
        assertEquals(P(30, 8, 0, false), Fonction.MIXER.p())
        assertEquals(P(60, 10, 0, false), Fonction.SMOOTHIE.p())
        assertEquals(P(30, 5, 0, false), Fonction.TRANCHEUSE.p())
        assertEquals(P(20 * 60, 1, 120, false), Fonction.VAPEUR.p())
        assertEquals(P(7 * 60, 1, 130, true), Fonction.SAISIR.p())
        assertEquals(P(8 * 60, 1, 130, false), Fonction.OEUFS.p())
        assertEquals(P(30 * 60, 1, 100, true), Fonction.RIZ.p())
        assertEquals(P(10 * 60, 1, 100, true), Fonction.EAU.p())
        assertEquals(P(6 * 3600, 1, 60, true), Fonction.SOUS_VIDE.p())
        assertEquals(P(4 * 3600, 1, 90, true), Fonction.MIJOTER.p())
        assertEquals(P(8 * 3600, 1, 40, true), Fonction.FERMENTER.p())
        // la fiche du programme dit la même chose que la trame capturée
        for (f in Fonction.values()) {
            val p = f.program ?: continue
            assertEquals("$f", p.speed, f.speed)
            assertEquals("$f", p.reverse, f.reverse)
            assertEquals("$f", p.targetCelsius, f.celsius)
            assertEquals("$f", p.defaultS, f.seconds)
            assertEquals("$f", p.minS, f.minS)
            assertEquals("$f", p.maxS, f.maxS)
        }
    }

    // ------------------------------------------------------------------ sécurités (EX-24)

    @Test fun programsAcceptOnlyTheirCapturedSetting() {
        val c = SmartCodec()
        for (p in SmartProgram.values()) {
            val s = Programs.programSetting(p)
            assertTrue("$p", c.accepts(s.operation, s.speed, s.temperatureLevel, s.direction, s.program, s.smartLevel))
            // vitesse valide (0..10) mais différente de celle du programme
            assertFalse("$p vitesse", c.accepts(s.operation, s.speed - 1, s.temperatureLevel, s.direction, p))
            assertFalse("$p sens", c.accepts(s.operation, s.speed, s.temperatureLevel,
                if (p.reverse) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE, p))
            assertFalse("$p température", c.accepts(s.operation, s.speed, (s.temperatureLevel + 1) % 20, s.direction, p))
            assertFalse("$p opération", c.accepts(if (p.operation == Operation.KNEAD) Operation.STANDARD else Operation.KNEAD, s.speed, s.temperatureLevel, s.direction, p))
            assertFalse("$p + niveau", c.accepts(s.operation, s.speed, s.temperatureLevel, s.direction, p, 2))
            // le Connect refuse tout programme du Smart
            assertFalse(app.toque.core.protocol.ConnectCodec.accepts(s.operation, s.speed, s.temperatureLevel, s.direction, p))
        }
        assertFalse(c.accepts(Operation.STANDARD, 1, 0, Direction.CLOCKWISE, null, 2))   // 40 °C ⇒ niveau Toque 1
        assertFalse(c.accepts(Operation.STANDARD, 1, 1, Direction.CLOCKWISE, null, 21))
        assertFalse(c.accepts(Operation.KNEAD, 4, 1, Direction.CLOCKWISE, null, 2))
    }

    @Test fun safetyRefusesAProgramWithAnotherSetting() {
        val p = SmartProgram.EGGS
        val good = Programs.smartProgram(p, 60_000, "œufs")
        assertTrue(SafetyPolicy.checkJob(good, 20).isEmpty())
        fun with(s: Setting) = good.copy(phases = listOf(Phase(s, 60_000)))
        val base = good.phases.single().setting
        assertFalse(SafetyPolicy.checkJob(with(base.copy(speed = 3)), 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(with(base.copy(direction = Direction.COUNTER_CLOCKWISE)), 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(with(base.copy(program = null)), 20).isEmpty())
        assertFalse("programme dans un travail manuel", SafetyPolicy.checkJob(good.copy(kind = JobKind.MANUAL), 20).isEmpty())
        // durée maximale de l'appli d'origine
        assertTrue(SafetyPolicy.checkJob(Programs.smartProgram(p, p.maxS * 1000L, "x"), 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(Programs.smartProgram(p, p.maxS * 1000L + 1000, "x"), 20).isEmpty())
        // sous-vide : jamais plus que la limite de Toque (99 min) tant que l'essai de la carte muette n'est pas fait
        assertTrue(SafetyPolicy.checkJob(Programs.smartProgram(SmartProgram.SOUS_VIDE, 99 * 60_000L, "x"), 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(Programs.smartProgram(SmartProgram.SOUS_VIDE, 99 * 60_000L + 1000, "x"), 20).isEmpty())
        assertEquals(99 * 60, Fonction.SOUS_VIDE.maxEffectifS)
        assertEquals(99 * 60, Fonction.SOUS_VIDE.dureeProposee)
        // Malaxer pâte molle : vitesse 4 en sens inverse acceptée (fonction 5), vitesse 5 jamais
        val soft = Programs.smartKnead(true, 60_000)
        assertTrue(SafetyPolicy.checkJob(soft, 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(soft.copy(phases = listOf(Phase(soft.phases.single().setting.copy(speed = 5), 60_000))), 20).isEmpty())
        assertFalse(SafetyPolicy.checkJob(soft.copy(phases = listOf(Phase(soft.phases.single().setting.copy(temperatureLevel = 1), 60_000))), 20).isEmpty())
        // vitesse rapide et contenu chaud : confirmation demandée (Smoothie, Mixer)
        val v = SafetyPolicy.checkJob(Programs.smartProgram(SmartProgram.SMOOTHIE, 60_000, "s"), 70)
        assertTrue(v.any { it.code == SafetyPolicy.HOT_BLEND })
    }

    @Test fun smoothieAndMixerAskForTheHotBlendConfirmationOnTheSmart() {
        // le Smart ne mesure pas toujours la température : contenu traité comme chaud
        for (f in listOf(Fonction.MIXER, Fonction.SMOOTHIE, Fonction.HACHER, Fonction.MALAXER_LIQUIDE)) {
            val b = SmartBench().ready()
            val v = b.c.startJob(Fonctions.job(f, f.seconds)!!)
            assertTrue("$f", v.has(SafetyPolicy.HOT_BLEND))
            b.step(1_000)
            assertTrue("$f : rien n'est envoyé avant la confirmation", b.tx.all { it.second.function == 0 })
        }
    }

    @Test fun lidOpenRefusesEveryFunctionAndStopsARunningOne() {
        for (f in reference.keys + etapes.keys) {
            val b = SmartBench().ready()
            b.board.lidOpen = true
            b.step(400)
            val v = b.c.startJob(Fonctions.job(f, f.seconds, hotBlendConfirmed = true)!!)
            assertTrue("$f", v.has("lid"))
        }
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Fonctions.job(Fonction.SOUS_VIDE, Fonction.SOUS_VIDE.dureeProposee)!!).ok)
        b.step(2_000)
        assertEquals(9, b.last().function)
        b.board.lidOpen = true
        b.step(400)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals(0, b.last().function)
    }

    @Test fun silentBoardStopsAProgram() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Fonctions.job(Fonction.RIZ, Fonction.RIZ.seconds)!!).ok)
        b.step(1_000)
        b.board.silent = true
        b.step(5_000)
        assertTrue(b.snap.hasFault)
        assertEquals(0, b.last().function)
    }

    @Test fun durationsAreReadable() {
        assertEquals("45 s", Fonctions.texteDuree(45))
        assertEquals("2 min 30 s", Fonctions.texteDuree(150))
        assertEquals("8 h", Fonctions.texteDuree(8 * 3600))
        assertEquals("6 h 15", Fonctions.texteDuree(6 * 3600 + 15 * 60))
        assertEquals("02:30" to "", Fonctions.horloge(150))
        assertEquals("4:00" to "h", Fonctions.horloge(4 * 3600))
    }

    @Test fun programJobsSurviveTheIpcFields() {
        // Le passage entre processus se fait par les noms des programmes : tous doivent être uniques et stables.
        val names = SmartProgram.values().map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (p in SmartProgram.values()) assertEquals(p, SmartProgram.valueOf(p.name))
    }
}
