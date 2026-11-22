# Phase 5 — Commandes moteur et chauffe du Monsieur Cuisine Smart : compte rendu

Date : 2026-11-20. État : **FAITE**. Tous les réglages ont été capturés avec l'appli d'origine. Toque les reproduit dans les tests, et 5 réglages ont été vérifiés sur le robot (§ 6).

But : enregistrer ce que fait l'appli d'origine (`com.tecpal.device.mc30` 2.21.0.0) pour chaque réglage,
puis le faire reproduire à l'identique par Toque-Smart.

## 1. Méthode

- Retour à l'appli d'origine, réversible, sans désinstaller Toque :
  - `am force-stop app.toque.smart` ;
  - `pm enable …/SerialPortMC30Service` ;
  - lancement direct de `com.tecpal.device.mc30/com.tecpal.device.activity.MainActivity`.
  
  Le bouton Accueil ouvre maintenant le choix du lanceur, parce que Toque se déclare aussi écran d'accueil. Après ces commandes, `com.tecpal.device.mc30` tient `/dev/ttyMT1` (fd 59).
- Mise à jour proposée par l'appli d'origine : refusée à chaque fois (« ultérieurement »), et la croix du bandeau a été fermée. La version reste la 2.21.0.0.
- Capture passive : strace attaché au pid de mc30, lancé par `adb shell su -c 'sh cap.sh …'` en tâche de fond côté PC. Rien n'est envoyé à la carte.
  - Attention : un seul strace à la fois par processus. Une capture encore active « vole » la suivante (09-malaxer-ferme était vide : ses trames sont dans 08).
- L'écran de l'appli d'origine a été piloté par `adb shell input tap/swipe`, avec des captures d'écran réduites pour vérifier chaque étape.
  - La durée était réglée à **4 s** pour que l'appli s'arrête seule.
  - Pour les programmes réglés en heures et minutes, la durée était de 1 min, avec **Pause** puis **Stop** après environ 4 s.
- Accords de l'utilisateur :
  - « Oui, les 6 » pour les essais avec le bol vide (vitesse 5, sens inverse, turbo, pétrin, mixer, trancheuse) ;
  - « Oui, les 13 » pour les essais avec ½ L d'eau (100, 70 et 40 °C, vapeur, saisir, œufs, sous-vide, mijoter, fermenter, smoothie, riz, chauffer l'eau, prélaver).
- Turbo : l'écran Turbo ne fait qu'afficher une consigne. Le turbo part d'un **appui long sur le bouton rotatif**, fait par l'utilisateur.

## 2. Résultats (détail : `protocole-2026-11-20/PROTOCOLE-SERIE-SMART.md` § 3 et § 10)

| Réglage | Trame (octets 10 niveau / 12 °C / 16 vitesse×2 / 17 sens / 19 fonction) | Toque |
|---|---|---|
| Vitesse 5 | 0 / 0 / 0A / 0 / 1 | identique (test) |
| Vitesse 2 sens inverse | 0 / 0 / 04 / **1** / 1 | identique (test) |
| 100 °C, 70 °C v1 | 0E, 08 / 0 / 02 / 0 / 1 | identique (test) |
| 40 °C v1 | 02 / 0 / 02 / 0 / 1 | trame reconstruite (test) ; 40 °C absent de l'interface de Toque |
| Turbo | trame de **35 octets**, fonction C6, octet 18 = 0A, 5 s, compteur de séquence séparé | identique (test) |
| Malaxer (pâte ferme / liquide) | 0 / 0 / 08 / 0 / **5** | identique (test) |
| Malaxer pâte molle | 0 / 0 / 08 / 1 / 5 | reconstruite par le codec (test) ; Toque utilise la pâte ferme |
| Vapeur | **12** (120 °C) / 0 / 02 / 0 / **3** | identique (test) |
| Saisir (rissoler) | **14** (130 °C) / 0 / 02 / 1 / **4** | identique (test) |
| Mixer, Smoothie | 0 / 0 / 10, 14 / 0 / 12, 13 | figés dans un test (pas de bouton Toque) |
| Œufs, Sous-vide, Mijoter, Fermenter | 1 / 130, 60, 90, 40 °C / 02 / 0,1,1,1 / 8, 9, 10, 11 | figés dans un test |
| Riz, Chauffer l'eau | 0E / 100 °C / 02 / 1 / 1, 3 | figés dans un test |
| Prélavage rapide, long | 1 / 0 / 14 / 0 / 6, 7 | figés dans un test |
| Trancheuse-râpe | = vitesse 5 manuelle | identique (test) |

