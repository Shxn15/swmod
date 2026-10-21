package com.shxn15.swmod.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.force.ForceData;

/** Zusatzdaten an Spielern (Fabric Data Attachments). */
public final class ModAttachments {
	/** Erlernte Machtfähigkeiten und Radbelegung: gespeichert, über den Tod hinweg behalten, an den eigenen Client synchronisiert. */
	public static final AttachmentType<ForceData> FORCE = AttachmentRegistry.<ForceData>builder()
			.persistent(ForceData.CODEC)
			.copyOnDeath()
			.syncWith(ForceData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(SwMod.id("force"));

	private ModAttachments() {
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
