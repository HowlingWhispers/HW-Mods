package buildcraft.lib.compat.howl;

import buildcraft.lib.net.BCPacketContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Native payload registration boundary; never substitutes BuildCraft messages. */
public final class NativeNetworkAccess {
    public interface Registrar {
        <T extends CustomPacketPayload> void register(String protocol,
                CustomPacketPayload.Type<T> type,
                StreamCodec<RegistryFriendlyByteBuf, T> codec,
                BiConsumer<T, BCPacketContext> handler);
    }

    private static boolean registered;

    private NativeNetworkAccess() {}

    public static synchronized <T extends CustomPacketPayload> void register(
            Registrar registrar, String protocol, CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, BCPacketContext> handler) {
        if (registered) throw new IllegalStateException("BuildCraft payload already registered");
        Objects.requireNonNull(registrar, "H.O.W.L. native payload registrar")
                .register(protocol, type, codec, handler);
        registered = true;
    }

    private static synchronized void requireRegistered() {
        if (!registered) throw new IllegalStateException(
                "H.O.W.L. must register the original BuildCraft payload before networking");
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        requireRegistered();
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        requireRegistered();
        var server = NativeLifecycleAccess.getCurrentServer();
        if (server == null) throw new IllegalStateException("No running Minecraft server");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendToPlayer(player, payload);
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        requireRegistered();
        for (ServerPlayer player : level.players()) sendToPlayer(player, payload);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos pos,
            CustomPacketPayload payload) {
        requireRegistered();
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false))
            sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        requireRegistered();
        ClientSender.send(payload);
    }

    /** Keep dedicated servers from resolving Minecraft's client singleton. */
    private static final class ClientSender {
        static void send(CustomPacketPayload payload) {
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection == null) throw new IllegalStateException("No Minecraft client connection");
            connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
        }
    }
}
