package com.shxn15.swmod.client.force.fx;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.force.ForceAbilities;

/** Überträgt Machteffekte beim Extrahieren der Render-States (Mixins rufen hier hinein). */
public final class ForceRenderer {
	private ForceRenderer() {
	}

	/**
	 * Gegriffene, gewürgte und geheilte Wesen bekommen einen farbigen Umriss, der auch durch Wände
	 * leuchtet – so wie Objekte in LEGO Star Wars, die man gerade mit der Macht bewegt.
	 */
	public static void extractOutline(Entity entity, EntityRenderState state, float partialTick) {
		if (ForceFx.auraOutline(entity.getId()) && ForceFx.auraStrength(entity.getId(), partialTick) > 0.35f) {
			state.outlineColor = ARGB.opaque(ForceFx.auraColor(entity.getId()));
		}
	}

	public static void extractLiving(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
		ForceRenderData data = ((ForceRenderData.Access) state).swmod$forceData();
		data.aura = ForceFx.auraStrength(entity.getId(), partialTick);
		data.auraColor = ForceFx.auraColor(entity.getId());
		data.time = ForceClientState.ticks() + partialTick;

		ForceFx.Gesture gesture = ForceFx.gesture(entity.getId());
		data.gesture = gesture;
		data.gestureTime = gesture != null ? ForceClientState.ticks() - gesture.start() + partialTick : 0f;
		if (gesture != null && gesture.ability() == ForceAbilities.SPEED) {
			data.gesture = null;
		}
	}
}
