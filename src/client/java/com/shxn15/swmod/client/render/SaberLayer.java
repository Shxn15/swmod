package com.shxn15.swmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Zeichnet das Lichtschwert eines Spielers in der Third-Person-Ansicht. */
public class SaberLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	public SaberLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		SaberRenderData data = ((SaberRenderData.Access) state).swmod$saberData();
		SaberPoseSampler.Sample sample = data.sample;
		if (!data.active || sample == null) {
			return;
		}

		SaberRenderer.submitHilt(poseStack, collector, data.hilt, sample.grip(), sample.direction(), 0f, light);

		if (data.extension > 0.001f) {
			SaberRenderer.submitTrail(poseStack, collector, sample.trailBases(), sample.trailTips(), sample.trailAlphas(), data.color);
			SaberRenderer.submitBlade(poseStack, collector, sample.emitter(), sample.tip(), data.color, 1f, data.time);
		}
	}
}
