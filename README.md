# Qwantium PvP Bot

Fabric-Mod für **Minecraft 1.21.11**: Ein PvP-Trainingsbot, der im Kampf **deinen eigenen Charakter** steuert. Er zielt, läuft, sprintet, strafed, schlägt, blockt mit dem Schild und isst Gapples. Damit kann z. B. dein Mitspieler den Bot anmachen, und du trainierst gegen ihn.

Inoffiziell und nicht mit QuantumUltimate / „Quantum's PvP Practice“ verbunden. Die Engine ist selbst geschrieben. Ihr Kampfstil ist dem Schwert-Bot aus Quantums Map nachgebaut (Timings, Reichweiten, W/S-Tap, Crits). Es steckt aber kein Code und keine Datei aus der Map in dieser Mod.

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
/qbot difficulty easy | normal | hard | crazy | quantum
/qbot target nearest          nächster Spieler (nicht aus deinem Team)
/qbot target <Spieler>        nur diesen Spieler
/qbot target mobs             Spieler und feindliche Mobs (zum Alleine-Testen)
/qbot autorespawn true|false  nach dem Tod automatisch respawnen
/qbot range <4-64>            Suchradius in Blöcken
```

Einstellungen werden in `config/qwantiumbot-client.json` gespeichert.

## Schwierigkeiten

Die Stufen entsprechen denen von Quantums Bot. Der Spielstil ist auf allen Stufen gleich. Unterschiede gibt es nur bei der Reichweite (pro Schlag neu ausgewürfelt) und bei der Verzögerung, mit der das Zielen dem Gegner folgt.

| Stufe | Bei Quantum | Reichweite pro Schlag | Ziel-Verzögerung |
|---|---|---|---|
| Leicht (`easy`) | Easy | 1,3–2,2 Blöcke | 1–5 Ticks |
| Mittel (`normal`) | Intermediate | 1,6–2,5 Blöcke | 1–4 Ticks |
| Schwer (`hard`) | Hard | 2,0–2,9 Blöcke | 1–3 Ticks |
| Crazy (`crazy`) | CRAZY | 2,3–3,0 Blöcke | 1–2 Ticks |
| Master (`quantum`) | MASTER | 2,9–3,0 Blöcke | 1–2 Ticks |

## Was der Bot kann

- Kampfstil wie Quantums Schwert-Bot:
  - schlägt alle 12 Ticks
  - würfelt nach jedem Schlag Reichweite, Ziel-Verzögerung und Spielstil neu aus
  - **Combo**: Sprint-Hits, S-Tap (direkt nach dem eigenen Treffer kurz rückwärts), W-Tap (bleibt stehen, wenn der Gegner näher als 1,8 Blöcke ist)
  - **Crit**: springt 2 Ticks nach dem Schlag und trifft im Fallen ohne Sprint
  - Strafen, alle 5 Ticks neu links oder rechts
  - Jump-Reset, wenn er getroffen wird
- Zielt auf die nächste Stelle der Hitbox
- Teammitglieder greift er nur an, wenn im Team Friendly Fire an ist (wie in Quantums Map)
- Er schlägt nur, wenn das Fadenkreuz wirklich auf dem Gegner ist, mit normaler Reichweite und Attack-Cooldown
- Schild in der Offhand zwischen den Schlägen, gegen Bögen und Armbrüste, aber nicht gegen Äxte
- Axt gegen einen hochgehaltenen Schild
- Isst bei wenig Leben einen Goldenen Apfel und geht dabei rückwärts weg
- Isst normales Essen, wenn der Hunger zu niedrig ist
- Legt bei wenig Leben ein Totem in die Offhand und füllt eine leere Offhand mit Schild oder Totem
- Läuft und springt nicht in Lava, Feuer, Kakteen oder von Klippen (mehr als 3 Blöcke)
- Springt über Hindernisse und schwimmt nach oben

Der Bot benutzt nur Items aus der **Hotbar** (1–9), wie ein Spieler im Kampf auch.

## Was (noch) nicht geht

- Kein Pathfinding um Wände und Labyrinthe herum: Er läuft direkt auf den Gegner zu
- Nur Nahkampf: kein Bogen, keine Crystals, Pearls, Mace oder Speer, keine Blöcke platzieren
- Tränke werden nicht benutzt

## Selbst bauen

```
./gradlew build                 # Mod-Jar landet in build/libs/
./gradlew runClient             # Minecraft mit der Mod (und HeroBot als Sparringspartner) starten
./gradlew runClientGameTest     # automatischer Test im echten Spiel
```

[HeroBot](https://modrinth.com/mod/herobot), die Fake-Spieler-Mod, die auch Quantums Map benutzt, ist nur in diesen Testläufen dabei. In der fertigen Jar ist sie nicht enthalten.

Der automatische Test (`src/gametest`) erstellt eine Testwelt und prüft:

- der Bot tötet ein stehendes Ziel
- er gewinnt gegen einen Husk, der zurückschlägt
- er isst bei wenig Leben einen Gapple
- er läuft nicht in einen Lavagraben
- ein Server mit der Mod erlaubt den Bot
- Duelle gegen einen HeroBot-Spieler: perfektes Zielen, Dauersprint, ein Schlag alle 12 Ticks, und wie in Quantums Map sind beide im selben Team mit Friendly Fire. Je 3 Runden auf Mittel, Schwer, Crazy und Master, der Punktestand steht im Log.
- Optional ein Kampf in **Quantums echter Map** gegen seinen Bot: `./gradlew runClientGameTest -PquantumMap=/pfad/zur/entpackten/map`. Der Test lädt eine Kopie der Map, startet einen Schwert-Kampf, lässt unseren Bot 2 Minuten auf Master kämpfen und schreibt Treffer, Totem-Pops und Tode ins Log. Die Map selbst gehört nicht ins Repo.

## Zusammen mit Quantum's PvP Practice testen

1. Minecraft 1.21.11 mit Fabric, dazu im `mods`-Ordner:
   - Fabric API
   - [HeroBot](https://modrinth.com/mod/herobot) 1.21.11
   - diese Mod
2. Quantums Map (v1.18 für 1.21.11) in den `saves`-Ordner entpacken und öffnen.
3. In der Map einen **Schwert- oder Axt-Kit** wählen und den Kampf starten. Unser Bot kann noch keine Crystals, Mace, Speere oder Bögen.
4. Den Quantum-Bot anschauen und **H** drücken, damit unser Bot nur ihn angreift und nicht andere Fake-Spieler in der Lobby. Dann **R** drücken.

Hinweis: Im Schwert-Modus ohne Schild bekommt Quantums Bot jeden Tick ein neues Totem und stirbt deshalb nicht. Gezählt werden die Totem-Pops in der Anzeige rechts.
