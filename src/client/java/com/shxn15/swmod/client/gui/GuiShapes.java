package com.shxn15.swmod.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.mixin.GuiGraphicsExtractorAccessor;

/**
 * Freie Formen für die Benutzeroberfläche: Ringe, Kreissegmente, weiche Leuchtflecken und
 * Linien mit Farbverlauf. Vanilla kann nur achsenparallele Rechtecke füllen; für das runde
 * Fähigkeitenrad und das Holocron sammeln wir hier eigene Vierecke ({@link Mesh}) und reihen sie
 * als ein GUI-Element ein.
 *
 * <p>Winkel in Bogenmaß, 0 = oben, im Uhrzeigersinn.
 */
public final class GuiShapes {
	private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET).withCull(false).buildSnippet();

	/** Normale Transparenz. */
	public static final RenderPipeline BLEND = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(SwMod.id("pipeline/gui_shapes"))
			.build());

	/** Additiv: Farben addieren sich zu Licht (Leuchten, Glühen). */
	public static final RenderPipeline GLOW = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(SwMod.id("pipeline/gui_glow"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.build());

	private GuiShapes() {
	}

	public static void init() {
		// Lädt die Klasse, damit die Pipelines früh registriert werden.
	}

	public static Mesh mesh() {
		return new Mesh();
	}

	/** Viele Vierecke, die gemeinsam gezeichnet werden. */
	public static final class Mesh {
		private final FloatArrayList positions = new FloatArrayList();
		private final IntArrayList colors = new IntArrayList();
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;

		private Mesh() {
		}

		public boolean isEmpty() {
			return this.colors.isEmpty();
		}

		private void vertex(float x, float y, int color) {
			this.positions.add(x);
			this.positions.add(y);
			this.colors.add(color);
			this.minX = Math.min(this.minX, x);
			this.minY = Math.min(this.minY, y);
			this.maxX = Math.max(this.maxX, x);
			this.maxY = Math.max(this.maxY, y);
		}

		public Mesh quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
			this.vertex(x0, y0, c0);
			this.vertex(x1, y1, c1);
			this.vertex(x2, y2, c2);
			this.vertex(x3, y3, c3);
			return this;
		}

		public Mesh rect(float x0, float y0, float x1, float y1, int color) {
			return this.quad(x0, y0, color, x0, y1, color, x1, y1, color, x1, y0, color);
		}

		/** Rechteck mit Farbverlauf von links nach rechts. */
		public Mesh rectH(float x0, float y0, float x1, float y1, int left, int right) {
			return this.quad(x0, y0, left, x0, y1, left, x1, y1, right, x1, y0, right);
		}

		/** Rechteck mit Farbverlauf von oben nach unten. */
		public Mesh rectV(float x0, float y0, float x1, float y1, int top, int bottom) {
			return this.quad(x0, y0, top, x0, y1, bottom, x1, y1, bottom, x1, y0, top);
		}

		/**
		 * Ringsegment zwischen zwei Radien und zwei Winkeln.
		 *
		 * @param inner Farbe am inneren Rand
		 * @param outer Farbe am äußeren Rand
		 */
		public Mesh arc(float cx, float cy, float r0, float r1, float a0, float a1, int inner, int outer) {
			int segments = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Mth.PI / 36f)));
			for (int i = 0; i < segments; i++) {
				float t0 = Mth.lerp(i / (float) segments, a0, a1);
				float t1 = Mth.lerp((i + 1) / (float) segments, a0, a1);
				float s0 = Mth.sin(t0);
				float c0 = -Mth.cos(t0);
				float s1 = Mth.sin(t1);
				float c1 = -Mth.cos(t1);
				this.quad(cx + s0 * r0, cy + c0 * r0, inner,
						cx + s0 * r1, cy + c0 * r1, outer,
						cx + s1 * r1, cy + c1 * r1, outer,
						cx + s1 * r0, cy + c1 * r0, inner);
			}
			return this;
		}

		public Mesh ring(float cx, float cy, float r0, float r1, int inner, int outer) {
			return this.arc(cx, cy, r0, r1, 0f, Mth.TWO_PI, inner, outer);
		}

		/** Kreisfläche mit Verlauf von der Mitte zum Rand. */
		public Mesh disc(float cx, float cy, float radius, int center, int edge) {
			return this.arc(cx, cy, 0f, radius, 0f, Mth.TWO_PI, center, edge);
		}

		/** Weicher Leuchtfleck: in der Mitte {@code color}, nach außen durchsichtig. */
		public Mesh glow(float cx, float cy, float radius, int color) {
			int mid = ARGB.color((int) (ARGB.alpha(color) * 0.35f), color);
			this.arc(cx, cy, 0f, radius * 0.45f, 0f, Mth.TWO_PI, color, mid);
			return this.arc(cx, cy, radius * 0.45f, radius, 0f, Mth.TWO_PI, mid, ARGB.color(0, color));
		}

		/** Vierzackiger Funkelstern: heller Kern, spitze, nach außen durchsichtige Zacken. */
		public Mesh star(float cx, float cy, float size, int color) {
			int tip = ARGB.color(0, color);
			float waist = size * 0.2f;
			// Die gemeinsame Diagonale beider Dreiecke liegt jeweils auf dem hellen Kern.
			this.quad(cx, cy + waist, color, cx + size, cy, tip, cx, cy - waist, color, cx - size, cy, tip);
			return this.quad(cx - waist, cy, color, cx, cy + size, tip, cx + waist, cy, color, cx, cy - size, tip);
		}

		/** Linie mit Breite und Farbverlauf. */
		public Mesh line(float x0, float y0, float x1, float y1, float width, int c0, int c1) {
			float dx = x1 - x0;
			float dy = y1 - y0;
			float length = Mth.sqrt(dx * dx + dy * dy);
			if (length < 1.0E-4f) {
				return this;
			}
			float nx = -dy / length * width * 0.5f;
			float ny = dx / length * width * 0.5f;
			return this.quad(x0 + nx, y0 + ny, c0, x0 - nx, y0 - ny, c0, x1 - nx, y1 - ny, c1, x1 + nx, y1 + ny, c1);
		}

		/** Weiche Linie: heller Kern, durchsichtige Ränder (für Leuchtstrahlen). */
		public Mesh softLine(float x0, float y0, float x1, float y1, float width, int c0, int c1) {
			float dx = x1 - x0;
			float dy = y1 - y0;
			float length = Mth.sqrt(dx * dx + dy * dy);
			if (length < 1.0E-4f) {
				return this;
			}
			float nx = -dy / length * width * 0.5f;
			float ny = dx / length * width * 0.5f;
			int e0 = ARGB.color(0, c0);
			int e1 = ARGB.color(0, c1);
			this.quad(x0, y0, c0, x0 + nx, y0 + ny, e0, x1 + nx, y1 + ny, e1, x1, y1, c1);
			return this.quad(x0 - nx, y0 - ny, e0, x0, y0, c0, x1, y1, c1, x1 - nx, y1 - ny, e1);
		}

		/** Gibt die Form zum Zeichnen ab (mit der aktuellen Transformation des Grafik-Stapels). */
		public void submit(GuiGraphicsExtractor graphics, boolean additive) {
			if (this.colors.isEmpty()) {
				return;
			}
			Matrix3x2f pose = new Matrix3x2f(graphics.pose());
			ScreenRectangle bounds = new ScreenRectangle(
					(int) Math.floor(this.minX) - 1, (int) Math.floor(this.minY) - 1,
					(int) Math.ceil(this.maxX - this.minX) + 2, (int) Math.ceil(this.maxY - this.minY) + 2).transformMaxBounds(pose);
			((GuiGraphicsExtractorAccessor) graphics).swmod$guiRenderState().addGuiElement(
					new MeshState(additive ? GLOW : BLEND, pose, this.positions.toFloatArray(), this.colors.toIntArray(), bounds));
		}
	}

	private record MeshState(RenderPipeline pipeline, Matrix3x2f pose, float[] positions, int[] colors, ScreenRectangle bounds)
			implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			for (int i = 0; i < this.colors.length; i++) {
				consumer.addVertexWith2DPose(this.pose, this.positions[i * 2], this.positions[i * 2 + 1]).setColor(this.colors[i]);
			}
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		@Override
		public @Nullable ScreenRectangle scissorArea() {
			return null;
		}
	}
}
