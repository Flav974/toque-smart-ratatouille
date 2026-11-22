package app.ratatouille.core

import app.ratatouille.core.pack.PackImage
import app.ratatouille.core.pack.RatPack
import app.ratatouille.core.pack.RatPose
import app.ratatouille.core.pack.RatPose.Humeur
import app.ratatouille.core.home.Home
import app.ratatouille.core.cuisine.Fonctions.Fonction
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class RatPackTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun put(root: File, name: String, bytes: Int = 10): File =
        File(root, name).apply { parentFile.mkdirs(); writeBytes(ByteArray(bytes) { 1 }) }

    @Test fun noPackMeansNeutralImages() {
        for (pack in listOf(RatPack(null), RatPack(File(tmp.root, "absent")), RatPack(tmp.newFolder("vide")))) {
            for (img in PackImage.values()) assertNull(pack.find(img))
            assertEquals(PackImage.values().size, pack.missing().size)
            assertTrue(pack.present().isEmpty())
        }
        assertEquals("pack : absent (images neutres)", RatPack(null).summary())
    }

    @Test fun fileInsteadOfFolderIsIgnored() {
        val f = tmp.newFile("pack")
        assertNull(RatPack(f).find(PackImage.LOGO))
    }

    @Test fun findsImagesByPreferredExtension() {
        val root = tmp.newFolder("pack")
        put(root, "logo.png")
        put(root, "actions/mixe.jpg")
        put(root, "actions/mixe.webp")
        val pack = RatPack(root)
        assertEquals("logo.png", pack.find(PackImage.LOGO)!!.name)
        assertEquals("mixe.webp", pack.find(PackImage.MIXE)!!.name)
        assertEquals(listOf(PackImage.LOGO, PackImage.MIXE), pack.present())
        assertEquals("pack : 2/${PackImage.values().size} images", pack.summary())
    }

    @Test fun rejectsEmptyTooBigAndWrongFiles() {
        val root = tmp.newFolder("pack")
        put(root, "logo.png", 0)
        put(root, "demarrage.png", (RatPack.MAX_BYTES + 1).toInt())
        put(root, "rat-accueil.gif")
        File(root, "accueil-fond.png").mkdirs()
        val pack = RatPack(root)
        assertNull(pack.find(PackImage.LOGO))
        assertNull(pack.find(PackImage.DEMARRAGE))
        assertNull(pack.find(PackImage.RAT_ACCUEIL))
        assertNull(pack.find(PackImage.ACCUEIL_FOND))
        put(root, "demarrage.jpg", RatPack.MAX_BYTES.toInt())
        assertNotNull(pack.find(PackImage.DEMARRAGE))
    }

    @Test fun linkLeavingThePackIsIgnored() {
        val outside = put(tmp.root, "dehors.png")
        val root = tmp.newFolder("pack")
        try {
            Files.createSymbolicLink(File(root, "logo.png").toPath(), outside.toPath())
        } catch (_: Exception) {
            return // système sans liens symboliques
        }
        assertNull(RatPack(root).find(PackImage.LOGO))
    }

    @Test fun tenActionsAndUniqueNames() {
        assertEquals(10, PackImage.values().count { it.path.startsWith("actions/") })
        assertEquals(PackImage.values().size, PackImage.values().map { it.path }.toSet().size)
        for (img in PackImage.values()) assertTrue(img.path, img.path.matches(Regex("[a-z/-]+")))
    }
}

class RivalsTest {
    private val t = Rivals.TOQUE_SMART

    @Test fun toqueNotRunning() {
        assertNull(Rivals.refusal(t, "PID=\nPREFS=<string name=\"mode\">hardware</string>\n"))
        assertNull(Rivals.refusal(t, "PID=\n"))
    }

    @Test fun toqueDrivesTheMotor() {
        assertEquals(
            "app.toque.smart pilote déjà le moteur : arrêtez-le d'abord",
            Rivals.refusal(t, "PID=4321\nPREFS=<string name=\"mode\">hardware</string>\n"),
        )
    }

