package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server -> client: the domain entered phase {@code phaseIndex}. */
public record DomainPhasePayload(UUID id, int phaseIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DomainPhasePayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("domain_phase"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainPhasePayload> CODEC = CustomPacketPayload.codec(DomainPhasePayload::write, DomainPhasePayload::new);

	public DomainPhasePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(id);
		buf.writeVarInt(phaseIndex);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
