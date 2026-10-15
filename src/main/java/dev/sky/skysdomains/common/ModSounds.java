package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sound events. Every event is a placeholder in {@code assets/skysdomains/sounds.json} that
 * currently points at a vanilla sound; replace the entry with your own OGG files.
 *
 * <p>Per domain: {@code <domain>.open}, {@code <domain>.loop}, {@code <domain>.impact},
 * {@code <domain>.close}. Plus generic events used by the sequencer.
 */
public final class ModSounds {
	public static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();

	public static final SoundEvent ACTIVATE = create("domain.activate");
	public static final SoundEvent DENIED = create("domain.denied");
	public static final SoundEvent IMPACT_FRAME = create("domain.impact_frame");
	public static final SoundEvent CLASH = create("domain.clash");
	public static final SoundEvent PLANET_LAUNCH = create("collapse.planet_launch");
	public static final SoundEvent PLANET_IMPACT = create("collapse.planet_impact");
	public static final SoundEvent SUN_IMPACT = create("collapse.sun_impact");
	public static final SoundEvent WHITE_HOLE = create("collapse.white_hole");
	public static final SoundEvent GLASS_SHATTER = create("glass_heaven.shatter");

	static {
		for (DomainType type : DomainType.values()) {
			create(type.id() + ".open");
			create(type.id() + ".loop");
			create(type.id() + ".impact");
			create(type.id() + ".close");
		}
	}

	private ModSounds() {
	}

	private static SoundEvent create(String path) {
		Identifier id = SkysDomains.id(path);
		SoundEvent event = SoundEvent.createVariableRangeEvent(id);
		EVENTS.put(path, event);
		return event;
	}

	public static void register() {
		for (Map.Entry<String, SoundEvent> entry : EVENTS.entrySet()) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, SkysDomains.id(entry.getKey()), entry.getValue());
		}
	}

	/** Looks up a registered sound by id string (mod namespace optional). */
	public static SoundEvent byId(String id) {
		String path = id.startsWith(SkysDomains.MOD_ID + ":") ? id.substring(SkysDomains.MOD_ID.length() + 1) : id;
		SoundEvent event = EVENTS.get(path);
		if (event != null) return event;
		Identifier identifier = Identifier.tryParse(id);
		if (identifier != null) {
			SoundEvent vanilla = BuiltInRegistries.SOUND_EVENT.getValue(identifier);
			if (vanilla != null) return vanilla;
		}
		return IMPACT_FRAME;
	}
}
