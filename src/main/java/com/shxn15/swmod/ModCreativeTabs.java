package com.shxn15.swmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import com.shxn15.swmod.lightsaber.BladeColor;
import com.shxn15.swmod.lightsaber.Lightsabers;

public final class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> SWMOD_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SwMod.id("swmod"));

	private ModCreativeTabs() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SWMOD_TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.swmod"))
				.icon(() -> Lightsabers.create(BladeColor.BLUE))
				.displayItems((context, output) -> {
					for (BladeColor color : BladeColor.values()) {
						output.accept(Lightsabers.create(color));
					}
					output.accept(new ItemStack(ModItems.HOLOCRON));
					output.accept(new ItemStack(ModItems.RUBY));
				})
				.build());
	}
}
