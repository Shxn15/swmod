package com.shxn15.swmod.force;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/** Serverseitiger Laufzeitzustand eines Spielers (Machtvorrat, Abklingzeiten, laufender Kanal). */
public final class ForceState {
	float force = -1f;
	float maxForce = ForceTuning.BASE_FORCE;
	int lastSpendTick = -1000;
	final Object2IntMap<Identifier> cooldownUntil = new Object2IntOpenHashMap<>();
	@Nullable Channel channel;
	/** Eigene Daten der Fähigkeiten, z.B. andauernde Wirkungen wie Machtgeschwindigkeit. */
	private final Map<ForceAbility, Object> abilityData = new IdentityHashMap<>();

	// --- HUD-Synchronisation ---
	float sentForce = -1f;
	float sentMaxForce = -1f;
	boolean statusDirty = true;
	int lastStatusSync;

	/** Eine gerade gehaltene Kanal-Fähigkeit. */
	static final class Channel {
		final ForceAbility ability;
		final int level;
		final int startTick;
		final Object data;

		Channel(ForceAbility ability, int level, int startTick, Object data) {
			this.ability = ability;
			this.level = level;
			this.startTick = startTick;
			this.data = data;
		}
	}

	public float force() {
		return this.force;
	}

	public float maxForce() {
		return this.maxForce;
	}

	public boolean isChanneling(ForceAbility ability) {
		return this.channel != null && this.channel.ability == ability;
	}

	public @Nullable Object channelData() {
		return this.channel != null ? this.channel.data : null;
	}

	public int cooldownLeft(ForceAbility ability, int now) {
		return Math.max(0, this.cooldownUntil.getInt(ability.id()) - now);
	}

	@SuppressWarnings("unchecked")
	public <T> @Nullable T data(ForceAbility ability) {
		return (T) this.abilityData.get(ability);
	}

	@SuppressWarnings("unchecked")
	public <T> T data(ForceAbility ability, Supplier<T> factory) {
		return (T) this.abilityData.computeIfAbsent(ability, a -> factory.get());
	}

	public void clearData(ForceAbility ability) {
		this.abilityData.remove(ability);
	}
}
