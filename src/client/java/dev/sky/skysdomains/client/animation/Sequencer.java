package dev.sky.skysdomains.client.animation;

import dev.sky.skysdomains.client.ClientDomain;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives one domain's sequence: fires phase keyframes relative to the phase age and runs event
 * actions when the server reports something (impact, shatter, ...).
 */
public final class Sequencer {
	private final ClientDomain domain;
	private final Sequence sequence;
	private final Map<String, Integer> eventCounters = new HashMap<>();
	private String currentPhase = "";

	public Sequencer(ClientDomain domain, Sequence sequence) {
		this.domain = domain;
		this.sequence = sequence;
	}

	/** Called when a phase starts. {@code catchUpTicks} > 0 replays earlier keyframes instantly (late join). */
	public void startPhase(String phase, int catchUpTicks) {
		currentPhase = phase;
		if (catchUpTicks <= 0) return;
		ActionContext ctx = ActionContext.phase(Minecraft.getInstance(), domain);
		for (Keyframe keyframe : sequence.phase(phase)) {
			if (keyframe.tick() < catchUpTicks && keyframe.every() == 0) {
				for (SequenceAction action : keyframe.actions()) action.run(ctx, true);
			}
		}
	}

	/** Called every client tick with the current age inside the phase. */
	public void tick(int phaseAge) {
		List<Keyframe> keyframes = sequence.phase(currentPhase);
		if (keyframes.isEmpty()) return;
		ActionContext ctx = null;
		for (Keyframe keyframe : keyframes) {
			if (!keyframe.firesAt(phaseAge)) continue;
			if (ctx == null) ctx = ActionContext.phase(Minecraft.getInstance(), domain);
			for (SequenceAction action : keyframe.actions()) action.run(ctx, false);
		}
	}

	public void event(String name, Vec3 pos, int a, int b) {
		List<SequenceAction> actions = sequence.event(name);
		if (actions == null) return;
		int counter = eventCounters.merge(name, 1, Integer::sum);
		ActionContext ctx = ActionContext.event(Minecraft.getInstance(), domain, name, pos, a, b, counter);
		for (SequenceAction action : actions) action.run(ctx, false);
	}

	public Sequence sequence() {
		return sequence;
	}
}
