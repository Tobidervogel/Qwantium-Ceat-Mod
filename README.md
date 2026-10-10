# Qwantium PvP Bot

Fabric-Mod für **Minecraft 1.21.11**: Ein PvP-Trainingsbot, der im Kampf **deinen eigenen Charakter** steuert. Er zielt, läuft, sprintet, strafed, schlägt, blockt mit dem Schild und isst Gapples. Damit kann z. B. dein Mitspieler den Bot anmachen, und du trainierst gegen ihn.

Inoffiziell und nicht mit QuantumUltimate / „Quantum's PvP Practice“ verbunden. Die KI ist komplett selbst geschrieben, es steckt kein Code aus Quantums Map drin.

## Wo funktioniert der Bot?

| Wo | Geht? |
|---|---|
| Singleplayer | ✅ |
| LAN-Welt (Host hat die Mod) | ✅ für den Host und für Gäste mit der Mod |
| Eigener Fabric-Server **mit** dieser Mod im `mods`-Ordner | ✅ |
| Server ohne die Mod (Hypixel, Realms, …) | ❌ Bot bleibt aus |

Der Server entscheidet: Nur wenn die Mod auch auf dem Server läuft, schickt er dem Client die Erlaubnis. Abschalten kann man das in `config/qwantiumbot-server.properties` mit `allow-bot=false`.

## Installation

1. [Fabric Loader](https://fabricmc.net/use/) für 1.21.11 installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) für 1.21.11 in den `mods`-Ordner legen.
3. `qwantium-pvp-bot-1.0.0+mc1.21.11.jar` in den `mods`-Ordner legen.
4. Für einen eigenen Server: Fabric API und die Mod-Jar auch in den `mods`-Ordner des Servers legen.

## Training mit einem Freund (LAN)

1. Beide haben die Mod installiert.
2. Einer öffnet seine Welt mit „Im LAN öffnen“, der andere tritt bei.
3. Der Bot-Spieler schaut dich an und drückt **H**. Ab dann kämpft der Bot nur gegen dich.
4. **R** drücken: Der Bot ist an. Sobald du in Reichweite bist, übernimmt er den Charakter.
5. Nochmal **R** drücken: Der Bot ist aus.

Solange der Bot keinen Gegner hat, kannst du ganz normal selbst laufen.

## Tasten

Änderbar unter Optionen → Steuerung → „Qwantium PvP-Bot“.

| Taste | Funktion |
|---|---|
| **R** | Bot an/aus |
| **H** | Angeschauten Spieler als einziges Ziel festlegen (ins Leere schauen = wieder „nächster Spieler“) |
| **J** | Schwierigkeit wechseln |

## Befehle

```
/qbot on | off | toggle | status | help
/qbot difficulty easy | normal | hard | quantum
/qbot target nearest          nächster Spieler (nicht aus deinem Team)
/qbot target <Spieler>        nur diesen Spieler
/qbot target mobs             Spieler und feindliche Mobs (zum Alleine-Testen)
/qbot autorespawn true|false  nach dem Tod automatisch respawnen
/qbot range <4-64>            Suchradius in Blöcken
```

Einstellungen werden in `config/qwantiumbot-client.json` gespeichert.

## Schwierigkeiten

| Stufe | Zielen | Reaktion | Extras |
|---|---|---|---|
| Leicht | langsam, wackelig | 8 Ticks | schlägt oft zu früh, kaum Schild |
| Normal | mittel | 5 Ticks | W-Tap, Strafen, manchmal Crits und Schild |
| Schwer | schnell | 3 Ticks | fast immer W-Tap und Strafen, Crits, Schild, Axt gegen Schild |
| Quantum | sehr schnell, präzise | 1 Tick | volle Ladung bei jedem Schlag, sagt Bewegungen voraus |

## Was der Bot kann

- Flüssiges Zielen auf die nächste Stelle der Hitbox, mit Vorhersage der Gegnerbewegung
- Er schlägt nur, wenn das Fadenkreuz wirklich auf dem Gegner ist, mit normaler Reichweite und Attack-Cooldown
- Sprint-Hits, W-Tap (Sprint-Reset für mehr Knockback), Strafen, Jump-Crits
- Schild in der Offhand zwischen den Schlägen, gegen Bögen und Armbrüste, aber nicht gegen Äxte
- Axt gegen einen hochgehaltenen Schild
- Isst bei wenig Leben einen Goldenen Apfel und geht dabei rückwärts weg
- Isst normales Essen, wenn der Hunger zu niedrig ist
- Legt bei wenig Leben ein Totem in die Offhand und füllt eine leere Offhand mit Schild oder Totem
- Läuft nicht in Lava, Feuer, Kakteen oder von Klippen (mehr als 3 Blöcke)
- Springt über Hindernisse und schwimmt nach oben

Der Bot benutzt nur Items aus der **Hotbar** (1–9), wie ein Spieler im Kampf auch.

## Was (noch) nicht geht

- Kein Pathfinding um Wände und Labyrinthe herum: Er läuft direkt auf den Gegner zu
- Nur Nahkampf: kein Bogen, keine Crystals, Pearls, Mace oder Speer, keine Blöcke platzieren
- Tränke werden nicht benutzt

## Selbst bauen

```
./gradlew build                 # Mod-Jar landet in build/libs/
./gradlew runClient             # Minecraft mit der Mod starten
./gradlew runClientGameTest     # automatischer Test im echten Spiel
```

Der automatische Test (`src/gametest`) erstellt eine Testwelt und prüft:

- der Bot tötet ein stehendes Ziel
- er gewinnt gegen einen Husk, der zurückschlägt
- er isst bei wenig Leben einen Gapple
- er läuft nicht in einen Lavagraben
- ein Server mit der Mod erlaubt den Bot
