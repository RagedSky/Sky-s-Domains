package dev.sky.skysdomains.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.sky.skysdomains.SkysDomains;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves {@code config/skysdomains.json}. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static SkysDomainsConfig config = new SkysDomainsConfig();

	private ConfigManager() {
	}

	public static SkysDomainsConfig get() {
		return config;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(SkysDomains.MOD_ID + ".json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				SkysDomainsConfig loaded = GSON.fromJson(json, SkysDomainsConfig.class);
				if (loaded != null) config = loaded;
			} catch (IOException | RuntimeException e) {
				SkysDomains.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		// Always rewrite so new options show up in existing files.
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SkysDomains.LOGGER.error("Could not write {}", path, e);
		}
	}

	/** Re-reads the file (used by the /skysdomains reload command). */
	public static void reload() {
		load();
	}
}
