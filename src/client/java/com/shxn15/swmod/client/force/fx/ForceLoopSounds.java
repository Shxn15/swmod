package com.shxn15.swmod.client.force.fx;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.registry.ModSounds;

/** Dauergeräusche gehaltener Fähigkeiten: Knistern der Blitze, Dröhnen des Würgens, Summen des Griffs. */
public final class ForceLoopSounds {
	private ForceLoopSounds() {
	}

	static void start(@Nullable Entity caster, ForceAbility ability) {
		SoundEvent sound = ability == ForceAbilities.LIGHTNING ? ModSounds.FORCE_LIGHTNING_LOOP
				: ability == ForceAbilities.GRIP ? ModSounds.FORCE_GRIP_LOOP
				: ability == ForceAbilities.GRAB ? ModSounds.FORCE_GRAB_LOOP : null;
		if (caster != null && sound != null) {
			Minecraft.getInstance().getSoundManager().play(new Loop(caster, ability, sound));
		}
	}

	/** Folgt dem Wirkenden und blendet aus, sobald der Kanal endet. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final Entity caster;
		private final ForceAbility ability;
		private boolean ending;

		Loop(Entity caster, ForceAbility ability, SoundEvent sound) {
			super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.caster = caster;
			this.ability = ability;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.05f;
			this.pitch = 1f;
			this.x = caster.getX();
			this.y = caster.getEyeY();
			this.z = caster.getZ();
		}

		@Override
		public void tick() {
			if (this.caster.isRemoved()) {
				this.stop();
				return;
			}
			ForceFx.Channel channel = ForceFx.channel(this.caster);
			this.ending = channel == null || channel.ability != this.ability;
			float target = this.ending ? 0f : 0.85f;
			this.volume = Mth.lerp(this.ending ? 0.35f : 0.5f, this.volume, target);
			if (this.ability == ForceAbilities.LIGHTNING) {
				this.pitch = 0.92f + this.random.nextFloat() * 0.16f;
			}
			this.x = this.caster.getX();
			this.y = this.caster.getEyeY();
			this.z = this.caster.getZ();
			if (this.ending && this.volume < 0.02f) {
				this.stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
