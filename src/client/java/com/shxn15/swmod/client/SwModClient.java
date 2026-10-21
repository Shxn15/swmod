package com.shxn15.swmod.client;

import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.client.hud.SaberHud;
import com.shxn15.swmod.client.input.SaberInput;
import com.shxn15.swmod.client.item.BladeColorTintSource;
import com.shxn15.swmod.client.item.LightsaberIgnitedProperty;
import com.shxn15.swmod.client.item.LightsaberTooltip;
import com.shxn15.swmod.client.render.SaberLayer;
import com.shxn15.swmod.client.render.SaberRenderTypes;
import com.shxn15.swmod.client.render.ThrownLightsaberRenderer;
import com.shxn15.swmod.client.sound.SaberSounds;
import com.shxn15.swmod.lightsaber.LightsaberItem;
import com.shxn15.swmod.network.SaberAnimPayload;
import com.shxn15.swmod.network.SaberStatusPayload;
import com.shxn15.swmod.registry.ModEntityTypes;

public class SwModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SaberInput.init();
		SaberRenderTypes.init();

		// Eingaben vor der Vanilla-Tastenverarbeitung, damit F/X mit Lichtschwert uns gehören.
		ClientTickEvents.START_CLIENT_TICK.register(SaberInput::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SaberAnimations.tick(client);
			SaberSounds.tick(client);
		});

		// Mit gezündetem Lichtschwert ersetzt unser Kampfsystem den normalen Angriff und das Abbauen.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> SaberInput.isSaberReady(client));
		LightsaberItem.clientCanBlock = player -> SaberInput.canBlock();

		ClientPlayNetworking.registerGlobalReceiver(SaberAnimPayload.TYPE, (payload, context) -> SaberAnimations.handle(payload));
		ClientPlayNetworking.registerGlobalReceiver(SaberStatusPayload.TYPE, (payload, context) -> SaberInput.handleStatus(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SaberAnimations.clear();
			SaberSounds.clear();
		});

		EntityRenderers.register(ModEntityTypes.THROWN_LIGHTSABER, ThrownLightsaberRenderer::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				registrationHelper.register(new SaberLayer<>(avatarRenderer));
			}
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, SwMod.id("lightsaber_hud"), SaberHud::render);
		ItemTooltipCallback.EVENT.register(LightsaberTooltip::append);
		ItemTintSources.ID_MAPPER.put(SwMod.id("blade_color"), BladeColorTintSource.MAP_CODEC);
		ConditionalItemModelProperties.ID_MAPPER.put(SwMod.id("lightsaber_ignited"), LightsaberIgnitedProperty.MAP_CODEC);
	}
}
