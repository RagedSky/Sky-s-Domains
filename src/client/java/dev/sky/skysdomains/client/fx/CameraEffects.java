package dev.sky.skysdomains.client.fx;

import dev.sky.skysdomains.client.animation.Easing;
import dev.sky.skysdomains.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;

import java.util.Random;

/**
 * Camera shake and FOV punch.
 *
 * <p>Shake is applied after level extraction by rotating the extracted camera orientation a little
 * (so terrain, entities and scene objects shake together). FOV punch temporarily changes the FOV
 * option and restores it when the punch ends (also on disconnect).
 */
public final class CameraEffects {
	private static final CameraEffects INSTANCE = new CameraEffects();
	private static final Random RANDOM = new Random();

	private int shakeRemaining;
	private int shakeTotal;
	private float shakeStrength;

	private int fovRemaining;
	private int fovTotal;
	private float fovDelta;
	private Easing fovEasing = Easing.EASE_OUT_EXPO;
	private Integer fovOriginal;

	private CameraEffects() {
	}

	public static CameraEffects get() {
		return INSTANCE;
	}

	public void shake(float strength, int ticks) {
		float multiplier = ConfigManager.get().accessibility.shakeMultiplier;
		if (multiplier <= 0.0F) return;
		shakeStrength = Math.max(shakeStrength, strength * multiplier);
		shakeRemaining = Math.max(shakeRemaining, ticks);
		shakeTotal = Math.max(shakeTotal, ticks);
	}

	public void fovPunch(float delta, int ticks, Easing easing) {
		Minecraft client = Minecraft.getInstance();
		if (fovOriginal == null) fovOriginal = client.options.fov().get();
		fovDelta = delta;
		fovRemaining = ticks;
		fovTotal = ticks;
		fovEasing = easing;
	}

	public void tick() {
		Minecraft client = Minecraft.getInstance();
		if (shakeRemaining > 0) {
			shakeRemaining--;
			if (shakeRemaining == 0) {
				shakeStrength = 0;
				shakeTotal = 0;
			}
		}
		if (fovOriginal != null) {
			if (fovRemaining > 0) {
				fovRemaining--;
				float progress = fovTotal == 0 ? 1.0F : 1.0F - (float) fovRemaining / fovTotal;
				float eased = 1.0F - fovEasing.apply(progress);
				int fov = Math.round(fovOriginal + fovDelta * eased);
				client.options.fov().set(Math.max(30, Math.min(110, fov)));
			} else {
				client.options.fov().set(fovOriginal);
				fovOriginal = null;
			}
		}
	}

	/** Registered on {@code LevelExtractionEvents.END_EXTRACTION}. */
	public void afterExtraction(LevelExtractionContext context) {
		if (shakeRemaining <= 0 || shakeStrength <= 0.0F) return;
		float falloff = shakeTotal == 0 ? 1.0F : (float) shakeRemaining / shakeTotal;
		float amount = shakeStrength * falloff * 0.03F;
		float yaw = (RANDOM.nextFloat() - 0.5F) * amount;
		float pitch = (RANDOM.nextFloat() - 0.5F) * amount;
		float roll = (RANDOM.nextFloat() - 0.5F) * amount * 0.5F;
		CameraRenderState camera = context.levelState().cameraRenderState;
		camera.orientation.mul(new Quaternionf().rotationXYZ(pitch, yaw, roll));
	}

	public void reset() {
		shakeRemaining = 0;
		shakeStrength = 0;
		if (fovOriginal != null) {
			Minecraft.getInstance().options.fov().set(fovOriginal);
			fovOriginal = null;
		}
		fovRemaining = 0;
	}
}
