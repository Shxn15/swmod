package com.shxn15.swmod.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.client.input.SaberInput;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.lightsaber.BladeColor;
import com.shxn15.swmod.lightsaber.LightsaberData;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;
import com.shxn15.swmod.registry.ModDataComponents;

/**
 * Startet ein echtes Minecraft und spielt das Lichtschwert einmal komplett durch:
 * zünden, schlagen (per Mausgeste), blocken, Third-Person, werfen, ausschalten.
 * Screenshots landen in build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class LightsaberClientGameTest implements FabricClientGameTest {
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
			// Minecraft verwirft die allererste Mausbewegung einer Sitzung absichtlich.
			input.moveCursor(1, 0);
			context.waitTicks(2);
			input.lookAt(0f, 0f);
			context.waitTicks(5);

			server.runCommand("execute as @p at @p run summon minecraft:zombie ^ ^ ^2.3 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.setItemInHand(InteractionHand.MAIN_HAND, Lightsabers.create(BladeColor.BLUE)));
			context.waitTicks(10);
			context.takeScreenshot("01_hilt_fp");

			// --- Zünden ---
			input.pressKey(SaberInput.TOGGLE);
			context.waitTicks(12);
			this.check(context.computeOnClient(mc -> Lightsabers.isIgnited(mc.player.getMainHandItem())), "Schwert sollte nach X gezündet sein");
			context.takeScreenshot("02_ignited_fp");

			// --- Schlag per Mausgeste (links -> rechts) ---
			// Die Bewegung verteilt sich wie bei einer echten Maus über mehrere Frames: das ist
			// trotzdem nur EIN Mauszug und darf nur einen Schlag auslösen (früher kamen zwei).
			input.holdKey(options -> options.keyAttack);
			context.waitTick();
			float yawBefore = context.computeOnClient(mc -> mc.player.getYRot());
			for (int step = 0; step < 4; step++) {
				input.moveCursor(40, 0);
				context.waitTick();
			}
			context.waitTicks(2);
			String mouseInfo = context.computeOnClient(mc -> "grabbed=" + mc.mouseHandler.isMouseGrabbed()
					+ " windowActive=" + mc.isWindowActive() + " yaw " + yawBefore + " -> " + mc.player.getYRot()
					+ " letzteGeste=" + SaberInput.lastDirection() + " vor " + SaberInput.ticksSinceLastDirection() + " Ticks");
			SwMod.LOGGER.info("[Test] Maus: {}", mouseInfo);
			this.check(context.computeOnClient(mc -> SaberInput.ticksSinceLastDirection() <= 10 && SaberInput.lastDirection() == SwingDirection.RIGHT),
					"Mausgeste nach rechts sollte einen Schlag nach rechts auslösen (" + mouseInfo + ")");
			context.takeScreenshot("03_swing_fp");
			context.waitTicks(12);
			input.releaseKey(options -> options.keyAttack);
			context.waitTicks(5);

			context.waitTicks(15);
			float health = server.computeOnServer(s -> zombieHealth(s.overworld().getAllEntities()));
			// 10 Schaden minus Zombie-Grundrüstung (2 Punkte) = 9,84. Ein zweiter (doppelter) Schlag hätte ihn fast getötet.
			this.check(health > 9.5f && health < 10.5f, "Zombie sollte nach genau einem Treffer ca. 10 Leben haben, hat aber " + health);

			// --- Blocken ---
			input.holdKey(options -> options.keyUse);
			context.waitTicks(8);
			this.check(context.computeOnClient(mc -> mc.player.isUsingItem()), "Rechtsklick sollte die Deckung heben");
			context.takeScreenshot("04_block_fp");
			input.releaseKey(options -> options.keyUse);
			context.waitTicks(8);

			// --- Stoß per kurzem Klick ---
			input.pressKey(options -> options.keyAttack);
			context.waitTicks(5);
			context.takeScreenshot("05_stab_fp");
			context.waitTicks(15);
			float health2 = server.computeOnServer(s -> zombieHealth(s.overworld().getAllEntities()));
			this.check(health2 < health || health2 < 0, "Stoß sollte den Zombie treffen (Leben vorher " + health + ", nachher " + health2 + ")");

			// --- Third-Person: Mannequin mit rotem Schwert von der Seite ---
			server.runCommand("kill @e[type=minecraft:zombie]");
			server.runCommand("execute as @p at @p run summon minecraft:mannequin ^ ^ ^3.5 {Rotation:[90f,0f],NoGravity:1b}");
			context.waitTicks(3);
			server.runOnServer(s -> {
				for (Entity entity : s.overworld().getAllEntities()) {
					if (entity instanceof Mannequin mannequin) {
						ItemStack saber = Lightsabers.create(BladeColor.RED);
						saber.set(ModDataComponents.LIGHTSABER, Lightsabers.data(saber).withIgnited(true));
						mannequin.setItemInHand(InteractionHand.MAIN_HAND, saber);
					}
				}
			});
			context.waitTicks(15);
			context.takeScreenshot("06_side_idle");
			this.swingMannequin(context, SwingDirection.RIGHT, "07_side_swing_right");
			this.swingMannequin(context, SwingDirection.DOWN, "08_side_swing_overhead");
			this.swingMannequin(context, SwingDirection.UP_LEFT, "09_side_swing_diagonal");
			server.runCommand("kill @e[type=minecraft:mannequin]");

			// --- Third-Person des Spielers ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("10_tp_front_idle");
			input.holdKey(options -> options.keyUse);
			context.waitTicks(8);
			context.takeScreenshot("11_tp_front_block");
			input.releaseKey(options -> options.keyUse);
			context.waitTicks(8);

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			input.lookAt(0f, 0f);
			context.waitTicks(5);
			input.holdKey(options -> options.keyAttack);
			context.waitTick();
			input.moveCursor(0, 160); // Maus nach unten = Überkopfschlag
			context.waitTicks(5);
			context.takeScreenshot("12_tp_back_overhead");
			context.waitTicks(12);
			input.releaseKey(options -> options.keyAttack);
			context.waitTicks(8);

			// --- Wurf (Ego-Sicht, damit der Körper nichts verdeckt) ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			input.lookAt(0f, 0f);
			context.waitTicks(3);
			input.pressKey(SaberInput.THROW);
			context.waitTicks(4);
			this.check(context.computeOnClient(mc -> Lightsabers.isThrown(mc.player.getMainHandItem(), mc.level)), "Schwert sollte nach F geworfen sein");
			String thrown = context.computeOnClient(mc -> {
				StringBuilder builder = new StringBuilder();
				for (Entity entity : mc.level.entitiesForRendering()) {
					if (entity instanceof ThrownLightsaberEntity saber) {
						builder.append(saber.position()).append(" returning=").append(saber.isReturning()).append("; ");
					}
				}
				return builder.toString();
			});
			SwMod.LOGGER.info("[Test] Geworfene Schwerter auf dem Client: {}", thrown.isEmpty() ? "keine" : thrown);
			this.check(!thrown.isEmpty(), "Geworfenes Schwert sollte auf dem Client existieren");
			context.takeScreenshot("13_throw");
			context.waitTicks(4);
			context.takeScreenshot("13b_throw_far");
			context.waitTicks(6);
			context.takeScreenshot("14_throw_return");
			context.waitTicks(40);
			this.check(context.computeOnClient(mc -> !Lightsabers.isThrown(mc.player.getMainHandItem(), mc.level)), "Schwert sollte zurückgekehrt sein");

			// --- Ausschalten & Inventar ---
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			input.pressKey(SaberInput.TOGGLE);
			context.waitTicks(12);
			this.check(context.computeOnClient(mc -> !Lightsabers.isIgnited(mc.player.getMainHandItem())), "Schwert sollte nach erneutem X aus sein");
			context.takeScreenshot("15_retracted_fp");

			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				int slot = 1;
				for (BladeColor color : BladeColor.values()) {
					ItemStack saber = Lightsabers.create(color);
					if (slot % 2 == 0) {
						LightsaberData data = Lightsabers.data(saber);
						saber.set(ModDataComponents.LIGHTSABER, data.withIgnited(true));
					}
					player.getInventory().setItem(slot++, saber);
				}
			});
			context.waitTicks(3);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(3);
			context.takeScreenshot("16_inventory");
			context.setScreen(() -> null);
		}

		this.testDedicatedServer(context);

		if (!this.failures.isEmpty()) {
			throw new AssertionError("Lichtschwert-Test fehlgeschlagen:\n - " + String.join("\n - ", this.failures));
		}
	}

	/** Dasselbe über einen echten dedizierten Server: hier laufen alle Pakete wirklich übers Netz. */
	private void testDedicatedServer(ClientGameTestContext context) {
		TestInput input = context.getInput();
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("time set midnight");
			input.moveCursor(1, 0);
			input.lookAt(0f, 0f);
			context.waitTicks(5);

			server.runCommand("execute as @p at @p run summon minecraft:zombie ^ ^ ^2.3 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.setItemInHand(InteractionHand.MAIN_HAND, Lightsabers.create(BladeColor.GREEN)));
			context.waitTicks(10);

			input.pressKey(SaberInput.TOGGLE);
			context.waitTicks(12);
			this.check(server.computeOnServer(s -> Lightsabers.isIgnited(s.getPlayerList().getPlayers().getFirst().getMainHandItem())),
					"[Server] Schwert sollte nach X auf dem Server gezündet sein");

			input.holdKey(options -> options.keyAttack);
			context.waitTick();
			input.moveCursor(-160, 0);
			context.waitTicks(6);
			context.takeScreenshot("20_server_swing_left");
			context.waitTicks(12);
			input.releaseKey(options -> options.keyAttack);
			context.waitTicks(5);
			float health = server.computeOnServer(s -> zombieHealth(s.overworld().getAllEntities()));
			this.check(health > 9.5f && health < 10.5f, "[Server] Zombie sollte nach einem Treffer ca. 10 Leben haben, hat aber " + health);

			input.holdKey(options -> options.keyUse);
			context.waitTicks(8);
			this.check(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().isUsingItem()), "[Server] Spieler sollte auf dem Server blocken");
			input.releaseKey(options -> options.keyUse);
			context.waitTicks(8);

			input.pressKey(SaberInput.THROW);
			context.waitTicks(4);
			context.takeScreenshot("21_server_throw");
			context.waitTicks(50);
			this.check(context.computeOnClient(mc -> !Lightsabers.isThrown(mc.player.getMainHandItem(), mc.level)), "[Server] Schwert sollte zurückgekehrt sein");
			float health2 = server.computeOnServer(s -> zombieHealth(s.overworld().getAllEntities()));
			this.check(health2 < health, "[Server] Wurf sollte den Zombie treffen (vorher " + health + ", nachher " + health2 + ")");
		}
	}

	/** Lässt das Mannequin (nur clientseitig) einen Schlag ausführen und fotografiert ihn mittendrin. */
	private void swingMannequin(ClientGameTestContext context, SwingDirection direction, String screenshot) {
		context.runOnClient(mc -> {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof Mannequin mannequin) {
					SaberAnimations.get(mannequin).startSwing(direction, SaberAnimations.ticks(), -1);
				}
			}
		});
		context.waitTicks(6);
		context.takeScreenshot(screenshot);
		context.waitTicks(14);
	}

	private static float zombieHealth(Iterable<Entity> entities) {
		for (Entity entity : entities) {
			if (entity instanceof Zombie zombie) {
				return zombie.getHealth();
			}
		}
		return -1f;
	}

	private void check(boolean condition, String message) {
		if (!condition) {
			this.failures.add(message);
			SwMod.LOGGER.error("[Test] FEHLER: {}", message);
		}
	}
}
