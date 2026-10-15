package dev.sky.skysdomains;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The six domains. The {@code id} is used everywhere in data (components, sequences, lang keys,
 * textures) and must never change once worlds exist.
 */
public enum DomainType {
	FROST_VAULT("frost_vault", "frost_sigil"),
	UMBRAL_SEA("umbral_sea", "umbral_sigil"),
	GLASS_HEAVEN("glass_heaven", "glass_sigil"),
	EMBER_GARDEN("ember_garden", "ember_sigil"),
	STASIS("stasis", "stasis_sigil"),
	STRUCTURAL_COLLAPSE("structural_collapse", "collapse_sigil");

	public static final Codec<DomainType> CODEC = Codec.STRING.xmap(DomainType::byIdOrThrow, DomainType::id);
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainType> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(DomainType::byIdOrThrow, DomainType::id).cast();

	private final String id;
	private final String sigilItemName;

	DomainType(String id, String sigilItemName) {
		this.id = id;
		this.sigilItemName = sigilItemName;
	}

	public String id() {
		return id;
	}

	/** Registry path of the sigil item that inscribes this domain. */
	public String sigilItemName() {
		return sigilItemName;
	}

	public String translationKey() {
		return "domain." + SkysDomains.MOD_ID + "." + id;
	}

	@Nullable
	public static DomainType byId(@Nullable String id) {
		if (id == null) return null;
		String lower = id.toLowerCase(Locale.ROOT);
		for (DomainType type : values()) {
			if (type.id.equals(lower)) return type;
		}
		return null;
	}

	public static DomainType byIdOrThrow(String id) {
		DomainType type = byId(id);
		if (type == null) throw new IllegalArgumentException("Unknown domain type: " + id);
		return type;
	}
}
