package com.shxn15.swmod.client.force.fx;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.combat.SaberFrame;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceParticleOptions;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.network.ForceFxPayload;

import static com.shxn15.swmod.force.ForceParticleOptions.*;

/**
 * Alle sichtbaren Machteffekte auf dem Client: laufende Kanäle (für Blitze, Auren und Gesten),
 * leuchtende Wesen, Machtgeschwindigkeit, Partikel-Choreografien und Bildschirmeffekte.
 */
public final class ForceFx {
	/** Ein laufender Kanal (Griff, Würgen, Blitze) eines Wirkenden. */
	public static final class Channel {
		public final ForceAbility ability;
		public final int level;
		public final int start;
		public IntList targets;
		public IntList extra;

		Channel(ForceAbility ability, int level, int start, IntList targets, IntList extra) {
			this.ability = ability;
			this.level = level;
			this.start = start;
			this.targets = targets;
			this.extra = extra;
		}

		/** Blitzketten als Paare (von, nach). */
		public IntList chains() {
			IntList result = new IntArrayList();
			if (this.extra.isEmpty()) return result;
			int count = this.extra.getInt(0);
			for (int i = 0; i < count * 2 && 1 + i < this.extra.size(); i++) {
				result.add(this.extra.getInt(1 + i));
			}
			return result;
		}

		/** Ziele, deren Lichtschwert die Blitze abfängt. */
		public IntList blocked() {
			IntList result = new IntArrayList();
			if (this.extra.isEmpty()) return result;
			int index = 1 + this.extra.getInt(0) * 2;
			if (index >= this.extra.size()) return result;
			int count = this.extra.getInt(index);
			for (int i = 0; i < count && index + 1 + i < this.extra.size(); i++) {
				result.add(this.extra.getInt(index + 1 + i));
			}
			return result;
		}
	}

	/** Leuchten um ein Wesen (LEGO-Star-Wars-Stil), blendet weich ein und aus. */
	public static final class Aura {
		int color;
		float strength;
		float prevStrength;
		float target;
		boolean flicker;
		int holdUntil;
	}

	/** Armbewegung beim Wirken. */
	public record Gesture(ForceAbility ability, int start, int duration, boolean channel) {
	}

	/** Wiederholte Partikel über einige Ticks (z.B. Heilspirale, Druckwellen). */
	private record Emitter(int entity, int start, int until, int kind, int color, Vec3 origin, Vec3 direction) {
	}

	private static final int EMIT_HEAL = 0;
	private static final int EMIT_PUSH_WAVE = 1;
	private static final int EMIT_PULL_WAVE = 2;
	private static final int EMIT_TRAIL = 3;
	private static final int EMIT_LEARN = 4;

	private static final Int2ObjectMap<Channel> CHANNELS = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Aura> AURAS = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Gesture> GESTURES = new Int2ObjectOpenHashMap<>();
	private static final Int2IntMap SPEED_UNTIL = new Int2IntOpenHashMap();
	private static final List<Emitter> EMITTERS = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private ForceFx() {
	}

	private static int now() {
		return ForceClientState.ticks();
	}

	// ------------------------------------------------------------------------------------------
	// Pakete vom Server
	// ------------------------------------------------------------------------------------------

