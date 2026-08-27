package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedControllerNamedEventBus;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEvent;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventBus;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventSource;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketNetwork;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

// Connect shared Named Events to the optional ComputerCraft wireless rednet network
public final class ComputerCraftRednetEventBridge implements AdvancedControllerNamedEventBus.ExternalBridge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();

    // ComputerCraft rednet broadcast channel
    private static final int REDNET_BROADCAST_CHANNEL = 65535;

    // ComputerCraft rednet computer id channel count
    private static final int REDNET_ID_CHANNELS = 65500;

    // Rednet duplicate lifetime in server ticks
    private static final long REDNET_DUPLICATE_TICKS = 200L;

    // Maximum retained rednet duplicate ids for one controller
    private static final int MAXIMUM_DUPLICATE_IDS = 512;

    // Next rednet message id
    private static final AtomicInteger NEXT_MESSAGE_ID = new AtomicInteger(1);

    // Installed ComputerCraft rednet event bridge
    private static final ComputerCraftRednetEventBridge INSTANCE = new ComputerCraftRednetEventBridge();
    private static NamedEventBus.Subscription transportSubscription;

    // Registered controller receivers
    private final Map<AdvancedContraptionControllerBlockEntity, ControllerReceiver> receivers =
            new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ComputerCraft rednet event bridge
    private ComputerCraftRednetEventBridge() {
    }

    // Install the ComputerCraft rednet event bridge
    public static void install() {
        AdvancedControllerNamedEventBus.installExternalBridge(INSTANCE);
        if (transportSubscription == null) {
            transportSubscription = NamedEventBus.subscribe("computercraft:rednet",
                    INSTANCE::publish);
        }
    }

    // Register one controller as a rednet receiver
    @Override
    public synchronized void register(AdvancedContraptionControllerBlockEntity controller) {
        if (!valid(controller) || receivers.containsKey(controller)) {
            return;
        }
        PacketNetwork network = wirelessNetwork(controller);
        if (network == null) {
            return;
        }
        ControllerReceiver receiver = new ControllerReceiver(controller, network);
        receivers.put(controller, receiver);
        network.addReceiver(receiver);
    }

    // Unregister one controller rednet receiver
    @Override
    public synchronized void unregister(AdvancedContraptionControllerBlockEntity controller) {
        ControllerReceiver receiver = receivers.remove(controller);
        if (receiver == null) {
            return;
        }
        receiver.network.removeReceiver(receiver);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Broadcast one shared Named Event as an ordinary rednet message
    private void publish(NamedEvent event) {
        if (event == null || event.source() == null) {
            return;
        }
        ServerLevel level = event.source().server().getLevel(event.source().dimension());
        if (level == null) {
            return;
        }
        PacketNetwork network = ComputerCraftAPI.getWirelessNetwork(level.getServer());
        int senderId = senderId(event.source());
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("nMessageID", nextMessageId());
        message.put("nRecipient", REDNET_BROADCAST_CHANNEL);
        message.put("nSender", senderId);
        message.put("message", ComputerCraftGraphCodec.plainGraphValue(
                event.data()));
        message.put("sProtocol", event.name());
        network.transmitSameDimension(new Packet(
                REDNET_BROADCAST_CHANNEL,
                Math.floorMod(senderId, REDNET_ID_CHANNELS),
                message,
                new NamedEventSender(level, event.source().position(), event.source().endpointId())), 0.0D);
    }

    // Receive one ordinary rednet broadcast as an ACC Named Event
    private static void receive(ControllerReceiver receiver, Packet packet) {
        AdvancedContraptionControllerBlockEntity controller = receiver.controller;
        if (!valid(controller) || packet == null
                || packet.channel() != REDNET_BROADCAST_CHANNEL
                || packet.sender() instanceof NamedEventSender
                || !(packet.payload() instanceof Map<?, ?> message)) {
            return;
        }
        Object recipient = message.get("nRecipient");
        Object messageId = message.get("nMessageID");
        Object protocol = message.get("sProtocol");
        if (!(recipient instanceof Number recipientNumber)
                || recipientNumber.intValue() != REDNET_BROADCAST_CHANNEL
                || !(messageId instanceof Number idNumber)
                || !Double.isFinite(idNumber.doubleValue())
                || !(protocol instanceof String rawName)) {
            return;
        }
        Object sender = message.get("nSender");
        String senderKey = sender instanceof Number senderNumber
                && Double.isFinite(senderNumber.doubleValue())
                ? Double.toString(senderNumber.doubleValue())
                : packet.sender().getSenderID();
        if (!receiver.accept(senderKey, idNumber.doubleValue())) {
            return;
        }
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > 128) {
            return;
        }
        try {
            AdvancedGraphDocument.Value data = GraphRuntime.fromLibraryValue(
                    ComputerCraftGraphCodec.plainRuntimeValue(
                            message.containsKey("message") ? message.get("message") : 0.0D));
            MinecraftServer server = controller.getLevel().getServer();
            if (server != null) {
                server.execute(() -> {
                    if (valid(controller)) {
                        controller.receiveNamedControllerEvent(name, data);
                        NamedEventSource source = source(packet, controller);
                        UUID eventId = UUID.nameUUIDFromBytes((senderKey + ":" + idNumber.doubleValue())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        NamedEventBus.publish(new NamedEvent(eventId, source, name,
                                GraphRuntime.toLibraryValue(data), 0,
                                Set.of("gadgetsandgizmos:acc", "computercraft:rednet")));
                    }
                });
            }
        } catch (LuaException | RuntimeException err) {
            LOGGER.warn("[G&G][ACC] Ignored invalid rednet Named Event '{}'", name, err);
        }
    }

    // Get the ComputerCraft wireless packet network
    private static PacketNetwork wirelessNetwork(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return null;
        }
        return ComputerCraftAPI.getWirelessNetwork(controller.getLevel().getServer());
    }

    // Check if the controller can participate in rednet
    private static boolean valid(AdvancedContraptionControllerBlockEntity controller) {
        return controller != null && !controller.isRemoved()
                && controller.getLevel() != null && !controller.getLevel().isClientSide
                && controller.getLevel().getServer() != null;
    }

    // Get the synthetic rednet sender id for one shared named event source
    private static int senderId(NamedEventSource source) {
        int hash = 31 * source.dimension().location().hashCode() + source.endpointId().hashCode();
        return -1 - Math.floorMod(hash, Integer.MAX_VALUE - 1);
    }

    // Build a shared named event source from a physical rednet packet sender
    private static NamedEventSource source(Packet packet,
                                            AdvancedContraptionControllerBlockEntity fallback) {
        Level level = packet.sender().getLevel();
        if (level != null && level.getServer() != null) {
            return new NamedEventSource(level.getServer(), level.dimension(),
                    packet.sender().getPosition(), "computercraft:rednet/" + packet.sender().getSenderID());
        }
        return new NamedEventSource(fallback.getLevel().getServer(), fallback.getLevel().dimension(),
                fallback.namedControllerEventPosition(), "computercraft:rednet/unknown");
    }

    // Get the next positive rednet message id
    private static int nextMessageId() {
        return NEXT_MESSAGE_ID.getAndUpdate(current ->
                current == Integer.MAX_VALUE ? 1 : current + 1);
    }

    // Send one packet from a shared named event source position
    private record NamedEventSender(
            Level level,
            Vec3 position,
            String endpointId
    ) implements PacketSender {
        // Get the controller level
        @Override
        public Level getLevel() {
            return level;
        }

        // Get the controller position
        @Override
        public Vec3 getPosition() {
            return position == null ? Vec3.atCenterOf(net.minecraft.core.BlockPos.ZERO) : position;
        }

        // Get the controller sender id
        @Override
        public String getSenderID() {
            return "gadgetsandgizmos:named_event/" + endpointId;
        }
    }

    // Receive rednet packets at one ACC controller position
    private static final class ControllerReceiver implements PacketReceiver {
        // Advanced controller
        private final AdvancedContraptionControllerBlockEntity controller;

        // Registered wireless network
        private final PacketNetwork network;

        // Recently received rednet message ids
        private final Map<MessageKey, Long> receivedMessages = new LinkedHashMap<>();

        // Initialize the controller receiver
        private ControllerReceiver(AdvancedContraptionControllerBlockEntity controller,
                                   PacketNetwork network) {
            this.controller = controller;
            this.network = network;
        }

        // Get the controller level
        @Override
        public Level getLevel() {
            return controller.getLevel();
        }

        // Get the controller position
        @Override
        public Vec3 getPosition() {
            return controller.namedControllerEventPosition();
        }

        // Get the controller rednet range
        @Override
        public double getRange() {
            return 0.0D;
        }

        // Check if this controller is an interdimensional rednet receiver
        @Override
        public boolean isInterdimensional() {
            return false;
        }

        // Receive one same-dimension rednet packet
        @Override
        public void receiveSameDimension(Packet packet, double distance) {
            receive(this, packet);
        }

        // Receive one different-dimension rednet packet
        @Override
        public void receiveDifferentDimension(Packet packet) {
        }

        // Accept one rednet message id once during its duplicate lifetime
        private synchronized boolean accept(String sender, double messageId) {
            long gameTime = controller.getLevel().getGameTime();
            Iterator<Map.Entry<MessageKey, Long>> iterator = receivedMessages.entrySet().iterator();
            while (iterator.hasNext()) {
                if (gameTime - iterator.next().getValue() > REDNET_DUPLICATE_TICKS) {
                    iterator.remove();
                }
            }
            MessageKey key = new MessageKey(sender, messageId);
            if (receivedMessages.containsKey(key)) {
                return false;
            }
            receivedMessages.put(key, gameTime);
            while (receivedMessages.size() > MAXIMUM_DUPLICATE_IDS) {
                Iterator<MessageKey> keys = receivedMessages.keySet().iterator();
                keys.next();
                keys.remove();
            }
            return true;
        }
    }

    // Identify one rednet message from one sender
    private record MessageKey(String sender, double messageId) {
    }
}
