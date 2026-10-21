package com.shxn15.swmod.combat;

/** Animationen, die der Server an die Clients verteilt. */
public enum SaberAnimation {
	/** Ein Schlag beginnt (Richtung + Ausholzeit). */
	SWING,
	/** Der eigene Schlag wurde geblockt und prallt ab. */
	RECOIL,
	/** Der eigene Schlag wurde perfekt pariert: man taumelt. */
	STAGGER,
	/** Zwei Klingen sind gleichzeitig aufeinandergetroffen. */
	CLASH,
	/** Man hat einen Treffer mit der Klinge abgefangen. */
	BLOCK_HIT,
	/** Man hat perfekt pariert. */
	PARRY,
	/** Die Deckung wurde durchbrochen. */
	GUARD_BREAK,
	/** Das Schwert wird geworfen. */
	THROW,
	/** Das Schwert wird wieder gefangen. */
	CATCH,
	/** Laufende Animation abbrechen (z.B. Schwert ausgeschaltet). */
	CANCEL;

	private static final SaberAnimation[] VALUES = values();

	public int id() {
		return this.ordinal();
	}

	public static SaberAnimation byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : CANCEL;
	}
}
