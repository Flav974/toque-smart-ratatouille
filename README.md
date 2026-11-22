# Toque Smart + "Ratatouille Edition"

🇫🇷 [Version française](README.fr.md)

A port of **[Toque](https://github.com/Wuki60/MrCuisineVibeConnect)** (free app by Wuki60, MIT) to the
Lidl **Monsieur Cuisine Smart (SKMS 1200A1)** (MediaTek MT8167, Android 8.1), plus a personal build.

- New **`Smart` codec** in Toque's engine: only frames captured from the original app are ever sent.
  Serial protocol: `/dev/ttyMT1`, 9600 baud, `AA` + length frames, 38 bytes out every ~205 ms, 53 in.
  Full write-up (French): [docs/PROTOCOLE-SERIE-SMART.md](docs/PROTOCOLE-SERIE-SMART.md).
- Gotchas: ART's JIT miscompiles some Kotlin on this robot (use `android:vmSafeMode="true"`); the
  robot runs `low_ram`.
- "Ratatouille Edition": AI recipes (your own Claude API key, every setting safety-checked, the AI
  never drives the motor), timers, shopping-list QR code, sounds, music bubble. Theme images are
  private and **not included**.
- Build: `./gradlew :core:test :app:assembleRelease`.

**Not affiliated with Lidl. Rooting voids the warranty and can brick the tablet. Use at your own
risk; make a full backup first.** Tested on one robot only.

Credits: Wuki60 (Toque), depau and EliasKotlyar's thread #38 (root method), ihrapsa (TWRP),
AroPix (MCS2MQTT). MIT licence, as Toque.
