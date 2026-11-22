# Phase 4 — Toque sur le Monsieur Cuisine Smart (SKMS 1200A1) : compte rendu

Date : 2026-11-20. État : **FAITE**. Toque-Smart est installé sur le robot. Il affiche le poids et l'état
du couvercle en direct. Il a fait tourner le moteur et chauffé, avec l'accord explicite de
l'utilisateur pour chaque essai.

## 1. Compilation (sur le PC, sans téléchargement)

- Outils déjà présents : OpenJDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`), SDK Android
  `~/Android/Sdk` (platform 35, build-tools 35.0.0), Gradle 8.11.1 dans le cache.
- Pour compiler hors ligne, la copie de travail utilise des versions un peu plus anciennes que le Toque
  d'origine :
  - AGP 8.7.3 (au lieu de 8.6.1) ;
  - Kotlin 2.0.21 (au lieu de 2.1.21) ;
  - Gradle 8.11.1 (au lieu de 8.13) ;
  - compileSdk 35.
  
  Les greffons sont résolus sans « plugin marker » (`settings.gradle.kts`). `local.properties` donne le chemin du SDK.
- Commande :
  ```
  cd <dossier>/toque-smart
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline :core:test :app:assembleRelease
  ```
- Résultat : 82 tests unitaires OK (1 ignoré : jumeau numérique absent).
- APK : `app/build/outputs/apk/release/app-release.apk`.
  - Paquet `app.toque.smart`, version 0.5.1-smart.1.
  - SHA-256 `0128a2141bfb20e4d3f36ac1f07c390879bc0ba45a00b5f7447116e8768b1823`.

## 2. Ce qui a changé par rapport à Toque (Connect)

| Fichier | Changement |
|---|---|
| `core/.../protocol/BoardCodec.kt` (nouveau) | Interface « format de carte » : `ConnectCodec` (Toque d'origine, inchangé) et `SmartCodec`. |
| `core/.../protocol/Smart.kt` (nouveau) | Protocole Smart : trames TX de 38 octets, RX de 53 octets, découpeur de flux, décodage vers `Telemetry`. Numérotation comme l'appli d'origine (+1 par nouvelle trame, 102 sauté, même trame et même séquence répétées pendant un programme). Niveaux de température Smart (21, avec 40 °C) obtenus à partir de la table de Toque. Seules les commandes `08 02` et `08 03` peuvent être produites, **jamais `FE xx`**. Seules STOP et la cuisson manuelle sont autorisées (turbo, pétrin, vapeur, rissoler : refusés tant qu'ils ne sont pas capturés). |
| `MachineController.kt` | Le codec vient de la liaison. Tare envoyée seulement entre deux STOP. Attente de 2 s après le dernier écho « moteur en marche » avant de redémarrer ou d'inverser (le Smart ne mesure pas le régime). Température traitée comme « peut-être chaude » sur le Smart (elle lit 0 tant qu'aucune chauffe n'a eu lieu) : confirmation au-delà de la vitesse 3 et montée progressive. Durée du programme envoyée dans la trame. Réglage refusé par le codec = alerte + STOP. |
| `MachineLoop.kt` | STOP avant de fermer une liaison. Après `emergencyStop()`, uniquement des STOP. Une erreur interne de boucle déclenche une alerte. |
| `RootSerial.kt` | `RootSerialTransport.forSmart()` : `/dev/ttyMT1`, 9600 bauds, arrêt de `com.tecpal.device.mc30` et `com.tecpal.testapp.mc30`. **Le pont shell envoie 3 STOP lui-même quand Toque s'arrête** (fermeture, plantage, processus tué). |
| `MachineService.kt` | Mode « Machine réelle » = Smart, une trame toutes les 200 ms (comme l'appli d'origine). |
| `Prefs.kt`, `LidlSync.kt` | Valeurs par défaut Smart. Le service à couper dans l'appli d'origine est `SerialPortMC30Service`. |
| `AndroidManifest.xml` | Nom « Toque Smart ». **`android:vmSafeMode="true"`** (voir § 5). |
| `build.gradle.kts` (app) | `applicationId` `app.toque.smart` : une version Connect ne peut jamais remplacer Toque-Smart. Les mises à jour automatiques depuis GitHub sont désactivées. |
| `ui/screens/AppsScreen.kt` (nouveau) | Réglages › Outils › **Applications du système** (demande de l'utilisateur) : une grille des applis, avec le bouton « Retour à Toque » par-dessus. L'appli d'origine et l'appli usine sont exclues, car elles ouvrent le port du moteur. |
| Tests | `SmartProtocolTest` : les 9 000 trames des 5 captures réelles. Chaque trame TX est reconstruite octet par octet, chaque trame RX est décodée sans avertissement, plus le découpage de flux et les trames de référence. `SmartControllerTest` : carte Smart simulée (repos, rotation, chauffe, couvercle, tare, silence de la carte, erreur de la carte, arrêt d'urgence, fermeture de la boucle). |
| `tools/smart/extract_frames.py` | Extrait les trames d'une capture strace en TSV (jeux de test). |

Une revue de sécurité indépendante du code n'a trouvé aucun défaut critique. Ses deux points
moyens (arrêt brutal de Toque, température non fiable) et ses deux points mineurs (tare juste
après un STOP, erreur de boucle) sont corrigés et couverts par des tests.

## 3. Installation sur le robot

- `adb -s <IP-DU-ROBOT>:5555 install app-release.apk` : installé dans `/data/app`, jamais sur `system`.
- Toque est autorisé en root dans Magisk avec `tools/robot/setup-root.sh` (uid 10050). Le busybox de Magisk est présent.
- Mode « Machine réelle » :
  - `shared_prefs/toque.xml` est écrit avec `mode=hardware`. `bootLaunch` n'est pas activé : Toque ne démarre pas tout seul.
  - `pm disable com.tecpal.device.mc30/com.tecpal.device.serialport.service.SerialPortMC30Service` (réversible).
  - `am force-stop` de l'appli d'origine et de l'appli usine.
- L'appli d'origine tourne avec l'uid **system** et redémarre seule en arrière-plan. Une fois son service
  série désactivé, elle n'ouvre plus `/dev/ttyMT1` (vérifié dans `/proc/*/fd`).

## 4. Essais sur le robot (dossier `essais-2026-11-20/`)

| # | Essai | Accord | Résultat |
|---|---|---|---|
| 1 | Lecture seule, STOP uniquement | oui | 19 TX toutes STOP, 20 RX valides, 0 invalide. Liaison « Prête ». |
| 2 | Poids en direct | oui | Paquet de riz : **1035 g** (comme l'appli d'origine), « Stable ». |
| 3 | Couvercle | oui | « Couvercle ouvert » affiché, -550 g sans couvercle, évènement `LID_OPENED`. |
| 4 | STOP de secours du pont | (STOP seulement) | `am force-stop app.toque.smart` : 3 trames STOP de 38 octets écrites par le pont, puis port libéré. |
| 5 | Rotation vitesse 1, 10 s, bol avec ½ L d'eau | « Oui » | `JOB_STARTED` 18:31:26, `JOB_FINISHED` 18:31:38. L'utilisateur a vu la lame tourner puis s'arrêter seule. (Trames non enregistrées : la capture en arrière-plan ne s'était pas lancée.) |
| 6 | Chauffe 37 °C, vitesse 1, 1 min, avec de l'eau | « Oui, lance » | Trames enregistrées : 295 trames actives **identiques** (séquence 199, niveau 1, vitesse ×2 = 2, fonction 1, durée 00:01:00), de 18:35:41.95 à 18:36:41.80 (59,9 s), puis STOP (séquence 200). Température réelle 37 °C, bit 5 (au-dessus de la consigne) à 1. 442 RX, 0 trame invalide. |

## 5. Problème trouvé : le JIT d'ART sur ce robot compile mal du code Kotlin

- **Symptôme :** l'interface plantait à chaque ouverture de « Cuisson manuelle ».
  - Première erreur : `NullPointerException` dans `String.format`, appelé par `tr("Balance : %d g")`.
  - Deuxième erreur : `Parameter specified as non-null is null` dans `Programs.manual(label)`.
- **Cause :** une fois compilée par le JIT, `I18nKt.tr(String)` renvoie `null`. Cette fonction contient `texts[fr] ?: fr` (bytecode `check-cast` / `if-nez` / `goto` / `move-object`).
  - Preuve avec `dalvikvm` sur le robot (`essais-2026-11-20/jit-test-T.java`) :
    - avec JIT : premier `null` au 321e appel, puis 199 679 `null` sur 200 000 appels ;
    - avec `-Xint` : 0 `null`.
- **Correction :** `android:vmSafeMode="true"`. ART n'utilise plus le JIT pour Toque : tout le code est interprété, y compris la logique de pilotage du moteur, que ce défaut aurait aussi pu toucher.
  - Vérifié : drapeau `VM_SAFE_MODE` présent, aucune zone JIT dans `/proc/<pid>/maps`, plus de plantage.
  - Les tests sur PC ne voient pas ce défaut, car la JVM du PC n'a pas ce bug.
- **À faire plus tard (optionnel) :** voir si Kotlin 2.1.21, la version du Toque d'origine (téléchargement nécessaire), produit un bytecode qui évite le défaut. Garder `vmSafeMode` de toute façon.

## 6. Retour à l'appli d'origine

- Depuis Toque : Réglages › Outils › **Revenir à l'appli d'origine**. Toque réactive `SerialPortMC30Service`, passe en mode Démo et lance l'appli d'origine.
- Par ADB :
  ```
  adb -s <IP-DU-ROBOT>:5555 shell su -c 'am force-stop app.toque.smart; pm enable com.tecpal.device.mc30/com.tecpal.device.serialport.service.SerialPortMC30Service'
  adb -s <IP-DU-ROBOT>:5555 shell input keyevent KEYCODE_HOME
  ```
  Ou redémarrer le robot : Toque ne se lance pas au démarrage (`bootLaunch` désactivé).
- Désinstaller : `adb -s <IP-DU-ROBOT>:5555 uninstall app.toque.smart`, après avoir réactivé le service ci-dessus.

## 7. Limites connues et suite possible

- Programmes non validés sur le Smart et refusés : turbo, pétrin, vapeur, rissoler. Il faut d'abord les capturer avec l'appli d'origine.
- Sens inverse : codé d'après l'analyse de l'appli d'origine (octet 17 = 1), jamais essayé sur le robot.
- La température reste à 0 °C tant qu'aucune chauffe n'a eu lieu depuis l'allumage. Toque traite donc toujours le contenu comme chaud pour la sécurité.
- 40 °C (niveau propre au Smart) n'est pas proposé par l'interface : Toque garde la table de température du Connect.
- Codes d'erreur de la carte (octet 15) inconnus : tout code non nul déclenche une alerte et un STOP.
- Recettes : l'import depuis l'appli d'origine du Smart n'a pas été adapté (format de base inconnu).
- Toque n'est pas l'écran d'accueil par défaut (c'est toujours `com.tecpal.device.mc30`). `bootLaunch` n'est pas activé.
- Rien n'est commité dans le dépôt git de `toque-smart` (diff par rapport à l'amont fd5661b : `git diff`).
