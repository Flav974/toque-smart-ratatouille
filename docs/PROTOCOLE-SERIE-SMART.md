# Protocole série Android ↔ carte de puissance — Monsieur Cuisine Smart (SKMS 1200A1)

> Les dossiers `captures/` et `captures5/` cités plus bas ne sont pas publiés (traces brutes). Les trames utiles sont dans `core/src/test/resources/smart/*.tsv` et vérifiées par les tests.

État : **VALIDÉ PAR CAPTURE le 2026-11-20** — analyse statique de l'appli d'origine
`com.tecpal.device.mc30` 2.21.0.0 (versionCode 22100000), puis 5 captures réelles (dossier `captures/`,
voir § 9) et, en phase 5, tous les programmes de l'appli d'origine (dossier `captures5/`, voir § 10). Micrologiciel de la carte : version 3090 (0x0C12).
Les points encore marqués *(hyp.)* n'ont pas été observés en vrai.

## 1. Liaison physique

| Élément | Valeur | Source |
|---|---|---|
| Port | `/dev/ttyMT1` (`/dev/ttyS1` sur une autre variante matérielle) | `/proc/<pid>/fd` + `SerialPortMC30Service` |
| Vitesse | 9600 bauds | `new SerialPort(file, 9600, 0)` + `stty -a` |
| Format | 8N1, mode brut, pas de contrôle de flux | `stty -a` (cs8 -parenb -cstopb -crtscts) |
| Bibliothèque | `/system/lib/libserial_port.so` (android-serialport-api) | `/proc/<pid>/maps` |
| Qui l'ouvre | `com.tecpal.device.mc30` (service `SerialPortMC30Service`) ; après un redémarrage, l'appli usine `com.tecpal.testapp.mc30` peut aussi le tenir ouvert | `/proc/*/fd` |

`/dev/ttyMT2` est tenu par `com.discovery.factorymode` (autre périphérique, 9600 bauds, émission seule). Ce n'est pas la carte moteur.
`/dev/ttyMT0` est la console. Sur le **Connect**, Toque utilise `/dev/ttyMT0` à 4800 bauds : **ça ne marche pas tel quel sur le Smart.**

## 2. Format général d'une trame (dans les deux sens)

```
Octet  0      : 0xAA                      début de trame
Octets 1-2    : L (16 bits, gros-boutiste) = indice de la somme de contrôle
                (taille totale de la trame = L + 1)
Octets 3-6    : Android -> carte : 02 4F 42 00  (longueur 2, identifiant "OB", drapeau 0)
                carte -> Android : 01 0F 00 00  (observé, toujours identique)
Octet  7      : numéro de séquence (0..255, +1 à chaque trame Android ; la valeur 102 est sautée).
                La carte répond avec le MÊME numéro que la trame à laquelle elle répond.
Octets 8..L-1 : charge utile = suite de « modules » de taille fixe
Octet  L      : somme de contrôle = (somme des octets 1 à L-1) & 0xFF
```

Le parseur Android ne lit que les octets 1-2 (longueur), 7 (séquence) et la charge utile à partir de l'octet 8.

Les deux premiers octets de la charge utile sont toujours la commande `(principale, secondaire)`.
La liste et l'ordre des modules dépendent de la commande (`q7.a` → `u7.e` pour 0x0801/0x0802/0x0803/0x0805).

Validation côté Android (`ja.p.j`) : octet 0 = 0xAA, L lu en 1-2, somme des octets 1..L-1 = octet L.
Lecture côté Android : lit 3 octets, puis jusqu'à 200 octets, puis dort 100 ms (90 ms sur balance 1 g).

## 3. Trame de commande Android → carte (commande 0x0802, 38 octets, L = 0x25)

