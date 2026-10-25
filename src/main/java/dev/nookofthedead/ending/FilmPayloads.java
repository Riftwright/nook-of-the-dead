package dev.nookofthedead.ending;

import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What the server and the clients tell each other of the films ({@link Film}) and the cure ({@link Cure}). The clients'
 * handlers are their own ({@code client/FilmCamera}).
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class FilmPayloads {
    private static final StreamCodec<ByteBuf, Vec3> VEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    /** Whether the If is cured. */
    public record Cured(boolean cured) implements CustomPacketPayload {
        public static final Type<Cured> TYPE = new Type<>(NookOfTheDead.id("cured"));
        public static final StreamCodec<ByteBuf, Cured> CODEC = ByteBufCodecs.BOOL.map(Cured::new, Cured::cured);

        @Override
        public Type<Cured> type() {
            return TYPE;
        }
    }

    /** Play film {@code film} (an ordinal of {@link Film.Kind}) about {@code anchors}; -1 stops whatever plays. */
    public record Play(int film, List<Vec3> anchors) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(NookOfTheDead.id("film"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Play::film,
                VEC.apply(ByteBufCodecs.list(16)), Play::anchors, Play::new);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** The one watching would rather not (Esc). */
    public record Skip() implements CustomPacketPayload {
        public static final Type<Skip> TYPE = new Type<>(NookOfTheDead.id("film_skip"));
        public static final StreamCodec<ByteBuf, Skip> CODEC = StreamCodec.unit(new Skip());

        @Override
        public Type<Skip> type() {
            return TYPE;
        }
    }

    /** The vaccine's console, going, used: the client asks whether to stop it (its own screen). */
    public record AskStop(net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
        public static final Type<AskStop> TYPE = new Type<>(NookOfTheDead.id("vaccine_ask_stop"));
        public static final StreamCodec<ByteBuf, AskStop> CODEC = net.minecraft.core.BlockPos.STREAM_CODEC.map(AskStop::new, AskStop::pos);

        @Override
        public Type<AskStop> type() {
            return TYPE;
        }
    }

    /** And they said yes: stop the console at {@code pos}. */
    public record Stop(net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(NookOfTheDead.id("vaccine_stop"));
        public static final StreamCodec<ByteBuf, Stop> CODEC = net.minecraft.core.BlockPos.STREAM_CODEC.map(Stop::new, Stop::pos);

        @Override
        public Type<Stop> type() {
            return TYPE;
        }
    }

    /** {@code payload} to {@code player}, if their client can take it (one without the mod, a test's mock player, cannot). */
    public static void send(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(Cured.TYPE, Cured.CODEC);
        registrar.playToClient(Play.TYPE, Play.CODEC);
        registrar.playToClient(AskStop.TYPE, AskStop.CODEC);
        registrar.playToServer(Stop.TYPE, Stop.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                VaccineBlock.stop(player, payload.pos());
            }
        }));
        registrar.playToServer(Skip.TYPE, Skip.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                Film.skip(player);
            }
        }));
    }

    private FilmPayloads() {}
}
