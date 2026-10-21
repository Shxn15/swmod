package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import com.shxn15.swmod.client.force.fx.ForceGestures;
import com.shxn15.swmod.client.force.fx.ForceRenderData;
import com.shxn15.swmod.client.render.SaberPoseSampler;
import com.shxn15.swmod.client.render.SaberRenderData;
import com.shxn15.swmod.client.render.ThirdPersonSaber;

/** Beide Hände greifen den Lichtschwertgriff, egal wo die Animation ihn gerade hinführt. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow
	@Final
	public ModelPart rightArm;

	@Shadow
	@Final
	public ModelPart leftArm;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void swmod$holdSaber(HumanoidRenderState state, CallbackInfo ci) {
		SaberRenderData data = ((SaberRenderData.Access) state).swmod$saberData();
		SaberPoseSampler.Sample sample = data.sample;
		if (data.active && sample != null) {
			ThirdPersonSaber.pointArmAt(this.rightArm, sample.grip());
			ThirdPersonSaber.pointArmAt(this.leftArm, sample.grip());
		}

		// Wirk-Gesten der Macht: die Zweithand löst sich dafür vom Lichtschwert.
		ForceGestures.apply((HumanoidModel<?>) (Object) this, state, ((ForceRenderData.Access) state).swmod$forceData(), data.active);
	}
}