| Octets | Module | Contenu |
|---|---|---|
| 8-9 | CMD | `08 02` |
| 10-15 | Température (6) | [10] = niveau de température (ou 1 = « température en °C dans l'octet 12 » selon le mode) ; [11] = 0 ; [12] = température en °C (modes à température libre) ; [13-15] = 0 |
| 16-18 | Moteur (3) | [16] = vitesse × 2 (0..20, demi-pas) ; [17] = 1 si rotation inverse ; [18] = 0 |
| 19 | Fonction (1) | code du mode (voir tableau) |
| 20-21 | Balance (2) | [20] = 0 ; [21] = 1 pour tarer (commande 0x0803) |
| 22-30 | Minuterie (9) | [22-24] = 0 ; [25-27] = heures, minutes, secondes ; [28-30] = 0 |
| 31 | Lumière (1) | 0 |
| 32 | Bouton (1) | 0 |
| 33-36 | Réservé (4) | 0 |
| 37 | Somme de contrôle | |

Note : le code d'origine écrit l'octet 11 avec `(byte)(t << 8)`, donc toujours 0 : la température tient sur l'octet 12 seul.

Niveaux de température (`ka.e.f9411a`, index = niveau) :
`0, 37, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130` °C
(21 niveaux, 40 °C en plus par rapport au Connect).

Vitesse : l'appli envoie `table[vitesse × 2]` avec table = 0..20, donc la valeur sur le fil vaut vitesse × 2.

### Codes de fonction (octet 19)

Colonne « Capturé » : trame réellement envoyée par l'appli d'origine le 2026-11-20 (§ 10).
Octet 10 = 1 avec l'octet 12 en °C : programmes à température libre.

| Code | Mode (type appli) | Paramètres utilisés |
|---|---|---|
| 0 | STOP / attente (type 0) | aucun |
| 1 | Manuel standard (type 3), riz (19), robot (20), rampe (123) | niveau temp, vitesse, sens, durée |
| 3 | Vapeur (4) ; bouilloire (14) sur une variante | niveau temp, vitesse, durée |
| 4 | Rôtir (5) | niveau temp, vitesse, durée |
| 5 | Pétrir (6, 124-126) | vitesse, sens, durée |
| 6 | Nettoyage court (121) | |
| 7 | Nettoyage long (122) | |
| 8 | Œufs (8) | temp °C, vitesse, durée |
| 9 | Sous vide (9) | temp °C (octet 10 = 1), vitesse, durée |
| 10 | Mijotage lent (10) | temp °C |
| 11 | Fermentation (11) | temp °C |
| 12 | Purée (15) | |
| 13 | Smoothie (13) | |
| 14 | Bouilloire (14) | |
| 15 | Veille (2) — ancien format | |
| 198 (0xC6) | Turbo (1) — trame courte de 35 octets, voir ci-dessous | **confirmé** |

Capturé (phase 5, `captures5/`) :

| Écran de l'appli d'origine | Fonction [19] | Niveau [10] | °C [12] | Vitesse×2 [16] | Sens [17] |
|---|---|---|---|---|---|
| Cuisson personnalisée v5 | 1 | 0 | 0 | 0A | 0 |
| Cuisson personnalisée v2 sens inverse | 1 | 0 | 0 | 04 | 1 |
| Cuisson personnalisée 100 / 70 / 40 °C, v1 | 1 | 0E / 08 / 02 | 0 | 02 | 0 |
| Trancheuse-râpe | 1 | 0 | 0 | 0A | 0 |
| Malaxer pâte ferme / pâte liquide | 5 | 0 | 0 | 08 | 0 |
| Malaxer pâte molle | 5 | 0 | 0 | 08 | 1 |
| Mixer | 12 | 0 | 0 | 10 | 0 |
| Smoothie | 13 | 0 | 0 | 14 | 0 |
| Cuisson vapeur (sans préchauffage) | 3 | 12 (120 °C) | 0 | 02 | 0 |
| Saisir | 4 | 14 (130 °C) | 0 | 02 | 1 |
| Cuire des œufs | 8 | 1 | 82 (130) | 02 | 0 |
| Sous-vide | 9 | 1 | 3C (60) | 02 | 1 |
| Mijoter | 10 | 1 | 5A (90) | 02 | 1 |
| Fermenter | 11 | 1 | 28 (40) | 02 | 1 |
| Cuire du riz | 1 | 0E | 64 (100) | 02 | 1 |
| Chauffer l'eau (sans durée, durée envoyée 00:05:05) | 3 | 0E | 64 (100) | 02 | 1 |
| Prélaver, nettoyage rapide (1 min 30) | 6 | 1 | 0 | 14 | 0 |
| Prélaver, nettoyage long (écran 1 min 45, envoyé 00:15:30) | 7 | 1 | 0 | 14 | 0 |

Remarques :
- L'interface limite la vitesse à 3 en sens inverse et dès qu'une température est réglée.
- Sous-vide : durée réglée 00 h 01 min, envoyée avec l'octet 25 = 1 (les autres programmes en h/min envoient l'octet 26 = 1). La carte ne décompte pas : sans effet.
- **Pause** = trame STOP (fonction 0) + quelques `08 01`. Pour le nettoyage long, une trame fonction 7 vitesse 0 précède le STOP.
- Le pétrin, la vapeur et « Saisir » sont **une seule trame, inchangée** pendant tout le programme : la carte gère elle-même le rythme.

