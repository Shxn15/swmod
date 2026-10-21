package com.shxn15.swmod;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import com.shxn15.swmod.force.HolocronItem;
import com.shxn15.swmod.lightsaber.LightsaberData;
import com.shxn15.swmod.lightsaber.LightsaberItem;
import com.shxn15.swmod.registry.ModDataComponents;

public final class ModItems {
	public static final Item RUBY = register("ruby", Item::new, new Item.Properties());

	public static final Item LIGHTSABER = register("lightsaber", LightsaberItem::new, new Item.Properties()
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.component(ModDataComponents.LIGHTSABER, LightsaberData.DEFAULT));

	public static final Item HOLOCRON = register("holocron", HolocronItem::new, new Item.Properties()
			.stacksTo(1)
			.rarity(Rarity.EPIC));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SwMod.id(name));
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
