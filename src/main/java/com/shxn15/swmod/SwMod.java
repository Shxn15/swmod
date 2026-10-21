package com.shxn15.swmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import com.shxn15.swmod.combat.SaberCombatSystem;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceSystem;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModAttachments;
import com.shxn15.swmod.registry.ModDataComponents;
import com.shxn15.swmod.registry.ModEntityTypes;
import com.shxn15.swmod.registry.ModParticles;
import com.shxn15.swmod.registry.ModSounds;

public class SwMod implements ModInitializer {
	public static final String MOD_ID = "swmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModDataComponents.init();
		ModSounds.init();
		ModParticles.init();
		ModAttachments.init();
		ForceAbilities.init();
		ModItems.init();
		ModEntityTypes.init();
		ModCreativeTabs.init();
		ModNetworking.init();
		SaberCombatSystem.init();
		ForceSystem.init();

		LOGGER.info("Star Wars Mod geladen - möge die Macht mit dir sein!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
