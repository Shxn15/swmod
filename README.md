# Star Wars Mod (swmod)

Ein Star Wars Mod für Minecraft **26.3** mit **Fabric** (Loader 0.19.5, Fabric API 0.163.0+26.3).

## Inhalt

### Lichtschwert
Kampfsystem inspiriert von *Movie Battles II*. Im Creative-Tab "Star Wars Mod" gibt es Lichtschwerter in 8 Farben.

| Eingabe | Aktion |
| --- | --- |
| **X** | Klinge zünden / ausschalten |
| **Linksklick halten + Maus bewegen** | Schlag in Mausrichtung (8 Richtungen: links↔rechts, oben↔unten, diagonal) |
| **Kurzer Linksklick** | Schlag per Bewegungstaste (W = Überkopf, S = Aufwärts, A/D = seitlich), ohne Taste: Stoß |
| **Rechtsklick (halten)** | Deckung. Kurz bevor ein Schlag trifft gedrückt = **perfekte Parade** |
| **F** | Lichtschwert werfen (kommt wie ein Bumerang zurück) |

**Kampfmechanik**
- 10 Schaden pro Treffer (Schlag oder Wurf). Getroffen wird genau dort, wo die Klinge sichtbar entlangfährt.
- Schläge lassen sich zu Kombos verketten. Gegenläufige Schläge (z.B. links→rechts, dann rechts→links) holen schneller aus.
- **Blocken** fängt Lichtschwerthiebe, Nahkampf von Mobs und Geschosse von vorne ab und kostet Blockpunkte (Balken unter dem Fadenkreuz).
- **Perfekte Parade** (Rechtsklick höchstens 0,3 s vor dem Treffer): kostet nichts, der Angreifer taumelt, und dein nächster Schlag kommt schneller (Konter). Pfeile fliegen dorthin zurück, wohin du schaust.
- Sind die Blockpunkte leer, **bricht die Deckung**: kurz kein Blocken möglich.
- Treffen zwei Schläge gleichzeitig aufeinander, **prallen die Klingen ab** (Funken, beide werden zurückgestoßen).
- Mannequins können Lichtschwerter halten (gezündet als Deko).

Alle Balance-Werte stehen in `combat/SaberTuning.java`.

### Die Macht
Fähigkeiten lernt und verbessert man im **Holocron** (Rezept: Amethystscherben in den Ecken,
Goldbarren an den Seiten, Enderauge in der Mitte). Rechtsklick mit dem Holocron öffnet es:
Fähigkeit anklicken, mit Erfahrungsstufen lernen bzw. aufwerten (3 Stufen je Fähigkeit) und per
Ziehen oder Zahlentaste 1–8 auf einen Platz des Fähigkeitenrads legen.

| Eingabe | Aktion |
| --- | --- |
| **C halten** | Fähigkeitenrad öffnen, mit der Maus wählen, Loslassen übernimmt. Mitte = keine Fähigkeit |
| **C kurz antippen** | Rad bleibt offen: Klick, C oder 1–8 wählt, 9 = keine Fähigkeit, Mausrad blättert |
| **Rechtsklick** | Gewählte Fähigkeit wirken (Blitze, Griff und Würgen: halten) |
| **Mausrad** (beim Machtgriff) | Gegriffenes näher heranholen / weiter wegschieben |
| **Linksklick** (beim Machtgriff) | Gegriffenes in Blickrichtung schleudern |

Ist keine Fähigkeit gewählt (oder hält man das Holocron), funktioniert Rechtsklick ganz normal
(Blocken, Bauen, Essen).

| Fähigkeit | Wirkung | Ab Stufe 2/3 |
| --- | --- | --- |
| **Machtstoß** | Druckwelle schleudert alles im Blickkegel weg; wer gegen eine Wand prallt, nimmt Schaden. Geschosse fliegen zurück | mehr Reichweite, Breite und Wucht |
| **Machtzug** | Zieht Gegner, Items und Erfahrung heran | Stufe 3 reißt Mobs die Waffe aus der Hand |
| **Machtgriff** | Hebt ein Wesen leuchtend an (wie in LEGO Star Wars III), man trägt es mit dem Blick herum | größere Wesen, stärkerer Wurf |
| **Machtgeschwindigkeit** | +60 % Tempo mit Lichtschlieren und Speedlines im Bild | +100 %/+150 %, länger, Stufen hochlaufen, weicher landen |
| **Machtheilung** | Heilt 3 Herzen plus Regeneration | heilt Gift/Wither, auf Stufe 3 auch Verbündete in der Nähe |
| **Machtblitze** | Halten: animierte Blitze aus der Hand in Gegner im Blickkegel | mehr Ziele, Stufe 3 springt auf weitere Gegner über |
| **Machtwürgen** | Halten: Opfer schwebt rot leuchtend in der Luft und verliert stetig Leben (ignoriert Rüstung) | mehr Schaden, Stufe 3 schleudert beim Loslassen |

- Die **Machtleiste** sitzt direkt unter der XP-Leiste und funktioniert wie Energie: Wirken kostet
  Macht, Blitze/Griff/Würgen verbrauchen laufend, nach kurzer Pause lädt sie sich von selbst wieder
  auf. Jede gelernte Stufe vergrößert den Vorrat (100 + 4 je Stufe).
- Rechts neben der Hotbar zeigt ein Abzeichen die gewählte Fähigkeit samt Abklingzeit.
- Ein Lichtschwert in Deckung fängt Machtblitze ab (kostet Blockpunkte).
- Befehl für Tests und Server: `/force learn <Spieler> <Fähigkeit> [Stufe]`, `/force learnall <Spieler>`,
  `/force reset <Spieler>`, `/force refill <Spieler>`.

Alle Balance-Werte stehen in `force/ForceTuning.java`. Neue Fähigkeiten: Klasse von `ForceAbility`
ableiten, in `ForceAbilities` registrieren (andere Mods über `ForceAbilities.register`), Symbol unter
`textures/gui/sprites/force/<name>.png` und Übersetzungen `force.<namespace>.<name>` und `….desc` –
Holocron, Rad und HUD finden sie automatisch.

### Sonstiges
- Rubin (`swmod:ruby`) als erstes Test-Item

## Entwickeln
Voraussetzung: **JDK 25**.

```bash
./gradlew runClient   # Minecraft mit dem Mod starten
./gradlew build       # Mod-Jar bauen -> build/libs/
```

Automatischer Spieltest (startet ein echtes Minecraft, spielt das Lichtschwert in Einzelspieler
und auf einem dedizierten Server sowie Holocron, Fähigkeitenrad und alle Machtfähigkeiten durch und
macht Screenshots in `build/run/clientGameTest/screenshots`):

```bash
./gradlew runClientGameTest
```

Texturen und Sounds (Lichtschwert, Machtfähigkeiten, Holocron) werden prozedural erzeugt (keine fremden Assets):

```bash
python3 tools/generate_assets.py         # alles; braucht numpy, Pillow und ffmpeg
python3 tools/generate_assets.py force   # nur die Macht-Assets
```
