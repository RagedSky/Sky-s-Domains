package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the activation key was pressed. */
public final class ActivateDomainPayload implements CustomPacketPayload {
	public static final ActivateDomainPayload INSTANCE = new ActivateDomainPayload();
	public static final CustomPacketPayload.Type<ActivateDomainPayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("activate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ActivateDomainPayload> CODEC = StreamCodec.unit(INSTANCE);

	private ActivateDomainPayload() {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
