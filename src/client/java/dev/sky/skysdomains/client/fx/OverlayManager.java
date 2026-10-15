package dev.sky.skysdomains.client.fx;

import dev.sky.skysdomains.config.ConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fullscreen overlays drawn as the last HUD element: impact frames (full-screen PNGs), colour
 * flashes, vignette, and the fog tint. "Reduced flashes" (accessibility option) turns hard
 * frames into soft fades.
 */
public final class OverlayManager {
	private static final OverlayManager INSTANCE = new OverlayManager();

	/** Impact frame textures are 256x144 placeholders, scaled to the screen. */
	private static final int FRAME_WIDTH = 256;
	private static final int FRAME_HEIGHT = 144;

	private record Frame(Identifier texture, int total, int[] remaining) {
	}

	private record Flash(int color, int hold, int fade, int[] age) {
	}

	private final List<Frame> frames = new ArrayList<>();
	private final List<Flash> flashes = new ArrayList<>();
	private final Map<UUID, float[]> vignettes = new HashMap<>();

	private OverlayManager() {
	}

	public static OverlayManager get() {
		return INSTANCE;
	}

	public void impactFrame(Identifier texture, int ticks) {
		if (ConfigManager.get().accessibility.reducedFlashes) {
			ticks = Math.max(ticks, 4);
		}
		frames.add(new Frame(texture, ticks, new int[]{ticks}));
	}

	public void flash(int rgb, int hold, int fade) {
		if (ConfigManager.get().accessibility.reducedFlashes) {
			hold = Math.max(1, hold / 2);
			fade = Math.max(fade, 6);
		}
		flashes.add(new Flash(rgb, hold, fade, new int[]{0}));
	}

	/** {@code ticks} 0 = until {@link #clearDomain(UUID)}. */
	public void vignette(UUID domain, float strength, int ticks) {
		vignettes.put(domain, new float[]{strength, ticks});
	}

	public void clearDomain(UUID domain) {
		vignettes.remove(domain);
	}

	public void clearAll() {
		frames.clear();
		flashes.clear();
		vignettes.clear();
	}

	public void tick() {
		Iterator<Frame> frameIterator = frames.iterator();
		while (frameIterator.hasNext()) {
			Frame frame = frameIterator.next();
			if (--frame.remaining()[0] <= 0) frameIterator.remove();
		}
		Iterator<Flash> flashIterator = flashes.iterator();
		while (flashIterator.hasNext()) {
			Flash flash = flashIterator.next();
			if (++flash.age()[0] >= flash.hold() + flash.fade()) flashIterator.remove();
		}
		Iterator<float[]> vignetteIterator = vignettes.values().iterator();
		while (vignetteIterator.hasNext()) {
			float[] v = vignetteIterator.next();
			if (v[1] > 0 && --v[1] <= 0) vignetteIterator.remove();
		}
	}

	/** HUD element: draws everything over the whole screen. */
	public void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean reduced = ConfigManager.get().accessibility.reducedFlashes;

		// Fog tint (density -> alpha), drawn first so frames/flashes stay crisp.
		FogSkyState fogSky = FogSkyState.get();
		if (fogSky.hasFog()) {
			int tint = ARGB.color((int) (fogSky.fogDensity() * 90), fogSky.fogColor());
			graphics.fill(0, 0, width, height, tint);
		}

		for (float[] v : vignettes.values()) {
			drawVignette(graphics, width, height, v[0]);
		}

		for (Flash flash : flashes) {
			float a = 1.0F;
			int age = flash.age()[0];
			if (age >= flash.hold()) a = 1.0F - (float) (age - flash.hold()) / Math.max(1, flash.fade());
			if (reduced) a *= 0.6F;
			graphics.fill(0, 0, width, height, ARGB.color((int) (a * 255), flash.color()));
		}

		for (Frame frame : frames) {
			graphics.pose().pushMatrix();
			graphics.pose().scale((float) width / FRAME_WIDTH, (float) height / FRAME_HEIGHT);
			graphics.blit(RenderPipelines.GUI_TEXTURED, frame.texture(), 0, 0, 0.0F, 0.0F, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
			graphics.pose().popMatrix();
			if (reduced) {
				// Soften the hard frame with a semi-transparent black layer.
				graphics.fill(0, 0, width, height, ARGB.color(90, 0x000000));
			}
		}
	}

	/** Concentric translucent frames darken the screen edges. */
	private static void drawVignette(GuiGraphicsExtractor graphics, int width, int height, float strength) {
		int steps = 8;
		int maxInset = Math.min(width, height) / 3;
		for (int i = 0; i < steps; i++) {
			int inset = maxInset * i / steps;
			int next = maxInset * (i + 1) / steps;
			int alpha = (int) (strength * 255 * (1.0F - (float) i / steps) / steps * 1.6F);
			int color = ARGB.color(Math.min(255, alpha), 0x000000);
			graphics.fill(inset, inset, width - inset, next, color);                       // top band
			graphics.fill(inset, height - next, width - inset, height - inset, color);     // bottom band
			graphics.fill(inset, next, next, height - next, color);                        // left band
			graphics.fill(width - next, next, width - inset, height - next, color);        // right band
		}
	}
}
