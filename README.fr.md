# Toque Smart + Édition Ratatouille

🇬🇧 [English summary](README.md)

Portage de **[Toque](https://github.com/Wuki60/MrCuisineVibeConnect)** (appli libre de Wuki60, MIT) sur le
**Monsieur Cuisine Smart (SKMS 1200A1)** de Lidl, et une version perso, l'**Édition Ratatouille**.

> **Projet indépendant, non affilié à Lidl, SilverCrest ni Hoyer.** Il modifie le logiciel de votre
> robot (root) : cela **annule la garantie** et une erreur peut rendre la tablette inutilisable.
> À vos risques. Faites la sauvegarde complète avant toute écriture. Chaque étape ci-dessous a été
> faite sur un seul robot : le mien.

## Ce qui marche sur le Smart

Matériel testé : **SKMS 1200A1**, tablette MediaTek **MT8167**, **Android 8.1**, 32 bits.

- Pesée, couvercle, température, rotation (sens normal et inverse), chauffe, turbo.
- Toutes les commandes de l'appli d'origine, capturées puis rejouées à l'identique : vapeur, saisir,
  pétrir, œufs, sous-vide, mijoter, fermenter, mixer, smoothie, prélavage, nettoyage.
- Le codec « Smart » (`core/src/main/kotlin/app/toque/core/protocol/Smart.kt`, `SmartProgram.kt`)
  n'envoie **que des trames capturées sur l'appli d'origine**, jamais de trame inventée. Des tests
  automatiques les vérifient octet par octet.
- Les règles de sécurité de Toque (vitesse en chauffe, sens inverse, durées) s'appliquent toujours.

## Ce que contient ce dépôt

| Dossier | Contenu |
|---|---|
| `core/` | Moteur en Kotlin pur : protocole (Connect + **Smart**), sécurité, recettes, et l'ajout Ratatouille (minuteurs, courses/QR, IA, sons, musique). |
| `app/` | Appli Android (`app.ratatouille.smart`, v1.0.0). Les images du thème ne sont **pas** incluses (voir plus bas). |
| `docs/PROTOCOLE-SERIE-SMART.md` | **Le protocole série du Smart** : `/dev/ttyMT1`, 9600 bauds, trames `AA` + longueur, 38 octets envoyés toutes les ~205 ms, 53 reçus. |
| `docs/TOQUE-README*.md` | README d'origine de Toque. |
| `COMPTE-RENDU-PHASE4.md`, `COMPTE-RENDU-PHASE5.md` | Comptes rendus des essais sur le robot. |
| `tools/` | Scripts d'installation (hérités de Toque). |

## Installer sur un Smart (résumé)

1. **Sauvegarde complète** de la mémoire avec SP Flash Tool (23 partitions, 15,6 Go). Indispensable :
   c'est le seul retour en arrière.
2. **Root** : TWRP d'ihrapsa + Magisk, méthode de depau (fil n° 38 du dépôt
   [EliasKotlyar/Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack)).
3. ADB par Wi-Fi, puis installer l'APK (`./gradlew :app:assembleRelease`).
4. Dans l'appli, Réglages › Machine › Machine réelle. Premier essai : bol vide, rotation 10 s.

La procédure détaillée du Connect est dans `docs/INSTALLATION.fr.md` ; **elle ne s'applique pas telle
quelle au Smart** (autre tablette, autre carte moteur, autre protocole).

## Pièges trouvés sur le Smart

- **Le JIT d'ART compile mal certains Kotlin** (`a ?: b` qui renvoie `null` après quelques centaines
  d'appels). Il faut `android:vmSafeMode="true"` dans le manifeste (déjà fait ici).
- Le robot est en mode **`low_ram`** : appli sobre, images sous-échantillonnées.
- Pour que l'appli parle au port série, il faut arrêter le service de l'appli d'origine
  (`SerialPortMC30Service`) ; c'est réversible (Réglages › Outils › Revenir à l'appli d'origine).

## Édition Ratatouille

Une appli à part, sur le moteur de Toque : tuiles façon appli d'origine, **cuisiner avec l'IA**
(Claude écrit une recette au format strict, chaque réglage est contrôlé par les règles de sécurité ;
l'IA ne pilote jamais le moteur ; votre propre clé API, gardée sur le robot), minuteurs, liste de
courses en QR code, sons personnalisables, bulle du lecteur de musique, molette = volume.

**Images : non incluses.** Le thème utilise des images privées (un rat cuisinier) qui ressemblent à un
personnage protégé ; elles ne sont pas publiées. Sans elles, l'appli se compile et affiche des images neutres
(le code ne plante pas sans le dossier d'images). Le nom « Ratatouille » est un nom de travail personnel : ne vous en servez pas
comme marque.

## Compiler

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :core:test :app:assembleRelease
```

Il faut le SDK Android (`ANDROID_HOME` ou `local.properties`). La signature utilise la clé publique
de Toque (`app/signature/toque.p12`), publiée exprès par le projet d'origine : ne signez pas d'appli
« de confiance » avec.

## Crédits

- **[Wuki60](https://github.com/Wuki60/MrCuisineVibeConnect) : Toque** (moteur, interface, sécurité). Licence MIT conservée.
- **depau** et le fil n° 38 d'**EliasKotlyar** : méthode de root.
- **ihrapsa** : TWRP pour ce robot.
- **AroPix** : MCS2MQTT, qui a aidé à comprendre le protocole.
- Portage Smart et Édition Ratatouille : faits avec Claude (Claude Code, Anthropic).

## Licence

MIT, comme Toque (voir `LICENSE`). Le fichier `docs/LICENSE-monsieur-cuisine-developer-sdk.txt` vient
du dépôt d'origine. Aucun contenu Lidl (code, sons, images) n'est inclus.
