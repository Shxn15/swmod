package com.shxn15.swmod.client.force.fx;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import com.shxn15.swmod.SwMod;

/**
 * Leuchtende Energiehülle um Wesen, die gerade von der Macht gehalten werden: das Modell wird ein
 * zweites Mal, etwas größer und mit wandernder Energie-Textur additiv darübergelegt (wie bei einem
 * aufgeladenen Creeper) – zusammen mit dem Umriss ergibt das den LEGO-Star-Wars-Glanz.
 */
public class ForceAuraLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	private static final Identifier TEXTURE = SwMod.id("textures/entity/force_aura.png");

	public ForceAuraLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		ForceRenderData data = ((ForceRenderData.Access) state).swmod$forceData();
		if (data.aura <= 0.02f) {
			return;
		}

		float t = data.time;
		float pulse = 0.8f + 0.2f * Mth.sin(t * 0.35f);
		float strength = Mth.clamp(data.aura * pulse, 0f, 1.2f);
		int color = ARGB.scaleRGB(ARGB.opaque(data.auraColor), Math.min(1f, strength));

		// Etwas aufgebläht um die Körpermitte, damit die Hülle über dem Modell liegt.
		float center = 1.501f - state.boundingBoxHeight / Math.max(0.01f, state.scale) * 0.5f;
		float inflate = 1.06f + 0.02f * Mth.sin(t * 0.5f);
		poseStack.pushPose();
		poseStack.translate(0f, center, 0f);
		poseStack.scale(inflate, inflate, inflate);
		poseStack.translate(0f, -center, 0f);
		collector.order(1).submitModel(this.getParentModel(), state, poseStack,
				RenderTypes.energySwirl(TEXTURE, t * 0.012f % 1f, t * 0.02f % 1f),
				light, OverlayTexture.NO_OVERLAY, color, null, 0);
		poseStack.popPose();
	}
}
