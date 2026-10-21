package com.shxn15.swmod.client.force.fx;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.texture.TextureAtlas;

import com.shxn15.swmod.SwMod;

/** Render-Pipelines der Machteffekte: Partikel, die sich additiv zu Licht aufaddieren. */
public final class ForceRenderTypes {
	public static final RenderPipeline GLOW_PARTICLE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
			.withLocation(SwMod.id("pipeline/glow_particle"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());

	public static final OitPipelineSet GLOW_PARTICLE_OIT = RenderPipelines.register(OitPipelineSet.builder("swmod_glow_particle",
			RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withShaderDefine("OIT_ADDITIVE").withCull(false)).build());

	/** Partikel-Ebene für leuchtende Machtpartikel. */
	public static final SingleQuadParticle.Layer GLOW_LAYER = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES,
			GLOW_PARTICLE, GLOW_PARTICLE_OIT);

	private ForceRenderTypes() {
	}

	public static void init() {
		// Lädt die Klasse, damit die Pipelines früh registriert werden.
	}
}