Constats :
- La table des 21 niveaux de température du Smart est **confirmée** (100 °C = 14, 70 °C = 8, 40 °C = 2, 120 °C = 18, 130 °C = 20).
- Sens inverse = octet 17 à 1 (**confirmé**). La carte garde le sens dans sa réponse [26] après l'arrêt.
- Pétrin, vapeur et saisir : **une seule trame, inchangée**, pendant tout le programme. C'est la carte qui gère le rythme. Toque (Connect) alternait les phases (pétrin inverse/normal, rissolage 5 s / 30 s, vapeur lame immobile) : sur le Smart, il envoie désormais la même trame que l'appli d'origine.
- Turbo : réponse de la carte avec la vitesse [25] = 0 et la touche turbo [27] = 0A. Le décodeur de Toque compte maintenant cet écho comme « moteur en marche ».
- Pause = trame STOP.
- Aucune erreur de carte (octet 15) dans toutes les captures.

## 3. Changements dans Toque-Smart

| Fichier | Changement |
|---|---|
| `core/.../protocol/Smart.kt` | Codes de fonction 3/4/5/C6. `buildTurbo` (trame de 35 octets) et `isTurbo`. Octet 12 (°C) dans `buildTx`/`decodeTx`. Écho turbo dans `decodeRx`. `SmartCodec` accepte KNEAD, STEAM, TOAST et TURBO, **uniquement avec les paramètres capturés** (`accepts`/`refusal`). Tout le reste est refusé, jamais corrigé en silence. Compteur de séquence turbo séparé. Toujours aucun `FE xx` possible. |
| `core/.../protocol/BoardCodec.kt` | `accepts()` : vérification complète d'un réglage avant le démarrage. |
| `core/.../machine/Safety.kt` | `Programs.forSmart()` : pétrin (v4, sens normal), vapeur (120 °C, v1), saisir (130 °C, v1, inverse), en une phase. `SafetyPolicy.COLD_CONFIRM`. |
| `core/.../machine/MachineController.kt` | `startJob` adapte les programmes pour le Smart et refuse tout de suite un réglage que le codec n'accepte pas. Pas de montée progressive quand la carte impose la vitesse (pétrin). **Turbo sur le Smart** : voir § 4. |
| `app/.../ui/MainActivity.kt`, `Ipc.kt`, `MachineClient.kt`, `MachineService.kt`, écrans Manuel, Programmes, Cuisine | Bouton Turbo : fenêtre « Contenu froid ? », puis nouvel appui. Commande IPC `CONFIRM_COLD`. |
| `app/.../ui/screens/ProgramsScreen.kt`, `lang/en.json` | Descriptions du pétrin et de la vapeur alignées sur le Smart, traductions anglaises. |
| Tests | `SmartProtocolTest` : 8 nouvelles captures (≈ 13 500 trames). Chaque trame TX est reconstruite octet par octet, y compris le turbo. Chaque réglage Toque est comparé à la trame capturée. Les programmes sans équivalent dans Toque sont figés. `SmartControllerTest` : pétrin, vapeur, saisir, turbo, confirmation « contenu froid », refus avant le démarrage. |

`android:vmSafeMode="true"` est conservé.

## 4. Turbo et température sur le Smart (décision de sécurité)

La carte du Smart renvoie 0 °C tant qu'aucune chauffe n'a eu lieu depuis l'allumage. Depuis la phase 4, Toque supposait donc un contenu à 60 °C, et le Turbo était **toujours interdit**. Désormais :
- si une température ≥ 60 °C est réellement mesurée, le Turbo reste interdit ;
- sinon, au premier appui, Toque demande « Contenu froid ? ». L'utilisateur confirme, puis appuie de nouveau ;
- La confirmation est **oubliée** :
  - à l'ouverture du couvercle ;
  - à l'envoi d'une chauffe ;
  - à tout changement d'état de la liaison (perte, port libéré) ;
  - au bout de 5 min.
- La confirmation est **refusée** dans ces cas :
  - aucune question « Contenu froid ? » ne l'a précédée ;
  - le couvercle a été ouvert depuis la question ;
  - le couvercle est ouvert ;
  - la liaison n'est pas prête.
- Le Turbo attend aussi 2 s après le dernier écho « moteur en marche ». Exemple : arrêt d'un programme en sens inverse, puis Turbo.
- Un appui reste limité à 3 s, comme avant.

Relecture de sécurité indépendante (sous-agent Opus, lecture seule) :
- **1 constat critique, corrigé :** la confirmation survivait à une coupure de liaison. Scénario : port libéré, soupe chaude versée, reprise, puis Turbo.
- **2 constats moyens, corrigés :**
  - Turbo juste après l'arrêt d'un programme en sens inverse ;
  - confirmation donnée après une ouverture du couvercle.
- **2 constats mineurs, corrigés :**
  - `adjust()` vérifie maintenant `codec.accepts` ;
  - tests ajoutés.
- **Contraintes vérifiées sans problème :**
  - aucun `FE xx` ;
  - un STOP part toujours ;
  - aucune correction silencieuse ;
  - `vmSafeMode` présent.

