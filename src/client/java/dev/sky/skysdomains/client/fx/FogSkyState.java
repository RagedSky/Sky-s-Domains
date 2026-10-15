package dev.sky.skysdomains.client.fx;

import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fog colour and sky override requested by domains (the most recent request wins).
 * The {@code LevelRendererFogSkyMixin} applies the colour to the vanilla fog colour and disables
 * the vanilla sky while a sky override is active; the domain sphere's inner wall then shows the
 * skybox texture ("space", "void").
 */
public final class FogSkyState {
	private static final FogSkyState INSTANCE = new FogSkyState();

	private record Fog(int color, float density) {
	}

	private final Map<UUID, Fog> fogs = new LinkedHashMap<>();
	private final Map<UUID, String> skies = new LinkedHashMap<>();

	private FogSkyState() {
	}

	public static FogSkyState get() {
		return INSTANCE;
	}

	public void setFog(UUID domain, int color, float density) {
		fogs.remove(domain);
		fogs.put(domain, new Fog(color, Math.max(0.0F, Math.min(1.0F, density))));
	}

	public void clearFog(UUID domain) {
		fogs.remove(domain);
	}

	public void setSky(UUID domain, String id) {
		skies.remove(domain);
		skies.put(domain, id);
	}

	public void clearSky(UUID domain) {
		skies.remove(domain);
	}

	public void clearDomain(UUID domain) {
		fogs.remove(domain);
		skies.remove(domain);
	}

	public void clearAll() {
		fogs.clear();
		skies.clear();
	}

	public boolean hasFog() {
		return !fogs.isEmpty();
	}

	private Fog current() {
		Fog last = null;
		for (Fog fog : fogs.values()) last = fog;
		return last;
	}

	public int fogColor() {
		Fog fog = current();
		return fog == null ? 0 : fog.color();
	}

	public float fogDensity() {
		Fog fog = current();
		return fog == null ? 0.0F : fog.density();
	}

	/** Blends the vanilla fog colour towards the override colour by its density. */
	public void applyFogColor(Vector4f fogColor) {
		Fog fog = current();
		if (fog == null) return;
		float r = ((fog.color() >> 16) & 0xFF) / 255.0F;
		float g = ((fog.color() >> 8) & 0xFF) / 255.0F;
		float b = (fog.color() & 0xFF) / 255.0F;
		float t = Math.min(1.0F, fog.density() * 1.5F);
		fogColor.set(fogColor.x + (r - fogColor.x) * t, fogColor.y + (g - fogColor.y) * t, fogColor.z + (b - fogColor.z) * t, fogColor.w);
	}

	/** True while a domain asked for a custom sky: the vanilla sky is not rendered. */
	public boolean suppressSky() {
		return !skies.isEmpty();
	}

	public String sky() {
		String last = "default";
		for (String sky : skies.values()) last = sky;
		return last;
	}
}
