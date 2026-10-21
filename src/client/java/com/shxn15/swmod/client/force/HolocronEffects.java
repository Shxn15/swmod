package com.shxn15.swmod.client.force;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.shxn15.swmod.force.ForceAbility;

/** Feiert das Lernen einer Fähigkeit: im Holocron mit Feuerwerk, sonst mit einer Meldung. */
final class HolocronEffects {
	private HolocronEffects() {
	}

	static void onLearned(ForceAbility ability, int level) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof HolocronScreen screen) {
			screen.celebrate(ability, level);
			return;
		}
		Component text = level <= 1
				? Component.translatable("screen.swmod.holocron.learned", ability.name())
				: Component.translatable("screen.swmod.holocron.upgraded", ability.name(), level);
		minecraft.gui.hud.setOverlayMessage(text.copy().withStyle(ChatFormatting.AQUA), true);
	}
}