    @Test fun toqueInDemoOrTwinMode() {
        assertNull(Rivals.refusal(t, "PID=4321\nPREFS=<string name=\"mode\">demo</string>"))
        assertNull(Rivals.refusal(t, "PID=4321\nPREFS=<string name=\"mode\">twin</string>"))
        assertNull(Rivals.refusal(t, "PID=4321\nPREFS=")) // jamais réglé : Démo
        assertNull(Rivals.refusal(t, "PID=4321")) // pas de fichier de réglages
    }

    @Test fun unreadableCheckIsARefusal() {
        assertTrue(Rivals.refusal(t, "")!!.startsWith("Vérification impossible"))
        assertTrue(Rivals.refusal(t, "su: permission denied")!!.startsWith("Vérification impossible"))
        assertTrue(Rivals.refusal(t, "PID=abc")!!.startsWith("Vérification impossible"))
    }

    @Test fun probeCommandIsSafe() {
        val c = Rivals.probeCommand(t)
        assertTrue(c.contains("pidof app.toque.smart:machine"))
        assertTrue(c.contains("/data/data/app.toque.smart/shared_prefs/toque.xml"))
        for (bad in listOf("a; rm -rf /", "app.toque.smart `id`", "", "app")) {
            try {
                Rivals.probeCommand(bad)
                throw AssertionError("accepté : $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun probeCommandRunsInAShell() {
        // La commande tourne dans sh : on la joue sur le PC avec de faux réglages.
        val sh = File("/bin/sh")
        if (!sh.canExecute()) return
        val p = ProcessBuilder("sh", "-c", Rivals.probeCommand("app.introuvable.zz")).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        assertTrue(out, out.lines().any { it.startsWith("PID=") })
        assertNull(Rivals.refusal("app.introuvable.zz", out))
    }
}

class HomeTest {
    @Test fun splashLastsThreeAndAHalfSeconds() {
        assertFalse(app.ratatouille.core.home.Home.splashDone(0))
        assertFalse(app.ratatouille.core.home.Home.splashDone(3_499))
        assertTrue(app.ratatouille.core.home.Home.splashDone(3_500))
    }

    @Test fun greetingFollowsTheHour() {
        assertEquals("Bonjour !", app.ratatouille.core.home.Home.greeting(9))
        assertEquals("Bonjour !", app.ratatouille.core.home.Home.greeting(17))
        assertEquals("Bonsoir !", app.ratatouille.core.home.Home.greeting(18))
        assertEquals("Bonsoir !", app.ratatouille.core.home.Home.greeting(2))
    }

    @Test fun tilesAndButtonsMatchTheReferenceImage() {
        val tiles = app.ratatouille.core.home.Home.Tile.values()
        assertEquals(listOf("Hacher", "Mixer", "Cuire", "Mijoter", "Pétrir", "Cuisson vapeur"), tiles.map { it.label })
        assertEquals(6, tiles.map { it.image }.toSet().size)
        assertTrue(tiles.all { it.image.path.startsWith("tuiles/") })
        assertEquals(6, PackImage.values().count { it.path.startsWith("tuiles/") })
        assertEquals(listOf("Fonctions", "Mes recettes", "Favoris", "Nettoyage", "Réglages", "Applications"),
            app.ratatouille.core.home.Home.Button.values().map { it.label })
        // R2 (EX-20) : chaque tuile ouvre le réglage prérempli de sa fonction
        assertEquals(app.ratatouille.core.cuisine.Fonctions.TUILES, tiles.map { it.fonction })
    }
}

/** Phase R3 : le rat de chaque écran, son humeur, et l'image la plus proche quand une image manque. */
class RatPoseTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun everyFunctionHasARatFromTheList() {
        for (f in Fonction.values()) assertTrue(f.name, RatPose.pourFonction(f).path.startsWith("rat/"))
        assertEquals(PackImage.RAT_HACHER, RatPose.pourFonction(Fonction.TRANCHEUSE))
        assertEquals(PackImage.RAT_PETRIR, RatPose.pourFonction(Fonction.MALAXER_MOLLE))
        assertEquals(PackImage.RAT_NETTOYAGE, RatPose.pourFonction(Fonction.NETTOYAGE_LONG))
    }

    @Test fun recipeStepRatFollowsTheSetting() {
        assertEquals(PackImage.RAT_RECETTE, RatPose.pourEtape(null))
        assertEquals(PackImage.RAT_PETRIR, RatPose.pourEtape(StepMachine(program = StepProgram.KNEAD, seconds = 60, speed = 4)))
        assertEquals(PackImage.RAT_CUIRE, RatPose.pourEtape(StepMachine(seconds = 60, speed = 1, tempC = 100)))
        assertEquals(PackImage.RAT_MIXER, RatPose.pourEtape(StepMachine(seconds = 30, speed = 8)))
        assertEquals(PackImage.RAT_HACHER, RatPose.pourEtape(StepMachine(seconds = 5, speed = 5)))
    }

    @Test fun moodComesFromTheMachineState() {
        assertEquals(Humeur.REPOS, RatPose.humeur(Snapshot()))
        assertEquals(Humeur.ATTENTION, RatPose.humeur(Snapshot(faultCode = "COMM_LOST")))
        assertEquals(Humeur.ATTENTION, RatPose.humeur(Snapshot(hasSample = true, lidOpen = true)))
        assertEquals(Humeur.ATTENTE, RatPose.humeur(Snapshot(jobState = "PAUSED", pauseReason = "USER")))
        assertEquals(Humeur.ATTENTION, RatPose.humeur(Snapshot(jobState = "PAUSED", pauseReason = "LID")))
        assertEquals(Humeur.EN_COURS, RatPose.humeur(Snapshot(jobState = "RUNNING")))
        assertEquals(Humeur.PRET, RatPose.humeur(Snapshot(jobState = "FINISHED")))
    }

    @Test fun missingImageFallsBackToTheClosestOne() {
        val root = tmp.newFolder("pack")
        File(root, "rat").mkdirs()
        File(root, "rat/cuire.webp").writeBytes(ByteArray(10) { 1 })
        val pack = RatPack(root)
        // Pas de rat « saisir » : celui qui cuit le remplace.
        assertEquals(PackImage.RAT_CUIRE, pack.first(RatPose.choix(PackImage.RAT_SAISIR)))
        // Pas de rat « prêt » ni « attention » : le rat de l'action reste.
        assertEquals(PackImage.RAT_CUIRE, pack.first(RatPose.choix(PackImage.RAT_CUIRE, Humeur.PRET)))
        assertEquals(PackImage.RAT_CUIRE, pack.first(RatPose.choix(PackImage.RAT_CUIRE, Humeur.ATTENTION)))
        File(root, "rat/attention.webp").writeBytes(ByteArray(10) { 1 })
        assertEquals(PackImage.RAT_ATTENTION, pack.first(RatPose.choix(PackImage.RAT_CUIRE, Humeur.ATTENTION)))
        // Rien du tout : null, l'interface dessine la toque.
        assertNull(RatPack(tmp.newFolder("vide")).first(RatPose.choix(PackImage.RAT_MIXER)))
    }

    @Test fun homeDecorFollowsTheTimeOfDay() {
        assertEquals(Home.Moment.JOUR, Home.moment(12, 11))
        assertEquals(Home.Moment.COUCHER, Home.moment(16, 11))
        assertEquals(Home.Moment.COUCHER, Home.moment(17, 11))
        assertEquals(Home.Moment.NUIT, Home.moment(18, 11))
        assertEquals(Home.Moment.NUIT, Home.moment(6, 6))
        assertEquals(Home.Moment.JOUR, Home.moment(7, 6))
        assertEquals(Home.Moment.COUCHER, Home.moment(21, 6))
        assertEquals(Home.Moment.NUIT, Home.moment(22, 6))
        assertEquals(PackImage.RAT_VEILLE, Home.Moment.NUIT.rat.first())
    }
}
