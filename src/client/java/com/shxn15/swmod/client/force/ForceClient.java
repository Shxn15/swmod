package com.shxn15.swmod.client.force;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;

import com.shxn15.swmod.ModItems;
import com.shxn15.swmod.client.force.fx.ForceFx;
import com.shxn15.swmod.client.force.fx.ForceParticle;
import com.shxn15.swmod.client.force.fx.ForceRenderTypes;
import com.shxn15.swmod.client.force.fx.ForceScreenEffects;
import com.shxn15.swmod.client.force.fx.LightningRenderer;
import com.shxn15.swmod.client.gui.GuiShapes;
import com.shxn15.swmod.force.HolocronItem;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.network.ForceStatusPayload;
import com.shxn15.swmod.registry.ModParticles;

/** Client-Seite der Machtfähigkeiten: Eingabe, HUD, Holocron, Effekte. */
public final class ForceClient {
	private ForceClient() {
	}

	public static void init() {
		ForceInput.init();
		GuiShapes.init();
		ForceRenderTypes.init();

		ClientPlayNetworking.registerGlobalReceiver(ForceStatusPayload.TYPE, (payload, context) -> ForceClientState.handleStatus(payload));
		ClientPlayNetworking.registerGlobalReceiver(ForceEventPayload.TYPE, (payload, context) -> ForceClientState.handleEvent(payload));
		ClientPlayNetworking.registerGlobalReceiver(ForceFxPayload.TYPE, (payload, context) -> ForceFx.handle(payload));

		ParticleProviderRegistry.getInstance().register(ModParticles.FORCE, ForceParticle.Provider::new);
		ClientHotbarScrollEvents.ALLOW.register(ForceInput::allowHotbarScroll);
		HolocronItem.clientOpen = player -> Minecraft.getInstance().gui.setScreen(new HolocronScreen());
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(ModItems.HOLOCRON)) {
				lines.add(Component.translatable("tooltip.swmod.holocron").withStyle(ChatFormatting.AQUA));
			}
		});

		ForceScreenEffects.init();
		ForceHud.init();
		ForceWheelHud.init();
		LightningRenderer.init();
	}

	/** Am Ende jedes Client-Ticks. */
	public static void tick(Minecraft minecraft) {
		ForceClientState.tick();
		ForceFx.tick(minecraft);
		ForceScreenEffects.tick(minecraft);
	}

	public static void clear() {
		ForceClientState.clear();
		ForceInput.reset();
		ForceFx.clear();
		ForceScreenEffects.clear();
	}
}
