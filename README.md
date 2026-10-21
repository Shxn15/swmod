# Star Wars Mod (swmod)

Ein Star Wars Mod für Minecraft **26.3** mit **Fabric** (Loader 0.19.5, Fabric API 0.163.0+26.3).

## Aktueller Stand
- Rubin (`swmod:ruby`) als erstes Test-Item
- Eigener Creative-Tab "Star Wars Mod"

## Entwickeln
Voraussetzung: **JDK 25**.

```bash
./gradlew runClient   # Minecraft mit dem Mod starten
./gradlew build       # Mod-Jar bauen -> build/libs/
```

Ingame testen: `/give @s swmod:ruby` oder im Creative-Tab "Star Wars Mod".
