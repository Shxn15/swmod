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

### Sonstiges
- Rubin (`swmod:ruby`) als erstes Test-Item

## Entwickeln
Voraussetzung: **JDK 25**.

```bash
./gradlew runClient   # Minecraft mit dem Mod starten
./gradlew build       # Mod-Jar bauen -> build/libs/
```

Automatischer Spieltest (startet ein echtes Minecraft, spielt das Lichtschwert in Einzelspieler
und auf einem dedizierten Server durch und macht Screenshots in `build/run/clientGameTest/screenshots`):

```bash
./gradlew runClientGameTest
```

Texturen und Sounds des Lichtschwerts werden prozedural erzeugt (keine fremden Assets):

```bash
python3 tools/generate_assets.py   # braucht numpy, Pillow und ffmpeg
```
