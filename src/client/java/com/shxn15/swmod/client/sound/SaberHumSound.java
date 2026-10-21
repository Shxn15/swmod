package com.shxn15.swmod.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Das typische Brummen einer gezündeten Klinge. Folgt dem Träger, wird beim Schwingen lauter
 * und höher (der klassische "Doppler"-Effekt der Filme) und blendet beim Ausschalten aus.
 */
public class SaberHumSound extends AbstractTickableSoundInstance {
	private final Entity entity;
	private boolean fadingOut;

	public SaberHumSound(Entity entity) {
		super(entity instanceof ThrownLightsaberEntity ? ModSounds.LIGHTSABER_SPIN : ModSounds.LIGHTSABER_HUM,
				SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01f;
		this.pitch = 1f;
		this.updatePosition();
	}

	public Entity entity() {
		return this.entity;
	}

	@Override
	public void tick() {
		if (this.entity.isRemoved()) {
			this.stop();
			return;
		}

		float targetVolume;
		float targetPitch;

		if (this.entity instanceof ThrownLightsaberEntity) {
			targetVolume = 0.75f;
			targetPitch = 1.1f;
		} else if (this.entity instanceof LivingEntity living) {
			ItemStack stack = living.getMainHandItem();
			SaberAnimState anim = SaberAnimations.peek(living);
			if (anim == null || !Lightsabers.isLightsaber(stack)) {
				this.fadingOut = true;
				targetVolume = 0f;
				targetPitch = this.pitch;
			} else {
				float extension = anim.extension(1f);
				float speed = anim.bladeSpeed(SaberAnimations.time(0f));
				this.fadingOut = extension <= 0.02f;
				targetVolume = 0.32f * extension + Math.min(0.35f, speed / 110f);
				targetPitch = 0.94f + Math.min(0.32f, speed / 95f);
			}
		} else {
			this.stop();
			return;
		}

		this.volume = Mth.lerp(0.45f, this.volume, targetVolume);
		this.pitch = Mth.lerp(0.5f, this.pitch, targetPitch);
		this.updatePosition();

		if (this.fadingOut && this.volume < 0.01f) {
			this.stop();
		}
	}

	private void updatePosition() {
		this.x = this.entity.getX();
		this.y = this.entity instanceof LivingEntity ? this.entity.getEyeY() - 0.4 : this.entity.getY();
		this.z = this.entity.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
