package dev.sky.skysdomains.client.animation;

import java.util.List;

/**
 * A keyframe inside a phase track: fires at {@code tick} (relative to the phase start) and,
 * if {@code every > 0}, again every {@code every} ticks afterwards.
 */
public record Keyframe(int tick, int every, List<SequenceAction> actions) {
	public boolean firesAt(int phaseAge) {
		if (phaseAge < tick) return false;
		if (phaseAge == tick) return true;
		return every > 0 && (phaseAge - tick) % every == 0;
	}
}
