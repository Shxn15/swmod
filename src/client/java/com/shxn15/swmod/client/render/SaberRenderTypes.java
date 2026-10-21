package com.shxn15.swmod.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.shxn15.swmod.SwMod;

/**
 * Eigener Render-Typ für das Leuchten der Klinge: additiv wie ein Blitz, aber ohne Tiefe zu
 * schreiben und ohne Backface-Culling. So überlagern sich die weichen Leuchtbänder sauber,
 * statt sich gegenseitig zu verdecken.
 */
public final class SaberRenderTypes {
	private static final RenderPipeline.Snippet GLOW_SNIPPET = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
			.withCull(false)
			.buildSnippet();

	public static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(GLOW_SNIPPET)
			.withLocation(SwMod.id("pipeline/saber_glow"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build());

	/** Variante für "verbesserte Transparenz" (Order Independent Transparency). */
	public static final OitPipelineSet GLOW_OIT = RenderPipelines.register(
			OitPipelineSet.builder("swmod_saber_glow", RenderPipeline.builder(GLOW_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build());

	public static final RenderType GLOW = RenderType.create("swmod_saber_glow",
			RenderSetup.builder(GLOW_PIPELINE).setOitPipelines(GLOW_OIT).sortOnUpload().createRenderSetup());

	private SaberRenderTypes() {
	}

	public static void init() {
		// Lädt die Klasse, damit die Pipelines früh registriert werden.
	}
}
