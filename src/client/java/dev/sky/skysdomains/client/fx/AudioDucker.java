package dev.sky.skysdomains.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * AudioDuck: temporarily lowers music, ambient, hostile, neutral, block and weather sounds so the
 * domain sounds stand out. Restores the player's option values afterwards.
 */
public final class AudioDucker {
	private static final AudioDucker INSTANCE = new AudioDucker();
	private static final SoundSource[] DUCKED = {SoundSource.MUSIC, SoundSource.AMBIENT, SoundSource.HOSTILE,
			SoundSource.NEUTRAL, SoundSource.BLOCKS, SoundSource.WEATHER, SoundSource.RECORDS};

	private int remaining;
	private float level = 1.0F;
	private boolean active;

	private AudioDucker() {
	}

	public static AudioDucker get() {
		return INSTANCE;
	}

	public void duck(int ticks, float level) {
		this.remaining = Math.max(this.remaining, ticks);
		this.level = Math.min(this.level, Math.max(0.0F, level));
		apply();
	}

	/** Lets a long duck (e.g. Stasis "silence") fade out within {@code maxRemaining} ticks. */
	public void release(int maxRemaining) {
		if (active) remaining = Math.min(remaining, maxRemaining);
	}

	public void tick() {
		if (!active) return;
		if (--remaining <= 0) reset();
	}

	private void apply() {
		Minecraft client = Minecraft.getInstance();
		for (SoundSource source : DUCKED) {
			float option = client.options.getSoundSourceVolume(source);
			client.getSoundManager().updateCategoryVolume(source, option * level);
		}
		active = true;
	}

	public void reset() {
		if (!active) return;
		Minecraft client = Minecraft.getInstance();
		for (SoundSource source : DUCKED) {
			client.getSoundManager().updateCategoryVolume(source, client.options.getSoundSourceVolume(source));
		}
		active = false;
		remaining = 0;
		level = 1.0F;
	}
}
