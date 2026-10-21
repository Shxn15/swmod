package com.shxn15.swmod.test;

import java.util.ArrayList;
import java.util.List;


import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.shxn15.swmod.client.input.SaberInput;
import com.shxn15.swmod.lightsaber.BladeColor;
import com.shxn15.swmod.lightsaber.Lightsabers;

/**
 * Startet ein echtes Minecraft und spielt das Lichtschwert einmal komplett durch:
 * zünden, schlagen (per Mausgeste), blocken, Third-Person, werfen, ausschalten.
 * Screenshots landen in build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class LightsaberClientGameTest implements FabricClientGameTest {
	private static final int LEFT_MOUSE = 0;
	private static final int RIGHT_MOUSE = 1;

	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			TestInput input = context.getInput();

			server.runCommand("time set midnight");
			server.runCommand("gamerule advance_time false");
			server.runCommand("weather clear");
			input.lookAt(0f, 0f);
			context.waitTicks(5);

			server.runCommand("execute as @p at @p run summon minecraft:zombie ^ ^ ^2.3 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, Lightsabers.create(BladeColor.BLUE));
			});
			context.waitTicks(10);
			context.takeScreenshot("01_hilt_fp");

			// --- Zünden ---
			input.pressKey(SaberInput.TOGGLE);
			context.waitTicks(12);
			this.check(context.computeOnClient(mc -> Lightsabers.isIgnited(mc.player.getMainHandItem())), "Schwert sollte nach X gezündet sein");
			context.takeScreenshot("02_ignited_fp");

			// --- Schlag per Mausgeste (links -> rechts) ---
			input.holdMouse(LEFT_MOUSE);
			context.waitTick();
			input.moveCursor(220, 0);
			context.waitTicks(5);
			context.takeScreenshot("03_swing_fp");
			context.waitTicks(12);
			input.releaseMouse(LEFT_MOUSE);
			context.waitTicks(5);

			float health = server.computeOnServer(s -> zombieHealth(s.overworld().getAllEntities()));
			this.check(Math.abs(health - 10f) < 0.01f, "Zombie sollte nach einem Treffer 10 Leben haben, hat aber " + health);

			// --- Blocken ---
			input.holdMouse(RIGHT_MOUSE);
			context.waitTicks(8);
			this.check(context.computeOnClient(mc -> mc.player.isUsingItem()), "Rechtsklick sollte die Deckung heben");
			context.takeScreenshot("04_block_fp");
			input.releaseMouse(RIGHT_MOUSE);
			context.waitTicks(8);

			// --- Third-Person von vorne ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("05_tp_front_idle");

			input.holdMouse(LEFT_MOUSE);
			context.waitTick();
			input.moveCursor(0, 220); // Maus nach unten = Überkopfschlag
			context.waitTicks(5);
			context.takeScreenshot("06_tp_front_swing");
			context.waitTicks(12);
			input.releaseMouse(LEFT_MOUSE);
			context.waitTicks(8);

			input.holdMouse(RIGHT_MOUSE);
			context.waitTicks(8);
			context.takeScreenshot("07_tp_front_block");
			input.releaseMouse(RIGHT_MOUSE);
			context.waitTicks(8);

			// --- Third-Person von hinten + Wurf ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			input.lookAt(90f, 0f);
			context.waitTicks(5);
			context.takeScreenshot("08_tp_back_idle");
			input.pressKey(SaberInput.THROW);
			context.waitTicks(5);
			this.check(context.computeOnClient(mc -> Lightsabers.isThrown(mc.player.getMainHandItem(), mc.level)), "Schwert sollte nach F geworfen sein");
			context.takeScreenshot("09_throw");
			context.waitTicks(50);
			this.check(context.computeOnClient(mc -> !Lightsabers.isThrown(mc.player.getMainHandItem(), mc.level)), "Schwert sollte zurückgekehrt sein");
			context.takeScreenshot("10_caught");

			// --- Ausschalten & Inventar ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			input.pressKey(SaberInput.TOGGLE);
			context.waitTicks(12);
			this.check(context.computeOnClient(mc -> !Lightsabers.isIgnited(mc.player.getMainHandItem())), "Schwert sollte nach erneutem X aus sein");
			context.takeScreenshot("11_retracted_fp");

			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(3);
			context.takeScreenshot("12_inventory");
			context.setScreen(() -> null);
		}

		if (!this.failures.isEmpty()) {
			throw new AssertionError("Lichtschwert-Test fehlgeschlagen:\n - " + String.join("\n - ", this.failures));
		}
	}

	private static float zombieHealth(Iterable<net.minecraft.world.entity.Entity> entities) {
		for (net.minecraft.world.entity.Entity entity : entities) {
			if (entity instanceof Zombie zombie) {
				return zombie.getHealth();
			}
		}
		return -1f;
	}

	private void check(boolean condition, String message) {
		if (!condition) {
			this.failures.add(message);
		}
	}
}
