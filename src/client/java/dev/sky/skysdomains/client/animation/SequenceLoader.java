package dev.sky.skysdomains.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.client.fx.OverlayManager;
import dev.sky.skysdomains.client.render.DomainSprites;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code assets/skysdomains/sequences/<domain>.json} on every resource reload (F3+T),
 * so animations can be tuned without restarting the game.
 */
public final class SequenceLoader {
	private static final Map<DomainType, Sequence> SEQUENCES = new EnumMap<>(DomainType.class);

	private SequenceLoader() {
	}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SkysDomains.id("sequences"),
				(ResourceManagerReloadListener) SequenceLoader::load);
	}

	public static Sequence get(DomainType type) {
		Sequence sequence = SEQUENCES.get(type);
		return sequence == null ? Sequence.EMPTY : sequence;
	}

	private static void load(ResourceManager manager) {
		SEQUENCES.clear();
		DomainSprites.invalidate();
		loadFrameSize(manager);
		for (DomainType type : DomainType.values()) {
			Identifier id = SkysDomains.id("sequences/" + type.id() + ".json");
			Optional<Resource> resource = manager.getResource(id);
			if (resource.isEmpty()) {
				SkysDomains.LOGGER.warn("Missing sequence file {}", id);
				continue;
			}
			try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				SEQUENCES.put(type, parse(json, type.id()));
			} catch (Exception e) {
				SkysDomains.LOGGER.error("Could not parse sequence {}", id, e);
			}
		}
		SkysDomains.LOGGER.info("Loaded {} domain sequences", SEQUENCES.size());
	}

	private static void loadFrameSize(ResourceManager manager) {
		Optional<Resource> resource = manager.getResource(SkysDomains.id("impact_frames.json"));
		if (resource.isEmpty()) return;
		try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			OverlayManager.setFrameSize(Actions.integer(json, "width", 2560), Actions.integer(json, "height", 1440));
		} catch (Exception e) {
			SkysDomains.LOGGER.warn("Could not read impact_frames.json", e);
		}
	}

	static Sequence parse(JsonObject json, String domainId) {
		Sequence.SphereStyle style = new Sequence.SphereStyle();
		if (json.has("sphere")) {
			JsonObject sphere = json.getAsJsonObject("sphere");
			style.texture = Actions.str(sphere, "texture", style.texture);
			style.color = Actions.color(sphere, "color", style.color);
			style.alpha = Actions.number(sphere, "alpha", style.alpha);
			style.insideAlpha = Actions.number(sphere, "inside_alpha", style.insideAlpha);
			style.insideOpaque = sphere.has("inside_opaque") && sphere.get("inside_opaque").getAsBoolean();
			style.scroll = Actions.number(sphere, "scroll", style.scroll);
			style.emissive = !sphere.has("emissive") || sphere.get("emissive").getAsBoolean();
		}

		Map<String, List<Keyframe>> phases = new HashMap<>();
		if (json.has("phases")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("phases").entrySet()) {
				List<Keyframe> keyframes = new ArrayList<>();
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					JsonObject keyframe = element.getAsJsonObject();
					keyframes.add(new Keyframe(Actions.integer(keyframe, "tick", 0), Actions.integer(keyframe, "every", 0),
							parseActions(keyframe.getAsJsonArray("actions"), domainId)));
				}
				keyframes.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
				phases.put(entry.getKey(), keyframes);
			}
		}

		Map<String, List<SequenceAction>> events = new HashMap<>();
		if (json.has("events")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("events").entrySet()) {
				events.put(entry.getKey(), parseActions(entry.getValue().getAsJsonArray(), domainId));
			}
		}
		return new Sequence(style, phases, events);
	}

	private static List<SequenceAction> parseActions(JsonArray array, String domainId) {
		List<SequenceAction> actions = new ArrayList<>();
		if (array == null) return actions;
		for (JsonElement element : array) {
			SequenceAction action = Actions.parse(element.getAsJsonObject(), domainId);
			if (action != null) actions.add(action);
		}
		return actions;
	}
}
