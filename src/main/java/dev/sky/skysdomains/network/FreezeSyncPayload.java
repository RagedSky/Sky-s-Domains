package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: entity ids that became frozen / unfrozen. */
public record FreezeSyncPayload(List<Integer> entityIds, boolean frozen) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FreezeSyncPayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("freeze_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FreezeSyncPayload> CODEC = CustomPacketPayload.codec(FreezeSyncPayload::write, FreezeSyncPayload::new);

	public FreezeSyncPayload(RegistryFriendlyByteBuf buf) {
		this(readIds(buf), buf.readBoolean());
	}

	private static List<Integer> readIds(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<Integer> ids = new ArrayList<>(count);
		for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
		return ids;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityIds.size());
		for (int id : entityIds) buf.writeVarInt(id);
		buf.writeBoolean(frozen);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
