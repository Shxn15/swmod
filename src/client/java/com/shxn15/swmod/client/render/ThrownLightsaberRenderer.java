package com.shxn15.swmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.combat.SaberGeometry;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;

/** Ein geworfenes Lichtschwert, das sich wie eine leuchtende Scheibe durch die Luft dreht. */
public class ThrownLightsaberRenderer extends EntityRenderer<ThrownLightsaberEntity, ThrownLightsaberRenderer.State> {
	/** Drehgeschwindigkeit in Grad pro Tick. */
	private static final float SPIN_SPEED = 56f;
	/** Abstand vom Griffpunkt zum Drehzentrum entlang der Klinge. */
	private static final float SPIN_CENTER = 0.42f;

	public ThrownLightsaberRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownLightsaberEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		HiltModels.update(state.hilt, entity.getStack(), null);
		state.color = Lightsabers.color(entity.getStack());
		state.spin = (entity.getAge() + partialTick) * SPIN_SPEED;
		state.time = SaberAnimations.time(partialTick);

		Vec3 motion = entity.getDeltaMovement();
		state.heading = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.25f, 0f);
		// Leicht gekippte Drehebene sieht dynamischer aus als eine perfekt flache Scheibe.
		poseStack.rotateDegrees(Axis.YP, state.heading);
		poseStack.rotateDegrees(Axis.ZP, 12f);

		// Bewegungsunschärfe: frühere Drehwinkel schwächer nachzeichnen.
		float[] ghostOffsets = {-14f, -28f, -42f};
		float[] ghostIntensity = {0.45f, 0.25f, 0.12f};
		for (int i = 0; i < ghostOffsets.length; i++) {
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.YP, state.spin + ghostOffsets[i]);
			this.submitSaber(state, poseStack, collector, ghostIntensity[i], false);
			poseStack.popPose();
		}

		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.spin);
		this.submitSaber(state, poseStack, collector, 1f, true);
		poseStack.popPose();

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private void submitSaber(State state, PoseStack poseStack, SubmitNodeCollector collector, float intensity, boolean withHilt) {
		Vector3f direction = new Vector3f(1f, 0f, 0f);
		Vector3f grip = new Vector3f(direction).mul(-SPIN_CENTER);
		Vector3f emitter = new Vector3f(direction).mul(SaberGeometry.GRIP_TO_EMITTER).add(grip);
		Vector3f tip = new Vector3f(direction).mul(SaberGeometry.GRIP_TO_EMITTER + SaberGeometry.BLADE_LENGTH).add(grip);

		if (withHilt) {
			SaberRenderer.submitHilt(poseStack, collector, state.hilt, grip, direction, 0f, state.lightCoords);
		}
		SaberRenderer.submitBlade(poseStack, collector, emitter, tip, state.color, intensity, state.time);
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState hilt = new ItemStackRenderState();
		public int color;
		public float spin;
		public float heading;
		public float time;
	}
}
