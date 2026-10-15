package dev.sky.skysdomains.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Scene textures are stitched into the block atlas (see {@code assets/minecraft/atlases/blocks.json}
 * which adds the {@code textures/scene} directory as sprite source), so they can be drawn with the
 * vanilla moving-block render types. Sprite ids look like {@code skysdomains:scene/planet_mars}.
 */
public final class DomainSprites {
	private static final Map<Identifier, TextureAtlasSprite> CACHE = new HashMap<>();

	private DomainSprites() {
	}

	public static TextureAtlasSprite get(Identifier spriteId) {
		TextureAtlasSprite sprite = CACHE.get(spriteId);
		if (sprite == null) {
			sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, spriteId));
			CACHE.put(spriteId, sprite);
		}
		return sprite;
	}

	public static void invalidate() {
		CACHE.clear();
	}
}
