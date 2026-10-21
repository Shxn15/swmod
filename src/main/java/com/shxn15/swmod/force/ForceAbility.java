package com.shxn15.swmod.force;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Eine Machtfähigkeit. Neue Fähigkeiten erben von dieser Klasse und werden in
 * {@link ForceAbilities#REGISTRY} registriert; Holocron, Rad, HUD und Netzwerk arbeiten
 * danach automatisch mit ihr. Für Symbol, Namen und Beschreibung gelten Konventionen:
 * <ul>
 *     <li>Symbol: {@code assets/<namespace>/textures/gui/sprites/force/<name>.png}</li>
 *     <li>Name: {@code force.<namespace>.<name>}, Beschreibung: {@code force.<namespace>.<name>.desc}</li>
 * </ul>
 *
 * <p>Alle Spiellogik läuft auf dem Server. Visuelle Effekte werden über
 * {@link ForceContext#fx} an die Clients geschickt.
 */
public abstract class ForceAbility {
	public enum Activation {
		/** Ein Rechtsklick, eine Wirkung (z.B. Stoß). */
		INSTANT,
		/** Wirkt, solange die rechte Maustaste gehalten wird (z.B. Blitze). */
		CHANNEL
	}

	private final Activation activation;
	private final ForceSide side;
	private final int color;
	private final int maxLevel;

	/**
	 * @param color Themenfarbe für Symbol, Rad und Effekte
	 */
	protected ForceAbility(Activation activation, ForceSide side, int color, int maxLevel) {
		this.activation = activation;
		this.side = side;
		this.color = color;
		this.maxLevel = maxLevel;
	}

	public final Identifier id() {
		return Objects.requireNonNull(ForceAbilities.REGISTRY.getKey(this), "Fähigkeit nicht registriert");
	}

	public String translationKey() {
		Identifier id = this.id();
		return "force." + id.getNamespace() + "." + id.getPath();
	}

	public Component name() {
		return Component.translatable(this.translationKey());
	}

	public Component description() {
		return Component.translatable(this.translationKey() + ".desc");
	}

	/** GUI-Sprite des Symbols. */
	public Identifier icon() {
		Identifier id = this.id();
		return Identifier.fromNamespaceAndPath(id.getNamespace(), "force/" + id.getPath());
	}

	public Activation activation() {
		return this.activation;
	}

	public ForceSide side() {
		return this.side;
	}

	public int color() {
		return this.color;
	}

	public int maxLevel() {
		return this.maxLevel;
	}

	// ------------------------------------------------------------------------------------------
	// Kosten
	// ------------------------------------------------------------------------------------------

	/** Erfahrungslevel, die das Erlernen bzw. Verbessern auf {@code level} kostet. */
	public abstract int learnCost(int level);

	/** Macht, die das Wirken kostet (bei Kanal-Fähigkeiten: der Start). */
	public abstract float cost(int level);

	/** Macht pro Tick, solange eine Kanal-Fähigkeit gehalten wird. */
	public float drain(int level) {
		return 0f;
	}

	/** Abklingzeit in Ticks, bei Kanal-Fähigkeiten ab dem Loslassen. */
	public abstract int cooldown(int level);

	/** Werte je Stufe für das Holocron. */
	public abstract List<ForceStat> stats();

	// ------------------------------------------------------------------------------------------
	// Serverlogik
	// ------------------------------------------------------------------------------------------

	/**
	 * Wirkt eine Sofort-Fähigkeit.
	 *
	 * @return false, wenn nichts passiert ist (dann fallen weder Kosten noch Abklingzeit an)
	 */
	public boolean cast(ForceContext context) {
		return false;
	}

	/**
	 * Beginnt eine Kanal-Fähigkeit.
	 *
	 * @return beliebige Daten für die Dauer des Kanals, oder null, wenn er nicht starten kann
	 */
	public @Nullable Object startChannel(ForceContext context) {
		return null;
	}

	/** @return false beendet den Kanal */
	public boolean tickChannel(ForceContext context, Object data) {
		return false;
	}

	public void stopChannel(ForceContext context, Object data) {
	}

	/**
	 * Steuerung während des Kanals (Mausrad, Linksklick), siehe {@link com.shxn15.swmod.network.ForceControlPayload}.
	 *
	 * @return true beendet den Kanal
	 */
	public boolean control(ForceContext context, Object data, int action, float value) {
		return false;
	}

	/**
	 * Wird jeden Tick für jeden Spieler aufgerufen (auch wenn er die Fähigkeit nicht kennt, dann ist
	 * {@code level} 0), z.B. um zeitlich begrenzte Wirkungen auslaufen zu lassen.
	 */
	public void tickPlayer(ServerPlayer player, ForceState state, int level, int now) {
	}

	/** Entfernt alle andauernden Wirkungen (Tod, Wiederbelebung, Zurücksetzen). */
	public void reset(ServerPlayer player, ForceState state) {
	}
}
