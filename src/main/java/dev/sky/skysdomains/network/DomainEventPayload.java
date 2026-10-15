package dev.sky.skysdomains.network;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Server -> client: a named event inside a domain, e.g. {@code planet_launch} (a = planet index,
 * b = flight ticks, pos = target), {@code impact} (a = planet index, 8 = sun), {@code shatter}
 * (a projectile broke at pos), {@code clash} (pos = other domain centre), {@code center_hit}.
 * The client resolves the event name against the {@code events} section of the sequence file.
 */
public record DomainEventPayload(UUID id, String event, Vec3 pos, int a, int b) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DomainEventPayload> TYPE = new CustomPacketPayload.Type<>(SkysDomains.id("domain_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainEventPayload> CODEC = CustomPacketPayload.codec(DomainEventPayload::write, DomainEventPayload::new);

	public static final String PLANET_LAUNCH = "planet_launch";
	public static final String IMPACT = "impact";
	public static final String SUN_LAUNCH = "sun_launch";
	public static final String SUN_IMPACT = "sun_impact";
	public static final String SHATTER = "shatter";
	public static final String CLASH = "clash";
	public static final String CENTER_HIT = "center_hit";

	public DomainEventPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readUtf(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(id);
		buf.writeUtf(event);
		buf.writeDouble(pos.x);
		buf.writeDouble(pos.y);
		buf.writeDouble(pos.z);
		buf.writeVarInt(a);
		buf.writeVarInt(b);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
