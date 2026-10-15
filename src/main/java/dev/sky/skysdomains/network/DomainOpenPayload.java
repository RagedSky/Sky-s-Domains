package dev.sky.skysdomains.network;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.domain.DomainPhase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: a domain exists. Also sent to players that come into range later, with the
 * current phase index and phase age so they join the animation mid-way.
 */
public record DomainOpenPayload(UUID id, int ownerEntityId, DomainType domainType, int tierLevel, int refinements,
								Vec3 center, double radius, List<DomainPhase> phases,
								int phaseIndex, int phaseAge, int planets, boolean sunAtEnd) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DomainOpenPayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("domain_open"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainOpenPayload> CODEC = CustomPacketPayload.codec(DomainOpenPayload::write, DomainOpenPayload::new);

	public DomainOpenPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readVarInt(), DomainType.byIdOrThrow(buf.readUtf()), buf.readVarInt(), buf.readVarInt(),
				new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readDouble(), readPhases(buf),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}

	private static List<DomainPhase> readPhases(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<DomainPhase> phases = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			phases.add(new DomainPhase(buf.readUtf(), buf.readVarInt()));
		}
		return phases;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(id);
		buf.writeVarInt(ownerEntityId);
		buf.writeUtf(domainType.id());
		buf.writeVarInt(tierLevel);
		buf.writeVarInt(refinements);
		buf.writeDouble(center.x);
		buf.writeDouble(center.y);
		buf.writeDouble(center.z);
		buf.writeDouble(radius);
		buf.writeVarInt(phases.size());
		for (DomainPhase phase : phases) {
			buf.writeUtf(phase.name());
			buf.writeVarInt(phase.ticks());
		}
		buf.writeVarInt(phaseIndex);
		buf.writeVarInt(phaseAge);
		buf.writeVarInt(planets);
		buf.writeBoolean(sunAtEnd);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
