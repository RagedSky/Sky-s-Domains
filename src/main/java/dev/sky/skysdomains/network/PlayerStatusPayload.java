package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: HUD state (energy, cooldown, remaining time of the own domain). */
public record PlayerStatusPayload(float energy, float maxEnergy, int cooldownTicks, int ownDomainRemainingTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlayerStatusPayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStatusPayload> CODEC = CustomPacketPayload.codec(PlayerStatusPayload::write, PlayerStatusPayload::new);

	public PlayerStatusPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeFloat(energy);
		buf.writeFloat(maxEnergy);
		buf.writeVarInt(cooldownTicks);
		buf.writeVarInt(ownDomainRemainingTicks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
