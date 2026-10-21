package com.shxn15.swmod.client.force.fx;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;

import com.shxn15.swmod.force.ForceParticleOptions;

/**
 * Ein leuchtendes Machtpartikel. Die Form ({@link ForceParticleOptions#style()}) bestimmt Textur,
 * Bewegung und Animation; alle außer Rauch leuchten additiv in voller Helligkeit.
 */
public class ForceParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final int style;
	private final float baseSize;
	private final float phase;
	private final float spin;
	private @Nullable Vector3f normal;
	private float growth;
	private float prevSize;
	private float size;

	protected ForceParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
							ForceParticleOptions options, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.style = options.style();
		this.baseSize = options.size();
		this.size = this.prevSize = this.style == ForceParticleOptions.RING ? options.size() * 0.3f : options.size();
		this.lifetime = options.lifetime();
		this.phase = this.random.nextFloat() * Mth.TWO_PI;
		this.spin = (this.random.nextFloat() - 0.5f) * 0.3f;
		int color = options.color();
		this.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
		this.hasPhysics = false;
		this.gravity = 0f;
		this.friction = 0.92f;

		switch (this.style) {
			case ForceParticleOptions.RING, ForceParticleOptions.RING_IN -> {
				double length = Math.sqrt(xd * xd + yd * yd + zd * zd);
				this.normal = length > 1.0E-5 ? new Vector3f((float) (xd / length), (float) (yd / length), (float) (zd / length)) : new Vector3f(0, 1, 0);
				this.growth = (float) length;
				this.setParticleSpeed(0, 0, 0);
			}
			case ForceParticleOptions.SPARK -> {
				this.setParticleSpeed(xd, yd, zd);
				this.hasPhysics = true;
				this.gravity = 0.9f;
				this.friction = 0.94f;
			}
			case ForceParticleOptions.MOTE -> {
				this.setParticleSpeed(xd, yd, zd);
				this.friction = 0.96f;
			}
			case ForceParticleOptions.SMOKE -> {
				this.setParticleSpeed(xd, yd, zd);
				this.friction = 0.9f;
				this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
			}
			case ForceParticleOptions.STREAK -> {
				this.setParticleSpeed(xd, yd, zd);
				this.friction = 0.88f;
			}
			default -> {
				this.setParticleSpeed(xd, yd, zd);
				this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
			}
		}
		this.updateAlpha();
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;
		this.prevSize = this.size;
		super.tick();
		switch (this.style) {
			case ForceParticleOptions.RING -> this.size += this.growth;
			case ForceParticleOptions.RING_IN -> this.size = Math.max(0.05f, this.size - this.growth);
			case ForceParticleOptions.SPARKLE -> this.roll += this.spin;
			case ForceParticleOptions.SMOKE -> {
				this.roll += this.spin * 0.3f;
				this.size += this.baseSize * 0.03f;
			}
			default -> {
			}
		}
		this.updateAlpha();
	}

	private void updateAlpha() {
		float t = this.lifetime > 0 ? this.age / (float) this.lifetime : 1f;
		float alpha = switch (this.style) {
			case ForceParticleOptions.RING, ForceParticleOptions.FLASH -> (1f - t) * (1f - t);
			case ForceParticleOptions.RING_IN -> Math.min(1f, t * 3f) * (1f - t * t);
			case ForceParticleOptions.MOTE -> Math.min(1f, t * 5f) * (1f - t);
			case ForceParticleOptions.SMOKE -> 0.55f * Math.min(1f, t * 4f) * (1f - t);
			case ForceParticleOptions.SPARKLE -> (1f - t) * (0.65f + 0.35f * Mth.sin(this.age * 0.9f + this.phase));
			default -> 1f - t * t;
		};
		this.setAlpha(Mth.clamp(alpha, 0f, 1f));
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Mth.clamp((this.age + partialTick) / Math.max(1, this.lifetime), 0f, 1f);
		float s = Mth.lerp(partialTick, this.prevSize, this.size);
		return switch (this.style) {
			case ForceParticleOptions.SPARKLE -> s * (0.55f + 0.45f * Mth.sin((this.age + partialTick) * 0.8f + this.phase)) * (1f - t * 0.6f);
			case ForceParticleOptions.SPARK -> s * (1f - t);
			case ForceParticleOptions.FLASH -> s * (0.6f + 0.4f * t);
			default -> s;
		};
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		if (ring(this.style) && this.normal != null) {
			// Druckwellen stehen quer zur Stoßrichtung im Raum, statt zur Kamera zu zeigen.
			Quaternionf rotation = new Quaternionf().rotationTo(0f, 0f, 1f, this.normal.x, this.normal.y, this.normal.z);
			this.extractRotatedQuad(state, camera, rotation, partialTick);
			return;
		}
		if (this.style == ForceParticleOptions.STREAK) {
			// Lichtstreifen zeigen in Flugrichtung.
			Quaternionf rotation = new Quaternionf(camera.rotation());
			Vector3f velocity = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
			new Quaternionf(rotation).conjugate().transform(velocity);
			rotation.rotateZ((float) Math.atan2(velocity.y, velocity.x));
			this.extractRotatedQuad(state, camera, rotation, partialTick);
			return;
		}
		super.extract(state, camera, partialTick);
	}

	private static boolean ring(int style) {
		return style == ForceParticleOptions.RING || style == ForceParticleOptions.RING_IN;
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return this.style == ForceParticleOptions.SMOKE ? SingleQuadParticle.Layer.TRANSLUCENT : ForceRenderTypes.GLOW_LAYER;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return this.style == ForceParticleOptions.SMOKE ? super.getLightCoords(partialTick) : FULL_BRIGHT;
	}

	/** Wählt die Textur passend zur Form aus der Liste in {@code particles/force.json}. */
	public static class Provider implements ParticleProvider<ForceParticleOptions> {
		private final FabricSpriteSet sprites;

		public Provider(FabricSpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(ForceParticleOptions options, ClientLevel level, double x, double y, double z,
									   double xd, double yd, double zd, RandomSource random) {
			List<TextureAtlasSprite> list = this.sprites.getSprites();
			TextureAtlasSprite sprite = list.get(Math.min(options.style(), list.size() - 1));
			return new ForceParticle(level, x, y, z, xd, yd, zd, options, sprite);
		}
	}
}
