package dev.sky.skysdomains.client.animation;

/** One action of a keyframe. Implementations live in {@link Actions}. */
@FunctionalInterface
public interface SequenceAction {
	/**
	 * @param ctx     the running domain and all client systems
	 * @param instant true when catching up (late join / resource reload): skip transient
	 *                overlays and sounds, jump tweens to their end
	 */
	void run(ActionContext ctx, boolean instant);
}
