package com.shxn15.swmod.client.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.shxn15.swmod.client.input.SaberInput;
import com.shxn15.swmod.lightsaber.BladeColor;
import com.shxn15.swmod.lightsaber.LightsaberData;
import com.shxn15.swmod.lightsaber.Lightsabers;

/** Tooltip mit Klingenfarbe, Zustand und einer kurzen Steuerungsübersicht. */
public final class LightsaberTooltip {
	private LightsaberTooltip() {
	}

	public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (!Lightsabers.isLightsaber(stack)) {
			return;
		}

		LightsaberData data = Lightsabers.data(stack);
		BladeColor preset = BladeColor.byRgb(data.color());
		Component colorName = preset != null
				? Component.translatable(preset.translationKey())
				: Component.literal(String.format("#%06X", data.color()));

		lines.add(Component.translatable("tooltip.swmod.lightsaber.color", colorName.copy().withColor(data.color()))
				.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(data.ignited() ? "tooltip.swmod.lightsaber.on" : "tooltip.swmod.lightsaber.off")
				.withStyle(data.ignited() ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));

		lines.add(Component.empty());
		lines.add(Component.translatable("tooltip.swmod.lightsaber.controls.attack").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("tooltip.swmod.lightsaber.controls.block").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("tooltip.swmod.lightsaber.controls.keys",
				SaberInput.TOGGLE.getTranslatedKeyMessage(), SaberInput.THROW.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
