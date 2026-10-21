package com.shxn15.swmod.force.ability;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceState;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtgeschwindigkeit: für einige Sekunden deutlich schneller laufen, höher springen und
 * (ab Stufe 2) Blöcke hochlaufen. Die Clients zeigen Geschwindigkeitslinien und Nachbilder.
 */
public class SpeedAbility extends ForceAbility {
	private static final Identifier SPEED_ID = SwMod.id("force_speed");
	private static final Identifier JUMP_ID = SwMod.id("force_speed_jump");
	private static final Identifier STEP_ID = SwMod.id("force_speed_step");
	private static final Identifier FALL_ID = SwMod.id("force_speed_fall");

	public SpeedAbility() {
		super(Activation.INSTANT, ForceSide.LIGHT, 0xFFE27A, 3);
	}

	/** Laufende Wirkung. */
	static final class Active {
		int until;
	}

	@Override
	public int learnCost(int level) {
		return at(SPEED_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(SPEED_COST, level);
	}

	@Override
	public int cooldown(int level) {
		return at(SPEED_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.seconds("duration", level -> at(SPEED_DURATION, level)),
				ForceStat.percent("speed", level -> at(SPEED_BONUS, level)),
				ForceStat.flag("step", level -> at(SPEED_STEP_BONUS, level) > 0),
				ForceStat.seconds("cooldown", level -> at(SPEED_COOLDOWN, level)));
	}

	@Override
	public boolean cast(ForceContext context) {
		ServerPlayer player = context.player();
		int lvl = context.level();
		int duration = at(SPEED_DURATION, lvl);

		apply(player, Attributes.MOVEMENT_SPEED, SPEED_ID, at(SPEED_BONUS, lvl), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		apply(player, Attributes.JUMP_STRENGTH, JUMP_ID, at(SPEED_JUMP_BONUS, lvl), AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.STEP_HEIGHT, STEP_ID, at(SPEED_STEP_BONUS, lvl), AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, at(SPEED_SAFE_FALL, lvl), AttributeModifier.Operation.ADD_VALUE);

		Active active = context.state().data(this, Active::new);
		active.until = context.now() + duration;

		context.fx(ForceFxPayload.CAST, List.of(player.getId()), List.of(duration));
		context.playSound(ModSounds.FORCE_SPEED, 1.0f, 1.0f);
		return true;
	}

	@Override
	public void tickPlayer(ServerPlayer player, ForceState state, int level, int now) {
		Active active = state.data(this);
		if (active != null && now >= active.until) {
			this.reset(player, state);
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.FORCE_SPEED_END, SoundSource.PLAYERS, 0.7f, 1.0f);
			ModNetworking.broadcastForceFx(player, new ForceFxPayload(player.getId(), this.id(), ForceFxPayload.EFFECT_END, level, List.of(player.getId()), List.of()));
		}
	}

	@Override
	public void reset(ServerPlayer player, ForceState state) {
		state.clearData(this);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		remove(player, Attributes.STEP_HEIGHT, STEP_ID);
		remove(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID);
	}

	private static void apply(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