Pétrin à vitesse 4 : la confirmation « contenu chaud » existante est demandée (même raison).

## 5. Compilation

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline :core:test :app:assembleRelease` : **103 tests, 0 échec**, 1 ignoré (jumeau numérique absent, comme avant).

APK installé (`install -r`, dans `/data/app`, 19:58) : SHA-256 `365489091ac2dae92ea1d3c5ce7b42d2944bc29cd538c114b7a330a9dae9497f`. Drapeau `VM_SAFE_MODE` vérifié.
Les essais du § 6 ont été faits avec la version précédente (`03393211…`). La seule différence est le texte des fenêtres de mixage à chaud (§ 7).

## 6. Essais de Toque sur le robot (19:49–19:53, ½ L d'eau froide, couvercle fermé)

Préparation :
- `SerialPortMC30Service` désactivé de nouveau, mc30 et l'appli usine arrêtées, Toque relancé (`/data/local/tmp/totoque.sh`) ;
- Toque affiche « Prête » en mode Machine réelle ;
- capture strace du pont série (`busybox cat`) : `essais-2026-11-20/phase5-toque-strace.txt` et `phase5-toque.tsv`. La capture est aussi un jeu de test, `15-toque-essais`.

Accord de l'utilisateur : « Oui, les 5 ».

| # | Essai Toque | Trame finale envoyée | Comparaison avec l'appli d'origine |
|---|---|---|---|
| 1 | Manuel vitesse 5, 5 s | `…00 00 0A 00 00 01 …` | identique (hors séquence et durée). Toque monte 1→5 par pas de 0,4 s, parce que la température est supposée chaude après la confirmation « Mixer quand même ». L'appli d'origine passe directement à 5. |
| 2 | Manuel 70 °C, vitesse 1, 5 s | niveau 08, vitesse 02, fonction 1 | identique. La sonde affiche ensuite 37 °C. |
| 3 | Manuel vitesse 2, sens inverse, 5 s | vitesse 04, sens 01 | identique (montée 1→2) |
| 4 | Pétrir, arrêté par Stop après ~11 s | vitesse 08, fonction 05, sens normal | identique. Une seule trame pendant tout le programme. La carte répond fonction 1, comme pour l'appli d'origine. |
| 5 | Turbo : fenêtre « Contenu froid ? », confirmation, appui 2 s | trame de 35 octets, séquence turbo 01 | identique octet pour octet (hors séquence). 10 trames en 2,0 s, puis STOP. Écho de la carte : touche turbo = 0A, fonction C6. |

- Sur les 1 196 réponses de la carte : aucune erreur, aucun bit d'état anormal, séquences toujours égales.
- Le test `toqueOnTheRobotSentTheSamePayloadsAsTheOriginalApp` vérifie que ces 5 réglages, tels qu'envoyés par Toque, existent dans les captures de l'appli d'origine.

Non essayés sur le robot avec Toque : vapeur et rissoler (durée minimale 5 min et 3 min dans l'interface). Leurs trames sont validées dans les tests contre la capture d'origine.

## 7. Restes et suites possibles

- **Texte corrigé (19:58)** dans les fenêtres de mixage à chaud (démarrage, changement de vitesse, reprise) :
  - à 60 °C ou plus : « Contenu chaud / Le bol est à N °C » ;
  - à 0 °C : « Contenu peut-être chaud / Température du bol inconnue : ce robot ne la mesure qu'après une chauffe » ;
  - entre les deux : « Le bol indiquait N °C, mais ce robot ne met pas toujours la mesure à jour : il peut être plus chaud ».
  
  Vérifié sur le robot : la fenêtre s'est ouverte, puis a été annulée, et le moteur n'a pas tourné. Traductions anglaises ajoutées.
- **Interface :**
  - 40 °C, propre au Smart, n'est toujours pas proposé ;
  - pâte molle (pétrin en sens inverse), œufs, sous-vide, mijoter, fermenter, mixer, smoothie, riz, bouilloire et prélavage n'ont pas de bouton. Leurs trames sont documentées et figées dans les tests.
- **Non capturés :**
  - le préchauffage de la vapeur (refusé pendant l'essai) ;
  - le comportement de la carte si Android cesse d'émettre en plein programme ;
  - les codes d'erreur de la carte (aucun vu).
- Rien n'est commité dans le dépôt `toque-smart`.

## 8. Retour à l'appli d'origine

Inchangé (voir phase 4 § 6) : Toque › Réglages › Outils › Revenir à l'appli d'origine, ou `pm enable …/SerialPortMC30Service` puis lancer `com.tecpal.device.mc30/com.tecpal.device.activity.MainActivity`.
Le bouton Accueil du robot ouvre désormais le choix du lanceur (Toque se déclare écran d'accueil).