	public static void handle(ForceFxPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		ForceAbility ability = ForceAbilities.REGISTRY.getValue(payload.ability());
		if (level == null || ability == null) {
			return;
		}
		Entity caster = level.getEntity(payload.caster());
		IntList targets = new IntArrayList(payload.targets());
		IntList extra = new IntArrayList(payload.extra());

		switch (payload.kind()) {
			case ForceFxPayload.CAST -> onCast(level, caster, ability, payload.level(), targets, extra);
			case ForceFxPayload.CHANNEL_START -> {
				CHANNELS.put(payload.caster(), new Channel(ability, payload.level(), now(), targets, extra));
				GESTURES.put(payload.caster(), new Gesture(ability, now(), 0, true));
				onChannelStart(level, caster, ability, targets);
				ForceLoopSounds.start(caster, ability);
			}
			case ForceFxPayload.CHANNEL_UPDATE -> {
				Channel channel = CHANNELS.get(payload.caster());
				if (channel != null) {
					channel.targets = targets;
					channel.extra = extra;
				} else {
					CHANNELS.put(payload.caster(), new Channel(ability, payload.level(), now(), targets, extra));
				}
			}
			case ForceFxPayload.CHANNEL_END -> {
				CHANNELS.remove(payload.caster());
				Gesture gesture = GESTURES.get(payload.caster());
				if (gesture != null && gesture.channel()) {
					GESTURES.put(payload.caster(), new Gesture(ability, now() - 6, 12, false));
				}
				for (int id : targets) {
					Entity target = level.getEntity(id);
					if (target != null) {
						burst(level, center(target), ability.color(), 10, 0.25);
					}
				}
			}
			case ForceFxPayload.IMPACT -> {
				for (int id : targets) {
					Entity target = level.getEntity(id);
					if (target != null) {
						onImpact(level, target, extra.isEmpty() ? 100 : extra.getInt(0));
					}
				}
			}
			case ForceFxPayload.EFFECT_END -> {
				SPEED_UNTIL.remove(payload.caster());
				if (caster != null) {
					ring(level, caster.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 0x9FE0FF, 0.3f, 0.12f, 10);
				}
			}
			case ForceFxPayload.LEARNED -> {
				// Lichtspirale in der Farbe der Fähigkeit steigt um den Lernenden auf.
				if (caster != null) {
					ring(level, caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0), ability.color(), 0.3f, 0.12f + payload.level() * 0.03f, 16);
					EMITTERS.add(new Emitter(caster.getId(), now(), now() + 20 + payload.level() * 6, EMIT_LEARN, ability.color(), Vec3.ZERO, Vec3.ZERO));
				}
			}
			default -> {
			}
		}
	}

	private static void onCast(ClientLevel level, @Nullable Entity caster, ForceAbility ability, int abilityLevel, IntList targets, IntList extra) {
		if (caster == null) {
			return;
		}
		Vec3 hand = handPos(caster, 1f);
		Vec3 look = caster.getLookAngle();
		int color = ability.color();
		LocalPlayer self = Minecraft.getInstance().player;

		if (ability == ForceAbilities.PUSH) {
			GESTURES.put(caster.getId(), new Gesture(ability, now(), 12, false));
			// In der Ego-Perspektive beginnt die Welle ein Stück vor der Kamera, sonst blendet sie nur.
			boolean ownView = isOwnView(caster);
			Vec3 origin = ownView ? caster.getEyePosition().add(look.scale(1.4)) : hand;
			EMITTERS.add(new Emitter(caster.getId(), now(), now() + 5, EMIT_PUSH_WAVE, color, origin, look));
			int streaks = ownView ? 14 + abilityLevel * 5 : 26 + abilityLevel * 8;
			for (int i = 0; i < streaks; i++) {
				Vec3 dir = spread(look, 0.35 + abilityLevel * 0.05);
				Vec3 start = ownView ? origin.add(dir.scale(RANDOM.nextDouble() * 0.8)) : hand;
				spawn(level, STREAK, i % 3 == 0 ? 0xFFFFFF : color, ownView ? 0.24f : 0.35f, 8 + RANDOM.nextInt(6), start,
						dir.scale(0.7 + RANDOM.nextDouble() * 0.8));
			}
			spawn(level, FLASH, 0xDDF2FF, ownView ? 0.28f : 0.9f, 5, hand.add(look.scale(ownView ? 0.15 : 0.4)), Vec3.ZERO);
			if (ownView) ForceScreenEffects.wave(false);
			for (int id : targets) {
				Entity target = level.getEntity(id);
				if (target != null) {
					burst(level, center(target), color, 8, 0.3);
					spawn(level, FLASH, 0xFFFFFF, target.getBbWidth() * 1.4f, 4, center(target), Vec3.ZERO);
					if (target == self) ForceScreenEffects.shake(0.8f);
				}
			}
			if (caster == self) ForceScreenEffects.shake(0.3f);
		} else if (ability == ForceAbilities.PULL) {
			GESTURES.put(caster.getId(), new Gesture(ability, now(), 12, false));
			EMITTERS.add(new Emitter(caster.getId(), now(), now() + 5, EMIT_PULL_WAVE, color, hand, look));
			if (isOwnView(caster)) ForceScreenEffects.wave(true);
			for (int id : targets) {
				Entity target = level.getEntity(id);
				if (target == null) continue;
				Vec3 from = center(target);
				Vec3 toHand = hand.subtract(from);
				for (int i = 0; i < 6; i++) {
					spawn(level, STREAK, color, 0.3f, 8, from.add(jitter(0.4)), toHand.scale(0.12 + RANDOM.nextDouble() * 0.05));
				}
				burst(level, from, color, 6, 0.15);
				if (target == self) ForceScreenEffects.shake(0.5f);
			}
			for (int id : extra) {
				EMITTERS.add(new Emitter(id, now(), now() + 20, EMIT_TRAIL, 0xFFE9A0, Vec3.ZERO, Vec3.ZERO));
			}
		} else if (ability == ForceAbilities.SPEED) {
			int duration = extra.isEmpty() ? 120 : extra.getInt(0);
			SPEED_UNTIL.put(caster.getId(), now() + duration);
			GESTURES.put(caster.getId(), new Gesture(ability, now(), 8, false));
			Vec3 feet = caster.position().add(0, 0.1, 0);
			ring(level, feet, new Vec3(0, 1, 0), color, 0.4f, 0.22f, 12);
			ring(level, feet.add(0, 0.9, 0), new Vec3(0, 1, 0), 0xFFFFFF, 0.3f, 0.16f, 9);
			for (int i = 0; i < 28; i++) {
				float angle = RANDOM.nextFloat() * Mth.TWO_PI;
				Vec3 dir = new Vec3(Mth.sin(angle), 0.15 + RANDOM.nextDouble() * 0.3, Mth.cos(angle));
				spawn(level, STREAK, i % 2 == 0 ? color : 0xFFFFFF, 0.4f, 10, feet.add(0, RANDOM.nextDouble() * 1.6, 0), dir.scale(0.5));
			}
			if (caster == self) ForceScreenEffects.speedBurst();
		} else if (ability == ForceAbilities.HEAL) {
			GESTURES.put(caster.getId(), new Gesture(ability, now(), 18, false));
			for (int id : targets) {
				Entity target = level.getEntity(id);
				if (target == null) continue;
				EMITTERS.add(new Emitter(id, now(), now() + 30, EMIT_HEAL, color, Vec3.ZERO, Vec3.ZERO));
				ring(level, target.position().add(0, 0.05, 0), new Vec3(0, 1, 0), color, 0.3f, 0.14f, 16);
				aura(id, color, 1f, now() + 26, false);
				if (target == self) ForceScreenEffects.healFlash();
			}
		}
	}

	private static void onChannelStart(ClientLevel level, @Nullable Entity caster, ForceAbility ability, IntList targets) {
		if (caster == null) {
			return;
		}
		Vec3 hand = handPos(caster, 1f);
		if (ability == ForceAbilities.LIGHTNING) {
			spawn(level, FLASH, 0xE8DDFF, isOwnView(caster) ? 0.3f : 1.2f, 5, hand, Vec3.ZERO);
			for (int i = 0; i < 10; i++) {
				spawn(level, SPARK, 0xD8C8FF, 0.08f, 8 + RANDOM.nextInt(6), hand, spread(caster.getLookAngle(), 0.8).scale(0.3));
			}
			return;
		}
		for (int id : targets) {
			Entity target = level.getEntity(id);
			if (target == null) continue;
			Vec3 c = center(target);
			if (ability == ForceAbilities.GRIP) {
				for (int i = 0; i < 14; i++) {
					spawn(level, SMOKE, 0x200006, 0.5f + RANDOM.nextFloat() * 0.4f, 20 + RANDOM.nextInt(10), c.add(jitter(target.getBbWidth() * 0.6)),
							new Vec3(0, 0.02 + RANDOM.nextDouble() * 0.03, 0));
				}
				spawn(level, FLASH, 0xFF3030, target.getBbWidth() * 1.8f, 6, throat(target), Vec3.ZERO);
			} else {
				burst(level, c, ability.color(), 18, 0.2);
				spawn(level, FLASH, 0xCFEFFF, target.getBbWidth() * 2f, 6, c, Vec3.ZERO);
			}
		}
	}

	private static void onImpact(ClientLevel level, Entity target, int speedPercent) {
		Vec3 c = center(target);
		float strength = Mth.clamp(speedPercent / 150f, 0.3f, 1.5f);
		spawn(level, FLASH, 0xFFFFFF, target.getBbWidth() * 1.6f * strength, 4, c, Vec3.ZERO);
		for (int i = 0; i < 10; i++) {
			spawn(level, SPARK, 0xFFF2D0, 0.07f, 8 + RANDOM.nextInt(8), c, jitter(0.5).scale(strength));
		}
		LocalPlayer self = Minecraft.getInstance().player;
		if (self != null) {
			double distance = self.distanceTo(target);
			if (distance < 10) {
				ForceScreenEffects.shake((float) (strength * (target == self ? 1.0 : 0.5) * (1 - distance / 10)));
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// Tick: Auren, Partikel der laufenden Kanäle und Wirkungen
	// ------------------------------------------------------------------------------------------

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			clear();
			return;
		}
		int now = now();
		LocalPlayer self = minecraft.player;

		// Auren der laufenden Kanäle
		for (Int2ObjectMap.Entry<Channel> entry : CHANNELS.int2ObjectEntrySet()) {
			Channel channel = entry.getValue();
			Entity caster = level.getEntity(entry.getIntKey());
			if (caster == null) continue;

			if (channel.ability == ForceAbilities.GRAB || channel.ability == ForceAbilities.GRIP) {
				for (int id : channel.targets) {
					Entity target = level.getEntity(id);
					if (target == null) continue;
					aura(id, channel.ability.color(), 1f, now + 2, false);
					tickHeld(level, caster, target, channel.ability);
					if (target == self && channel.ability == ForceAbilities.GRIP) {
						ForceScreenEffects.gripped();
					}
				}
			} else if (channel.ability == ForceAbilities.LIGHTNING) {
				tickLightning(level, caster, channel, self);
			}
		}

		// Machtgeschwindigkeit: Nachleuchten und Lichtschlieren hinter dem Läufer
		SPEED_UNTIL.int2IntEntrySet().removeIf(entry -> entry.getIntValue() < now);
		for (Int2IntMap.Entry entry : SPEED_UNTIL.int2IntEntrySet()) {
			Entity entity = level.getEntity(entry.getIntKey());
			if (entity == null) continue;
			Vec3 motion = entity.position().subtract(entity.xo, entity.yo, entity.zo);
			if (motion.horizontalDistanceSqr() > 0.01) {
				Vec3 back = motion.normalize().scale(-0.25);
				for (int i = 0; i < 2; i++) {
					Vec3 pos = entity.position().add((RANDOM.nextDouble() - 0.5) * entity.getBbWidth(), RANDOM.nextDouble() * entity.getBbHeight(),
							(RANDOM.nextDouble() - 0.5) * entity.getBbWidth());
					if (entity != self || !minecraft.options.getCameraType().isFirstPerson()) {
						spawn(level, STREAK, i == 0 ? ForceAbilities.SPEED.color() : 0xFFFFFF, 0.45f, 7, pos, back);
					}
				}
			}
		}

		tickEmitters(level, now);

		// Auren ein-/ausblenden
		AURAS.int2ObjectEntrySet().removeIf(entry -> {
			Aura aura = entry.getValue();
			aura.prevStrength = aura.strength;
			float target = now <= aura.holdUntil ? aura.target : 0f;
			aura.strength = target > aura.strength ? Math.min(target, aura.strength + 0.22f) : Math.max(target, aura.strength - 0.12f);
			return aura.strength <= 0f && now > aura.holdUntil;
		});

		GESTURES.int2ObjectEntrySet().removeIf(entry -> {
			Gesture gesture = entry.getValue();
			return !gesture.channel() && now - gesture.start() > gesture.duration();
		});
	}

	/** Funkeln und Leuchtfäden um ein gegriffenes oder gewürgtes Wesen. */
	private static void tickHeld(ClientLevel level, Entity caster, Entity target, ForceAbility ability) {
		Vec3 c = center(target);
		float w = target.getBbWidth();
		float h = target.getBbHeight();
		if (ability == ForceAbilities.GRIP) {
			Vec3 throat = throat(target);
			spawn(level, SPARKLE, RANDOM.nextBoolean() ? 0xFF3030 : 0xFF8070, 0.14f, 10, throat.add(jitter(w * 0.35)), jitter(0.02));
			if (RANDOM.nextInt(2) == 0) {
				spawn(level, SMOKE, 0x1A0004, 0.35f + RANDOM.nextFloat() * 0.3f, 22, c.add(jitter(w * 0.5)), new Vec3(0, 0.03, 0));
			}
			if (RANDOM.nextInt(4) == 0) {
				spawn(level, FLASH, 0xFF2020, w * 1.1f, 4, throat, Vec3.ZERO);
			}
			// Dunkle Schlieren von der Hand zum Opfer.
			Vec3 hand = handPos(caster, 1f);
			Vec3 to = c.subtract(hand);
			spawn(level, SMOKE, 0x300008, 0.25f, 12, hand.add(jitter(0.1)), to.scale(1 / 12.0));
		} else {
			for (int i = 0; i < 2; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				Vec3 pos = new Vec3(c.x + Math.sin(angle) * w * 0.75, target.getY() + RANDOM.nextDouble() * h, c.z + Math.cos(angle) * w * 0.75);
				spawn(level, SPARKLE, i == 0 ? 0xFFFFFF : ability.color(), 0.12f + RANDOM.nextFloat() * 0.08f, 12, pos, new Vec3(0, 0.02, 0));
			}
			// Lichtstrom von der Hand zum Gegriffenen; direkt vor der eigenen Kamera beginnt er etwas weiter vorn.
			Vec3 hand = handPos(caster, 1f);
			Vec3 to = c.subtract(hand);
			Vec3 start = isOwnView(caster) ? hand.add(to.scale(0.2)) : hand;
			spawn(level, MOTE, 0x9FD8FF, 0.12f, 10, start.add(jitter(0.05)), c.subtract(start).scale(1 / 10.0));
		}
	}

	private static void tickLightning(ClientLevel level, Entity caster, Channel channel, @Nullable LocalPlayer self) {
		Vec3 hand = handPos(caster, 1f);
		if (RANDOM.nextInt(2) == 0) {
			// Flackern in der Handfläche; vor der eigenen Kamera klein, sonst überstrahlt es das halbe Bild.
			float size = isOwnView(caster) ? 0.14f + RANDOM.nextFloat() * 0.1f : 0.5f + RANDOM.nextFloat() * 0.4f;
			spawn(level, FLASH, 0xD8C8FF, size, 2, hand, Vec3.ZERO);
		}
		IntList blocked = channel.blocked();
		IntList all = new IntArrayList(channel.targets);
		IntList chains = channel.chains();
		for (int i = 1; i < chains.size(); i += 2) {
			all.add(chains.getInt(i));
		}
		for (int id : all) {
			Entity target = level.getEntity(id);
			if (target == null) continue;
			boolean isBlocked = blocked.contains(id);
			Vec3 point = isBlocked ? LightningRenderer.saberPoint(target, 1f) : center(target).add(jitter(target.getBbWidth() * 0.3));
			for (int i = 0; i < 2; i++) {
				spawn(level, SPARK, i == 0 ? 0xFFFFFF : 0xB98CFF, 0.06f, 6 + RANDOM.nextInt(6), point, jitter(0.25).add(0, 0.12, 0));
			}
			if (!isBlocked) {
				aura(id, 0xC8B0FF, 0.8f + RANDOM.nextFloat() * 0.4f, ForceClientState.ticks() + 3, true);
				if (target == self) ForceScreenEffects.shocked();
			}
		}
	}

	private static void tickEmitters(ClientLevel level, int now) {
		EMITTERS.removeIf(emitter -> {
			int age = now - emitter.start();
			switch (emitter.kind()) {
				case EMIT_HEAL -> {
					Entity entity = level.getEntity(emitter.entity());
					if (entity != null) {
						float progress = age / (float) (emitter.until() - emitter.start());
						for (int strand = 0; strand < 3; strand++) {
							double angle = age * 0.55 + strand * Math.PI * 2 / 3;
							double radius = entity.getBbWidth() * 0.75 * (1 - progress * 0.4);
							Vec3 pos = entity.position().add(Math.sin(angle) * radius, progress * entity.getBbHeight() * 1.1 + 0.1, Math.cos(angle) * radius);
							int color = strand == 0 ? 0xFFF0A0 : strand == 1 ? emitter.color() : 0xFFFFFF;
							spawn(level, MOTE, color, 0.16f, 16, pos, new Vec3(0, 0.03, 0));
						}
						if (age % 3 == 0) {
							spawn(level, SPARKLE, 0xE8FFD0, 0.15f, 14, entity.position().add(jitter(entity.getBbWidth() * 0.6)).add(0, RANDOM.nextDouble()
									* entity.getBbHeight(), 0), new Vec3(0, 0.04, 0));
						}
					}
				}
				case EMIT_PUSH_WAVE -> {
					// Eine Druckwelle, die sich vom Handballen aus nach vorne schiebt.
					double distance = 0.6 + age * 1.2;
					float size = 0.5f + age * 0.35f;
					ring(level, emitter.origin().add(emitter.direction().scale(distance)), emitter.direction(), age % 2 == 0 ? 0xFFFFFF : emitter.color(),
							size, 0.18f + age * 0.04f, 7);
				}
				case EMIT_PULL_WAVE -> {
					// Ringe ziehen sich aus der Ferne zur Hand zusammen.
					double distance = 6.0 - age * 1.3;
					if (distance > 0.4) {
						ring(level, emitter.origin().add(emitter.direction().scale(distance)), emitter.direction(), emitter.color(), 1.6f - age * 0.2f,
								-0.12f, 6);
					}
				}
				case EMIT_LEARN -> {
					Entity entity = level.getEntity(emitter.entity());
					if (entity != null) {
						// In der Ego-Perspektive bleibt die Spirale unter Augenhöhe und etwas weiter weg.
						boolean ownView = isOwnView(entity);
						float progress = age / (float) (emitter.until() - emitter.start());
						double top = entity.getBbHeight() * (ownView ? 0.6 : 1.2);
						double radius = entity.getBbWidth() * (ownView ? 1.6 : 1.0);
						for (int strand = 0; strand < 2; strand++) {
							double angle = age * 0.5 + strand * Math.PI;
							Vec3 pos = entity.position().add(Math.sin(angle) * radius, 0.1 + progress * top, Math.cos(angle) * radius);
							spawn(level, SPARKLE, strand == 0 ? emitter.color() : 0xFFE9A0, 0.13f, 14, pos, new Vec3(0, 0.015, 0));
						}
					}
				}
				case EMIT_TRAIL -> {
					Entity entity = level.getEntity(emitter.entity());
					if (entity != null) {
						spawn(level, SPARKLE, emitter.color(), 0.18f, 10, center(entity), Vec3.ZERO);
						spawn(level, MOTE, 0xFFFFFF, 0.12f, 8, center(entity).add(jitter(0.15)), Vec3.ZERO);
					}
				}
				default -> {
				}
			}
			return now >= emitter.until();
		});
	}

	// ------------------------------------------------------------------------------------------
	// Abfragen für Rendering
	// ------------------------------------------------------------------------------------------

	public static Int2ObjectMap<Channel> channels() {
		return CHANNELS;
	}

	public static @Nullable Channel channel(Entity caster) {
		return CHANNELS.get(caster.getId());
	}

	/** Stärke des Leuchtens um ein Wesen (0 = keins). */
	public static float auraStrength(int entityId, float partialTick) {
		Aura aura = AURAS.get(entityId);
		if (aura == null) return 0f;
		float strength = Mth.lerp(partialTick, aura.prevStrength, aura.strength);
		if (aura.flicker) {
			strength *= 0.55f + 0.45f * RANDOM.nextFloat();
		}
		return strength;
	}

	public static int auraColor(int entityId) {
		Aura aura = AURAS.get(entityId);
		return aura != null ? aura.color : ForceSide.LIGHT.color;
	}

	/** Flackernde Auren (Blitztreffer) bekommen keinen Umriss, nur die Energiehülle. */
	public static boolean auraOutline(int entityId) {
		Aura aura = AURAS.get(entityId);
		return aura != null && !aura.flicker;
	}

	public static @Nullable Gesture gesture(int entityId) {
		return GESTURES.get(entityId);
	}

	public static boolean isSpeeding(Entity entity) {
		return SPEED_UNTIL.getOrDefault(entity.getId(), -1) >= now();
	}

	public static void clear() {
		CHANNELS.clear();
		AURAS.clear();
		GESTURES.clear();
		SPEED_UNTIL.clear();
		EMITTERS.clear();
	}

	// ------------------------------------------------------------------------------------------
	// Helfer
	// ------------------------------------------------------------------------------------------

	private static void aura(int entityId, int color, float strength, int holdUntil, boolean flicker) {
		Aura aura = AURAS.computeIfAbsent(entityId, id -> new Aura());
		aura.color = color;
		aura.target = strength;
		aura.holdUntil = Math.max(aura.holdUntil, holdUntil);
		aura.flicker = flicker;
	}

	/** Sieht der eigene Spieler gerade durch die Augen dieses Wesens? */
	static boolean isOwnView(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
	}

	public static Vec3 center(Entity entity) {
		return entity.getBoundingBox().getCenter();
	}

	/** Etwa auf Halshöhe. */
	public static Vec3 throat(Entity entity) {
		return new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight() - 0.25, entity.getZ());
	}

	/**
	 * Ungefähre Lage der wirkenden Hand: die Zweithand, ausgestreckt vor dem Körper. In der
	 * Ego-Perspektive etwas unten links im Bild, wo die erste-Person-Hand erscheint.
	 */
	public static Vec3 handPos(Entity entity, float partialTick) {
		Vec3 eye = entity.getEyePosition(partialTick);
		SaberFrame frame = SaberFrame.of(eye, entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
		boolean leftHanded = entity instanceof Player player && player.getMainArm() == HumanoidArm.LEFT;
		float side = leftHanded ? 1f : -1f;
		return isOwnView(entity) ? frame.toWorld(side * 0.3f, -0.26f, 0.6f) : frame.toWorld(side * 0.34f, -0.28f, 0.72f);
	}

	static void spawn(ClientLevel level, int style, int color, float size, int lifetime, Vec3 pos, Vec3 velocity) {
		level.addParticle(ForceParticleOptions.of(style, color, size, lifetime), pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}

	/** Ein Druckwellenring mit Blickrichtung {@code normal}; wächst um {@code growth} Blöcke pro Tick (negativ: zieht sich zusammen). */
	static void ring(ClientLevel level, Vec3 pos, Vec3 normal, int color, float size, float growth, int lifetime) {
		Vec3 n = normal.normalize().scale(Math.max(0.001, Math.abs(growth)));
		ForceParticleOptions options = ForceParticleOptions.of(growth < 0 ? RING_IN : RING, color, size, lifetime);
		level.addParticle(options, pos.x, pos.y, pos.z, n.x, n.y, n.z);
	}

	static void burst(ClientLevel level, Vec3 pos, int color, int count, double speed) {
		for (int i = 0; i < count; i++) {
			spawn(level, SPARKLE, i % 3 == 0 ? 0xFFFFFF : color, 0.12f + RANDOM.nextFloat() * 0.12f, 10 + RANDOM.nextInt(10), pos, jitter(speed));
		}
	}

	static Vec3 jitter(double amount) {
		return new Vec3((RANDOM.nextDouble() - 0.5) * 2 * amount, (RANDOM.nextDouble() - 0.5) * 2 * amount, (RANDOM.nextDouble() - 0.5) * 2 * amount);
	}

	/** Zufällige Richtung in einem Kegel um {@code dir}. */
	static Vec3 spread(Vec3 dir, double amount) {
		return dir.add(jitter(amount)).normalize();
	}
}
