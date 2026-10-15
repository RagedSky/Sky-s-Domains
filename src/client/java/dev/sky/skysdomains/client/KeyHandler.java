package dev.sky.skysdomains.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.common.TalismanItem;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.network.ActivateDomainPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLScancode;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * The activation key (default R, configurable in the config file and in the vanilla controls
 * screen). No right click is used, so there is no conflict with blocks or food.
 */
public final class KeyHandler {
	public static final String KEY_ACTIVATE = "key." + SkysDomains.MOD_ID + ".activate";
	private static KeyMapping activate;

	private KeyHandler() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(SkysDomains.id("domains"));
		activate = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_ACTIVATE, InputConstants.Type.KEYBOARD,
				scancode(ConfigManager.get().keybindDefault), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			while (activate.consumeClick()) {
				if (!(client.player.getOffhandItem().getItem() instanceof TalismanItem)) {
					client.player.sendOverlayMessage(Component.translatable("message.skysdomains.no_talisman"));
					continue;
				}
				ClientPlayNetworking.send(ActivateDomainPayload.INSTANCE);
			}
		});
	}

	/** Maps a config key name ("R", "G", "LEFTBRACKET", ...) to an SDL scancode, falling back to R. */
	private static int scancode(String name) {
		String field = "SDL_SCANCODE_" + (name == null ? "R" : name.trim().toUpperCase(Locale.ROOT));
		try {
			Field f = SDLScancode.class.getField(field);
			return f.getInt(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			SkysDomains.LOGGER.warn("Unknown key '{}' in config, using R", name);
			return SDLScancode.SDL_SCANCODE_R;
		}
	}
}
