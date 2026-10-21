package com.shxn15.swmod.force;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Was ein Spieler über die Macht weiß: erlernte Fähigkeiten mit Stufe, die Belegung des
 * Fähigkeitenrads und die gerade gewählte Fähigkeit. Bleibt über Tod und Neustart erhalten
 * und wird automatisch an den eigenen Client synchronisiert (siehe {@code ModAttachments}).
 *
 * <p>Gespeichert werden nur IDs: Fähigkeiten, die es (z.B. nach Entfernen eines Addons) nicht
 * mehr gibt, werden einfach ignoriert.
 *
 * @param levels   erlernte Fähigkeiten und ihre Stufe (1..max)
 * @param wheel    Fähigkeit je Radplatz ({@link #WHEEL_SLOTS} Plätze, im Uhrzeigersinn ab oben)
 * @param selected gewählter Radplatz, -1 = keine Fähigkeit (Rechtsklick wirkt normal)
 */
public record ForceData(Map<Identifier, Integer> levels, List<Optional<Identifier>> wheel, int selected) {
	public static final int WHEEL_SLOTS = 8;
	public static final ForceData EMPTY = new ForceData(Map.of(), List.of(), -1);

	private static final Codec<List<Optional<Identifier>>> WHEEL_CODEC = Codec.STRING.listOf().xmap(
			strings -> strings.stream().map(s -> Optional.ofNullable(s.isEmpty() ? null : Identifier.tryParse(s))).toList(),
			slots -> slots.stream().map(slot -> slot.map(Identifier::toString).orElse("")).toList());

	public static final Codec<ForceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(ForceData::levels),
			WHEEL_CODEC.optionalFieldOf("wheel", List.of()).forGetter(ForceData::wheel),
			Codec.INT.optionalFieldOf("selected", -1).forGetter(ForceData::selected)
	).apply(instance, ForceData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ForceData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.<ByteBuf, Identifier, Integer, Map<Identifier, Integer>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
			ForceData::levels,
			ByteBufCodecs.optional(Identifier.STREAM_CODEC).apply(ByteBufCodecs.list(WHEEL_SLOTS)), ForceData::wheel,
			ByteBufCodecs.VAR_INT, ForceData::selected,
			ForceData::new);

	public ForceData {
		levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
		List<Optional<Identifier>> slots = new ArrayList<>(WHEEL_SLOTS);
		for (int i = 0; i < WHEEL_SLOTS; i++) {
			slots.add(i < wheel.size() && wheel.get(i) != null ? wheel.get(i) : Optional.empty());
		}
		wheel = List.copyOf(slots);
		if (selected < -1 || selected >= WHEEL_SLOTS) {
			selected = -1;
		}
	}

	// ------------------------------------------------------------------------------------------
	// Stufen
	// ------------------------------------------------------------------------------------------

	public int level(Identifier ability) {
		return this.levels.getOrDefault(ability, 0);
	}

	public int level(ForceAbility ability) {
		return Math.min(ability.maxLevel(), this.level(ability.id()));
	}

	public boolean knows(ForceAbility ability) {
		return this.level(ability) > 0;
	}

	public ForceData withLevel(Identifier ability, int level) {
		Map<Identifier, Integer> copy = new LinkedHashMap<>(this.levels);
		if (level <= 0) {
			copy.remove(ability);
		} else {
			copy.put(ability, level);
		}
		return new ForceData(copy, this.wheel, this.selected);
	}

	/** Summe aller erlernten Stufen (bekannter Fähigkeiten): vergrößert den Machtvorrat. */
	public int totalLevels() {
		int total = 0;
		for (ForceAbility ability : ForceAbilities.REGISTRY) {
			total += this.level(ability);
		}
		return total;
	}

	// ------------------------------------------------------------------------------------------
	// Rad
	// ------------------------------------------------------------------------------------------

	public @Nullable Identifier slot(int index) {
		return index >= 0 && index < WHEEL_SLOTS ? this.wheel.get(index).orElse(null) : null;
	}

	/** Die Fähigkeit auf einem Radplatz, falls bekannt und erlernt. */
	public @Nullable ForceAbility abilityInSlot(int index) {
		Identifier id = this.slot(index);
		ForceAbility ability = id != null ? ForceAbilities.REGISTRY.getValue(id) : null;
		return ability != null && this.knows(ability) ? ability : null;
	}

	public @Nullable ForceAbility selectedAbility() {
		return this.abilityInSlot(this.selected);
	}

	/** Legt eine Fähigkeit auf einen Platz. Jede Fähigkeit liegt höchstens einmal im Rad. */
	public ForceData withSlot(int index, @Nullable Identifier ability) {
		if (index < 0 || index >= WHEEL_SLOTS) {
			return this;
		}
		List<Optional<Identifier>> slots = new ArrayList<>(this.wheel);
		if (ability != null) {
			for (int i = 0; i < WHEEL_SLOTS; i++) {
				if (slots.get(i).filter(ability::equals).isPresent()) {
					slots.set(i, Optional.empty());
				}
			}
		}
		slots.set(index, Optional.ofNullable(ability));
		return new ForceData(this.levels, slots, this.selected);
	}

	public ForceData withSelected(int index) {
		return new ForceData(this.levels, this.wheel, index);
	}

	public int slotOf(Identifier ability) {
		for (int i = 0; i < WHEEL_SLOTS; i++) {
			if (this.wheel.get(i).filter(ability::equals).isPresent()) {
				return i;
			}
		}
		return -1;
	}

	public int firstFreeSlot() {
		for (int i = 0; i < WHEEL_SLOTS; i++) {
			if (this.abilityInSlot(i) == null) {
				return i;
			}
		}
		return -1;
	}
}
