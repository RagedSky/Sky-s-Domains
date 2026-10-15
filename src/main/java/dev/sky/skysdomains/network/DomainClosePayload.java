package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server -> client: the domain is gone. {@code collapsed} = ended early (clash / owner left). */
public record DomainClosePayload(UUID id, boolean collapsed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DomainClosePayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("domain_close"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainClosePayload> CODEC = CustomPacketPayload.codec(DomainClosePayload::write, DomainClosePayload::new);

	public DomainClosePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(id);
		buf.writeBoolean(collapsed);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
