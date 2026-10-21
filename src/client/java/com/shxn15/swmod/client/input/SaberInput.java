package com.shxn15.swmod.client.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.client.force.ForceInput;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.lightsaber.LightsaberData;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.network.SaberActionPayload;
import com.shxn15.swmod.network.SaberStatusPayload;
import com.shxn15.swmod.registry.ModDataComponents;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Steuerung des Lichtschwerts:
 * <ul>
 *     <li>Linksklick halten + Maus bewegen: Schlag in Bewegungsrichtung (8 Richtungen)</li>
 *     <li>Kurzer Linksklick ohne Mausbewegung: Richtung über WASD, ohne Taste ein Stoß</li>
 *     <li>Rechtsklick: Deckung (über die normale Item-Benutzung)</li>
 *     <li>X: Klinge an/aus, F: Werfen</li>
 * </ul>
 */
public final class SaberInput {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SwMod.id("lightsaber"));

	public static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.swmod.toggle_saber", InputConstants.Type.KEYBOARD, InputConstants.KEY_X, CATEGORY));
	public static final KeyMapping THROW = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.swmod.throw_saber", InputConstants.Type.KEYBOARD, InputConstants.KEY_F, CATEGORY));

	/** Mausbewegung (in "Grad", wie die Kamera sich drehen würde), ab der ein Schlag ausgelöst wird. */
	private static final double GESTURE_THRESHOLD = 2.4;
	/** So stark dreht sich die Kamera, während man mit gehaltener Maustaste einen Schlag "zeichnet". */
	private static final double CAMERA_DAMPING = 0.35;
	/** Nach so vielen Ticks ohne Mausbewegung wird ein gehaltener Klick zum Standardangriff. */
	private static final int CLICK_TIMEOUT = 7;
	/** Weniger Mausbewegung pro Tick (Grad) gilt als "Maus steht still": der aktuelle Mauszug ist zu Ende. */
	private static final double REST_MOVEMENT = 0.4;
	/** So viele ruhige Ticks beenden einen Mauszug. */
	private static final int REST_TICKS = 2;
	/** Während eines Mauszugs startet nur eine deutliche Richtungsänderung (Kosinus darunter) einen neuen Schlag. */
	private static final double NEW_STROKE_MAX_DOT = -0.2;

	private static boolean attackHeld;
	private static boolean gestureUsed;
	private static int heldTicks;
	private static double gestureX;
	private static double gestureY;

	// Ein Mauszug löst genau einen Schlag aus. Bewegt man die Maus danach in dieselbe Richtung weiter,
	// gehört das noch zum selben Zug (früher kam so fast immer ein zweiter Schlag hinterher).
	private static boolean strokeLocked;
	private static double strokeX;
	private static double strokeY;
	private static double tickMovement;
	private static int restTicks;

	// Vom Server gemeldeter Status (für HUD und Vorhersage).
	private static float blockPoints = 100f;
	private static int stunTicks;
	private static int guardTicks;

	// Für die HUD-Anzeige der zuletzt erkannten Richtung.
	private static SwingDirection lastDirection = SwingDirection.STAB;
	private static int lastDirectionTick = -100;

	private SaberInput() {
	}

	public static void init() {
		// Lädt die Klasse, damit die Tastenbelegungen registriert werden.
	}

	/** Darf der Spieler gerade mit dem Lichtschwert kämpfen? */
	public static boolean isSaberReady(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && !player.isSpectator() && Lightsabers.isReadyInMainHand(player);
	}

	// ------------------------------------------------------------------------------------------
	// Tick (vor der Vanilla-Tastenverarbeitung)
	// ------------------------------------------------------------------------------------------

	public static void startTick(Minecraft minecraft) {
		if (stunTicks > 0) stunTicks--;
		if (guardTicks > 0) guardTicks--;

		LocalPlayer player = minecraft.player;
		if (player == null) {
			attackHeld = false;
			return;
		}

		ItemStack mainHand = player.getMainHandItem();
		boolean holdingSaber = Lightsabers.isLightsaber(mainHand);

		handleToggle(minecraft, player, mainHand, holdingSaber);
		handleThrow(minecraft, player, holdingSaber);
		handleAttack(minecraft, player);
	}

	private static void handleToggle(Minecraft minecraft, LocalPlayer player, ItemStack mainHand, boolean holdingSaber) {
		boolean pressed = false;
		while (TOGGLE.consumeClick()) {
			pressed = true;
		}
		// X ist in Vanilla "Schnellleiste laden" (nur Kreativ). Mit Lichtschwert in der Hand gehört X uns.
		if (holdingSaber && TOGGLE.same(minecraft.options.keyLoadHotbarActivator)) {
			while (minecraft.options.keyLoadHotbarActivator.consumeClick()) {
				pressed = true;
			}
		}

		if (!pressed || !holdingSaber || minecraft.gui.screen() != null || Lightsabers.isThrown(mainHand, player.level())) {
			return;
		}

		// Vorhersage: sofortiges Feedback, der Server bestätigt per Item-Update.
		LightsaberData data = Lightsabers.data(mainHand);
		boolean ignite = !data.ignited();
		mainHand.set(ModDataComponents.LIGHTSABER, data.withIgnited(ignite));
		player.playSound(ignite ? ModSounds.LIGHTSABER_IGNITE : ModSounds.LIGHTSABER_RETRACT, 1.0f, 1.0f);
		if (!ignite) {
			SaberAnimations.get(player).startReaction(com.shxn15.swmod.combat.SaberAnimation.CANCEL, SwingDirection.STAB, 0, SaberAnimations.ticks());
		}
		ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.TOGGLE, 0));
	}

	private static void handleThrow(Minecraft minecraft, LocalPlayer player, boolean holdingSaber) {
		boolean pressed = false;
		while (THROW.consumeClick()) {
			pressed = true;
		}

		boolean sharesSwapKey = THROW.same(minecraft.options.keySwapOffhand);
		if (holdingSaber && sharesSwapKey) {
			// F ist in Vanilla "Hand wechseln". Mit Lichtschwert in der Hand wird stattdessen geworfen.
			while (minecraft.options.keySwapOffhand.consumeClick()) {
				pressed = true;
			}
		}

		if (!pressed || minecraft.gui.screen() != null) {
			return;
		}

		if (!holdingSaber) {
			if (sharesSwapKey && !player.isSpectator()) {
				// Falls unsere Taste der Vanilla-Taste den Klick "weggenommen" hat: Hand trotzdem wechseln.
				player.connection.send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			}
			return;
		}

		if (isSaberReady(minecraft) && stunTicks <= 0 && !player.isUsingItem()) {
			ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.THROW, 0));
		}
	}

	private static void handleAttack(Minecraft minecraft, LocalPlayer player) {
		boolean ready = isSaberReady(minecraft) && minecraft.gui.screen() == null && !ForceInput.blocksAttack();
		boolean down = ready && minecraft.options.keyAttack.isDown();

		// Auch Klicks zählen, die zwischen zwei Ticks gedrückt UND losgelassen wurden.
		int clicks = 0;
		if (ready) {
			while (minecraft.options.keyAttack.consumeClick()) {
				clicks++;
			}
		}

		// Bewegung zerfällt mit der Zeit: nur zügige Bewegungen zählen als Schlag.
		gestureX *= 0.55;
		gestureY *= 0.55;

		// Steht die Maus kurz still, ist der Mauszug vorbei und der nächste darf wieder schlagen.
		restTicks = tickMovement < REST_MOVEMENT ? restTicks + 1 : 0;
		tickMovement = 0;
		if (restTicks >= REST_TICKS) {
			strokeLocked = false;
		}

		if (!down && !attackHeld && clicks > 0) {
			requestSwing(minecraft, player, directionFromKeys(minecraft));
		} else if (down && !attackHeld) {
			attackHeld = true;
			gestureUsed = false;
			strokeLocked = false;
			heldTicks = 0;
			gestureX = 0;
			gestureY = 0;
		} else if (down) {
			heldTicks++;
			if (!gestureUsed && heldTicks >= CLICK_TIMEOUT) {
				gestureUsed = true;
				requestSwing(minecraft, player, directionFromKeys(minecraft));
			}
		} else if (attackHeld) {
			attackHeld = false;
			if (ready && !gestureUsed) {
				requestSwing(minecraft, player, directionFromKeys(minecraft));
			}
		}
	}

	/** Klick ohne Mausbewegung: Richtung über die Bewegungstasten (wie in Jedi Academy / Movie Battles). */
	private static SwingDirection directionFromKeys(Minecraft minecraft) {
		int x = (minecraft.options.keyRight.isDown() ? 1 : 0) - (minecraft.options.keyLeft.isDown() ? 1 : 0);
		int y = (minecraft.options.keyDown.isDown() ? 1 : 0) - (minecraft.options.keyUp.isDown() ? 1 : 0);
		if (x == 0 && y == 0) {
			return SwingDirection.STAB;
		}
		// Vorwärts = Überkopfschlag (nach unten), Rückwärts = Aufwärtsschlag.
		return SwingDirection.fromScreenVector(x, y);
	}

	// ------------------------------------------------------------------------------------------
	// Maus (aus MouseHandlerMixin)
	// ------------------------------------------------------------------------------------------

	/**
	 * Wird mit der aufgelaufenen Mausbewegung (Pixel) aufgerufen, bevor die Kamera gedreht wird.
	 *
	 * @return Faktor, mit dem die Kameradrehung skaliert wird
	 */
	public static double onMouseMove(double dx, double dy) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!attackHeld || !isSaberReady(minecraft) || minecraft.gui.screen() != null || ForceInput.blocksAttack()) {
			return 1.0;
		}

		// Gleiche Umrechnung wie Vanilla: Pixel -> Drehung in Grad.
		double sensitivity = minecraft.options.sensitivity().get() * 0.6 + 0.2;
		double degreesPerPixel = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
		double mx = dx * degreesPerPixel;
		double my = -dy * degreesPerPixel; // Bildschirm-y zeigt nach unten
		gestureX += mx;
		gestureY += my;
		tickMovement += Math.sqrt(mx * mx + my * my);

		double magnitude = Math.sqrt(gestureX * gestureX + gestureY * gestureY);
		if (magnitude >= GESTURE_THRESHOLD && minecraft.player != null) {
			double nx = gestureX / magnitude;
			double ny = gestureY / magnitude;
			gestureX = 0;
			gestureY = 0;

			// Derselbe Mauszug geht weiter (auch in leichter Kurve): kein neuer Schlag, nur Richtung nachführen.
			boolean sameStroke = strokeLocked && nx * strokeX + ny * strokeY > NEW_STROKE_MAX_DOT;
			strokeLocked = true;
			strokeX = nx;
			strokeY = ny;
			if (!sameStroke) {
				gestureUsed = true;
				requestSwing(minecraft, minecraft.player, SwingDirection.fromScreenVector(nx, ny));
			}
		}

		return CAMERA_DAMPING;
	}

	// ------------------------------------------------------------------------------------------

	private static void requestSwing(Minecraft minecraft, LocalPlayer player, SwingDirection direction) {
		if (stunTicks > 0 || player.isUsingItem()) {
			return;
		}

		lastDirection = direction;
		lastDirectionTick = SaberAnimations.ticks();

		SaberAnimState anim = SaberAnimations.get(player);
		int now = SaberAnimations.ticks();
		if (anim.canChain(now)) {
			anim.startSwing(direction, now, -1);
		} else {
			anim.queue(direction);
		}
		ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.SWING, direction.id()));
	}

	public static boolean canBlock() {
		return guardTicks <= 0 && stunTicks <= 0;
	}

	public static void handleStatus(SaberStatusPayload payload) {
		blockPoints = payload.blockPoints();
		stunTicks = payload.stunTicks();
		guardTicks = payload.guardTicks();
	}

	// --- für das HUD ---

	public static float blockPoints() {
		return blockPoints;
	}

	public static int stunTicks() {
		return stunTicks;
	}

	public static int guardTicks() {
		return guardTicks;
	}

	public static boolean isAttackHeld() {
		return attackHeld;
	}

	public static double gestureX() {
		return gestureX;
	}

	public static double gestureY() {
		return gestureY;
	}

	public static SwingDirection lastDirection() {
		return lastDirection;
	}

	public static int ticksSinceLastDirection() {
		return SaberAnimations.ticks() - lastDirectionTick;
	}
}