### Trame Turbo (35 octets, L = 0x22) — confirmée

Envoyée quand on appuie longuement sur le **bouton rotatif** (l'écran Turbo de l'appli ne fait qu'afficher la consigne). Construite par `o7.a` : `d(8,2)`, `j(10)`, `g(198)`, `c(6)`, `u(5000 ms)`.

```
AA 00 22 02 4F 42 00 02 | 08 02 | 00 00 00 00 00 00 | 00 00 0A | C6 | 00 00 | 00 00 00 00 00 05 00 00 00 | 00 00 06 | 9C
          séq.            CMD     température (6)     moteur:     fn    balance  minuterie : 5 s              lum., bouton,
          (compteur à part)                           [18]=turbo                                             [33]=06
```
- Séquence : compteur **séparé** de celui des trames normales (`l7.r`, statique), +1 à chaque appui, 102 sauté. Le compteur normal n'avance pas pendant le turbo.
- La même trame est renvoyée toutes les ~300 ms pendant l'appui (~4,3 s observées), puis une trame STOP normale.
- Réponse de la carte : fonction [28] = C6, vitesse [25] = 0, **touche turbo [27] = 0A**.

### Autres commandes Android → carte

| Commande | Rôle | Contenu |
|---|---|---|
| `08 01` | « ACCESS » : interrogation pendant qu'un programme tourne | CMD seul, reste à 0 |
| `08 02` + fonction 0 | interrogation au repos = **ordre STOP** | CMD + fonction 0 |
| `08 03` | Tare | fonction 0, balance [21] = 1 |
| `FE 01`…`FE 06` | Mise à jour du micrologiciel de la carte (OTA) | **ne jamais envoyer** |

### Trames réelles capturées (Android → carte)

```
Repos (STOP), séq. 0xBD :
AA 00 25 02 4F 42 00 BD 08 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 7F
Tare (ouverture de l'écran Balance), séq. 0xA0 :
AA 00 25 02 4F 42 00 A0 08 03 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 64
Manuel vitesse 1, sans chauffe, 30 s, séq. 0xB1 :
AA 00 25 02 4F 42 00 B1 08 02 00 00 00 00 00 00 02 00 00 01 00 00 00 00 00 00 00 1E 00 00 00 00 00 00 00 00 00 94
Manuel 37 °C (niveau 1), vitesse 1, 1 min : octet 10 = 01, octet 16 = 02, octet 19 = 01, octet 26 = 01
```

## 4. Trame d'état carte → Android (commande 0x0802, **53 octets, L = 0x34**, confirmé)

| Octets | Module | Contenu |
|---|---|---|
| 8-9 | CMD | commande à laquelle la carte répond (0xFE.. = OTA) |
| 10-11 | Version | version du micrologiciel de la carte (16 bits) |
| 12-14 | État | [12] = bits d'état (ci-dessous) ; [13-14] = remarques |
| 15 | Erreur | code d'erreur de la carte |
| 16 | Code retour | |
| 17-24 | Température | [17] niveau ; [18-19] température cible (16 bits signés) ; [20-21] **température réelle** ; [22] unité ; [23-24] remarques |
| 25-27 | Moteur | [25] vitesse × 2 ; [26] sens ; [27] touche turbo |
| 28 | Fonction | mode en cours |
| 29-35 | Balance | [29] = unité (4 bits hauts) / nb de décimales (4 bits bas) ; [30-33] **poids** (32 bits gros-boutiste) ; [34] état balance ; [35] étape de calibration |
| 36-44 | Minuterie | [36-38] durée écoulée h/m/s ; [39-41] **compte à rebours** h/m/s ; [42-44] départ différé |
| 45 | Lumière | |
| 46 | Bouton | |
| 47-51 | (5 octets non décrits par l'appli) | toujours 00 dans les captures ; ignorés par l'appli |
| 52 | Somme de contrôle | |

Observé en vrai :
- **Poids** [30-33] : grammes, entier signé (ex. 1035 = écran 1035 g ; -550 couvercle retiré). Varie de ±5 g au repos. Pas de décimale ([29] = 00).
- **Température réelle** [20-21] : °C (ex. `00 25` = 37 °C, comme l'écran). Elle reste à 0 tant qu'aucun programme avec chauffe n'a été lancé depuis le démarrage, même avec de l'eau chaude. Ensuite elle reste affichée après l'arrêt.
- **Température cible** [18-19] : toujours 0. [17] = niveau envoyé (écho).
- **Moteur** [25] et **fonction** [28] : écho de la commande. Aucune mesure de régime réel.
- **Compte à rebours** [39-41] : écho de la durée demandée, **non décompté par la carte**.
- **État balance** [34] : 2 sur l'écran Balance et pendant un programme, 0 sinon.

Bits de l'octet d'état [12] :

| Bit | Connect (Toque) | Smart |
|---|---|---|
| 0-3 | moteur/chauffe, balance stable, sens | **toujours 0**, même moteur en marche (observé) |
| 4 | erreur | jamais vu (code d'erreur dans l'octet 15) |
| 5 | au-dessus de la température cible | **confirmé** : 1 avec de l'eau à 37 °C et une consigne de 37 °C |
| 6 | sonde du bol détectée | **bol absent = 1** (confirmé : bol retiré pour le vider) |
| 7 | couvercle ouvert | **couvercle ouvert = 1** (confirmé) |

Balance : l'appli détecte une balance au gramme si plusieurs poids non multiples de 5 arrivent (`v()`), puis passe l'interrogation à 90 ms.

## 5. Dialogue

Observé :
- L'appli envoie une trame toutes les **~205 ms**. La carte répond **~100 ms** après, avec le même numéro de séquence. Elle n'envoie jamais rien d'elle-même.
- Au repos : trame `08 02` fonction 0 (= STOP).
- **Pendant un programme, l'appli renvoie la trame de commande complète à chaque tour** (fonction 1, vitesse, niveau de température, durée inchangée).
- **C'est l'appli qui arrête le programme** : à la fin de la durée, elle repasse à la trame STOP. La carte ne décompte pas le temps.
- Trames `08 01` (ACCESS) : vues seulement quelques fois au lancement d'un programme.
- *(hyp.)* Sans réponse de la carte, l'appli arrête le programme (minuteur `removeCallbacks`). Côté carte, l'arrêt en cas de silence d'Android n'a pas été testé : **un remplaçant comme Toque doit envoyer STOP lui-même et ne jamais cesser d'émettre en cours de programme.**

## 6. Différences avec le Connect (protocole utilisé par Toque)

| | Connect (Toque, `Mcc.kt`) | Smart (ce document) |
|---|---|---|
| Port | `/dev/ttyMT0` | `/dev/ttyMT1` |
| Vitesse | 4800 bauds | 9600 bauds |
| Début de trame | `0x55` | `0xAA` |
| Fin de trame | `0xAA` | aucune (somme de contrôle en dernier) |
| Longueur | 1 octet | 2 octets gros-boutiste |
| Trame commande | 15 octets, type `A1` | 38 octets, `"OB"` + commande `08 02` |
| Trame état | 27 octets, type `B1` | 53 octets (confirmé) |
| Séquence | non | oui (octet 7) |
| Somme de contrôle | octets 0..n-3 | octets 1..L-1 |
| Niveaux de temp | 20 (sans 40 °C) | 21 (avec 40 °C) |
| Vitesse sur le fil | 0..10 | 0..20 (× 2) |
| Programmes | Android pilote tout | la carte a des codes de mode (vapeur, pétrin, sous vide…) mais Android garde la minuterie et envoie l'arrêt |
| Mesures renvoyées | régime réel, temp. réelle, poids | temp. réelle et poids seulement (vitesse = écho) |
| Paquet d'origine à arrêter | `de.silpion.mc2` | `com.tecpal.device.mc30` (et `com.tecpal.testapp.mc30` s'il tient le port) |

Conséquence pour le portage : la couche `RootSerialTransport` de Toque se réutilise en changeant port, vitesse et paquet ; `Mcc.kt` (encodage/décodage) est à réécrire pour ce format.

## 7. Méthode de capture (passive)

`strace` 7.3 (paquet Termux) attaché à l'appli d'origine, lecture des appels `read`/`write` sur le descripteur de `/dev/ttyMT1`. Rien n'est envoyé à la carte.

```sh
P=<pid qui tient /dev/ttyMT1>; T=/data/data/com.termux/files/usr
LD_LIBRARY_PATH=$T/lib $T/bin/strace -f -p $P -e trace=read,write -e signal=none \
  -e status=successful -tt -xx -s 256 -o /data/local/tmp/st.txt
```

Outils : `decode_strace.py capture.txt FD [--raw]` décode toutes les trames ; `changes.sh capture.txt FD` n'affiche que celles qui changent.

## 8. Reste à faire (pour le portage, pas bloquant)

- [x] Capture au repos, pesée + tare, couvercle, bol, rotation vitesse 1, chauffe 37 °C.
- [ ] Table des codes d'erreur (octet 15) : aucune erreur vue.
- [x] Sens inverse, turbo, modes spéciaux (vapeur, pétrin, saisir, œufs, sous vide, mijoter, fermenter, mixer, smoothie, riz, bouilloire, prélavage) : § 3 et § 10.
- [ ] Comportement de la carte si Android cesse d'émettre en plein programme (test à faire avec accord, bol vide).

## 9. Captures (dossier `captures/`, fd 59, pid de `com.tecpal.device.mc30`)

| Fichier | Contenu |
|---|---|
| `01-repos.txt` | 5 s au repos (écran d'accueil) |
| `02-balance-couvercle.txt` | écran Balance (tare), paquet de riz 1 kg (lu 1035 g), retrait, couvercle ouvert/fermé |
| `03-rotation-v1-30s.txt` | manuel vitesse 1, sans chauffe, 30 s, bol vide |
| `04-chauffe-37C-v1-1min.txt` | manuel 37 °C, vitesse 1, 1 min, ½ L d'eau froide (temp. réelle restée à 0) |
| `05-eau-chaude-puis-37C-1min.txt` | bol retiré/vidé (bit 6), eau chaude, puis manuel 37 °C 1 min (temp. réelle 37, bit 5) |

## Sources

- Appli d'origine du Smart (`com.tecpal.device.mc30`) observée à l'exécution (strace sur le port série). Aucun code ni son de Lidl n'est publié ici : seulement des trames d'octets.
- AroPix/MCS2MQTT : module LSPosed qui lit les objets Java de l'appli (poids, vitesse, température, temps), pas les octets.
- Toque (`core/.../protocol/Mcc.kt`, `RootSerial.kt`) ; le codec du Smart est `core/.../protocol/Smart.kt`.

## 10. Captures de la phase 5 (dossier `captures5/`, 2026-11-20, fd 59)

Appli d'origine pilotée par `adb shell input tap`, durée réglée à 4 s quand c'est possible (l'appli s'arrête seule).
Programmes à durée en h/min : 1 min puis **Pause** puis **Stop** après ~4 s. Fichiers `.txt` (strace brut) et `.tsv` (trames, aussi dans `toque-smart/core/src/test/resources/smart/`).

| Fichier | Contenu |
|---|---|
| `06-v5-4s` | Cuisson personnalisée, vitesse 5, 4 s, bol vide |
| `07-v2-inverse` | vitesse 2, sens inverse, 4 s |
| `08-turbo-puis-malaxer-ferme` | Turbo (appui long sur le bouton rotatif), puis Malaxer pâte ferme 4 s |
| `10-malaxer-molle`, `11-malaxer-liquide` | Malaxer pâte molle / liquide, 4 s |
| `12-mixer`, `13-trancheuse` | Mixer, Trancheuse-râpe, 4 s |
| `14-eau` | ½ L d'eau : 100 °C (19:17), 70 °C (19:18), 40 °C (19:19), vapeur (19:20), saisir (19:21), œufs (19:23), sous-vide (19:24), mijoter (19:26), fermenter (19:27), smoothie (19:28), riz (19:30), chauffer l'eau (19:31), prélavage rapide (19:32:12) et long (19:32:48) |


## 11. Programmes à étapes (capture complète du 2026-11-21, phase R2 de l'Édition Ratatouille)

Capture : strace passif de `com.tecpal.device.mc30` (fd 61), 16:37 → 17:24. Fichiers :
`ratatouille/essais-r2/st-r2-etapes-mc30.txt` (brut), `…-decode.txt` (décodé), copie en
`ratatouille/app/core/src/test/resources/smart/16-etapes-mc30.tsv`.

Règles vues :
- **Repos dans un programme = trame active avec vitesse 0** : la fonction, le niveau et la
  température (octet 12) restent ; la chauffe continue. Ce n'est pas un STOP.
- **Minuterie (octets 25-27) = temps restant**, réécrit au début de chaque étape (environ 0,8 s
  après le changement de moteur). Exceptions : Saisir garde la durée totale (7:00) du début à la
  fin ; le nettoyage long envoie 15:30 pendant ses deux premières étapes, puis 1:45.
- Changement de sens direct, sans STOP, après une étape à vitesse 10 (prélavage, nettoyage long).
- Pause demandée par l'utilisateur : STOP × 3, puis une trame commande `08 01`.
- La réponse de la carte renvoie fn = 1 pendant ces programmes (pas le code envoyé).

| Programme (fn) | Étapes (octets 10 niveau / 12 °C / 16 vitesse×2 / 17 sens) |
|---|---|
| Malaxer pâte ferme (5), 2:00 | 45 s v4 · 15 s v0 · 20 s v4 inv · 10 s v0 · 20 s v4 · 10 s v0 inv · STOP |
| Malaxer pâte molle (5), 2:00 | 15 s v4 inv · 35 s v0 inv · 70 s v4 · STOP |
| Saisir (4), niveau 20 | [5 s v1 inv, 30 s v0 inv] en boucle ; minuterie fixe |
| Mijoter (10), niveau 1, 90 °C | [30 s v1 inv, 120 s v0 inv] × 3, 30 s v1 inv, puis v0 inv jusqu'à la fin |
| Fermenter (11), niveau 1, 40 °C | 5 min v1 inv, puis v0 inv jusqu'à la fin |
| Prélavage rapide (6), niveau 1, 1:30 | 30 s v10 · 30 s v4 inv · 30 s v10 · STOP |
| Nettoyage long (7) | 30 s v10 niv 1 · préchauffe v1 niv 2 (40 °C) jusqu'au **bit 5 de l'octet d'état** (« température atteinte », 15 min au plus ; 25 s ici, eau déjà chaude) · 2 × [25 s v10 niv 1, 15 s v4 inv niv 1] · 25 s v10 niv 0 · STOP |

Code d'origine correspondant : `y2/f0.java` (durées, vitesses, températures, `n(2)` = 2 répétitions).
Limite d'origine : après un pétrissage, l'appli refuse le suivant pendant 30 min (« Veuillez
attendre 30 min avant le prochain pétrissage… »). Alerte « vitesse élevée + liquide très chaud »
(`SafetyDialogManager`, vitesse ≥ 4 et 60 °C) avant Malaxer quand l'eau est chaude.
