package com.rieno.gadgetsandgizmos.compat.synaxis;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEvent;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventBus;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventSource;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentConfig;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentMemory;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentRegistry;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSemantics;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentType;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentTypeId;
import com.verr1.synaxis.foundation.cimulink.core.component.EvalContext;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.InputPort;
import com.verr1.synaxis.foundation.cimulink.core.signal.OutputPort;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalReader;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalWriter;
import com.verr1.synaxis.foundation.cimulink.game.circuit.ComponentConfigCodecs;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointAddress;
import com.verr1.synaxis.foundation.cimulink.game.component.BusPortSpec;
import com.verr1.synaxis.foundation.cimulink.game.component.BusSignalMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Connect Synaxis numeric Named Event nodes to the shared G&G transport bus
public final class SynaxisNamedEventCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ComponentTypeId SEND_ID = ComponentTypeId.of("gadgetsandgizmos:named_event_send");
    public static final ComponentTypeId RECEIVE_ID = ComponentTypeId.of("gadgetsandgizmos:named_event_receive");
    private static final BusPortSpec VALUE_PORT = new BusPortSpec("named_event", "value", BusSignalMode.REAL);
    private static final BusPortSpec PULSE_PORT = new BusPortSpec("named_event", "pulse", BusSignalMode.BOOLEAN);
    public static final InputPort SEND_VALUE = new InputPort(VALUE_PORT.wireName(
            com.verr1.synaxis.foundation.cimulink.core.signal.PortDirection.INPUT));
    public static final InputPort SEND_PULSE = new InputPort(PULSE_PORT.wireName(
            com.verr1.synaxis.foundation.cimulink.core.signal.PortDirection.INPUT));
    public static final OutputPort RECEIVE_VALUE = new OutputPort(VALUE_PORT.wireName(
            com.verr1.synaxis.foundation.cimulink.core.signal.PortDirection.OUTPUT));
    public static final OutputPort RECEIVE_PULSE = new OutputPort(PULSE_PORT.wireName(
            com.verr1.synaxis.foundation.cimulink.core.signal.PortDirection.OUTPUT));
    private static final String TRANSPORT_ID = "synaxis:cimulink";
    private static final ComponentSchema SEND_SCHEMA = ComponentSchema.of(
            java.util.List.of(PortDef.input(SEND_VALUE.name(), SignalType.REAL),
                    PortDef.input(SEND_PULSE.name(), SignalType.BOOLEAN)), java.util.List.of());
    private static final ComponentSchema RECEIVE_SCHEMA = ComponentSchema.of(java.util.List.of(),
            java.util.List.of(PortDef.output(RECEIVE_VALUE.name(), SignalType.REAL),
                    PortDef.output(RECEIVE_PULSE.name(), SignalType.BOOLEAN)));
    private static final Map<ReceiveMemory, Receiver> RECEIVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static NamedEventBus.Subscription transportSubscription;
    private static boolean installed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Synaxis Named Event bridge
    private SynaxisNamedEventCompat() {
    }

    // Register the optional Synaxis component types and their persistent configuration codecs
    public static synchronized void install(ComponentRegistry registry) {
        if (installed || registry == null) {
            return;
        }
        ComponentConfigCodecs.register(SEND_ID, SynaxisNamedEventConfig.CODEC,
                () -> new SynaxisNamedEventConfig(""));
        ComponentConfigCodecs.register(RECEIVE_ID, SynaxisNamedEventConfig.CODEC,
                () -> new SynaxisNamedEventConfig(""));
        registry.register(new SendComponent());
        registry.register(new ReceiveComponent());
        transportSubscription = NamedEventBus.subscribe(TRANSPORT_ID,
                SynaxisNamedEventCompat::receive);
        installed = true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Deliver one shared event to every matching active Synaxis receive component
    private static void receive(NamedEvent event) {
        if (event == null) {
            return;
        }
        synchronized (RECEIVERS) {
            RECEIVERS.entrySet().removeIf(entry -> {
                ReceiveMemory memory = entry.getKey();
                Receiver receiver = entry.getValue();
                if (memory == null || receiver == null) {
                    return true;
                }
                if (receiver.matches(event)) {
                    memory.offer(event.data().asNumber());
                }
                return false;
            });
        }
    }

    // Register or refresh the endpoint associated with one live receive component
    private static void register(ReceiveMemory memory, SynaxisNamedEventConfig config,
                                 EvalContext context) {
        source(context).ifPresent(source -> RECEIVERS.put(memory,
                new Receiver(source.server(), source.dimension(), source.position(), source.endpointId(), config.name())));
    }

    // Resolve the server-authoritative source represented by the current Synaxis endpoint
    private static java.util.Optional<NamedEventSource> source(EvalContext context) {
        if (context == null || context.domain() != ExecutionDomain.GAME_TICK) {
            return java.util.Optional.empty();
        }
        return context.owner().filter(owner -> owner.present()).flatMap(owner -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            EndpointAddress address = owner.address();
            if (server == null || address == null) {
                return java.util.Optional.empty();
            }
            ServerLevel level = server.getLevel(address.dimension());
            if (level == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new NamedEventSource(server, address.dimension(),
                    Vec3.atCenterOf(address.pos()), "synaxis/" + owner.endpointId()));
        });
    }

    // Read the named event configuration supplied by the visual node adapter
    private static SynaxisNamedEventConfig config(ComponentConfig config) {
        return config instanceof SynaxisNamedEventConfig named
                ? named : new SynaxisNamedEventConfig("");
    }

    // Publish one rising-edge numeric Synaxis signal as an immutable named event
    private static final class SendComponent implements ComponentType {
        @Override
        public ComponentTypeId id() {
            return SEND_ID;
        }

        @Override
        public ComponentSchema schema(ComponentConfig config) {
            return SEND_SCHEMA;
        }

        @Override
        public ComponentConfig defaultConfig() {
            return new SynaxisNamedEventConfig("");
        }

        @Override
        public ComponentMemory createMemory(ComponentConfig config) {
            return new SendMemory();
        }

        @Override
        public ComponentSemantics semantics(ComponentConfig config) {
            return ComponentSemantics.temporal(SEND_SCHEMA);
        }

        @Override
        public void step(EvalContext context, ComponentConfig componentConfig, ComponentMemory componentMemory,
                         SignalReader reader, SignalWriter writer) {
            if (!(componentMemory instanceof SendMemory memory)) {
                return;
            }
            boolean pulsed = reader.bool(SEND_PULSE);
            SynaxisNamedEventConfig named = config(componentConfig);
            if (pulsed && !memory.pulsed && !named.name().isEmpty()) {
                source(context).ifPresent(source -> NamedEventBus.publish(NamedEvent.of(source,
                        named.name(), GraphValue.number(reader.real(SEND_VALUE)), 0)));
            }
            memory.pulsed = pulsed;
        }
    }

    // Expose one queued named event as a single-tick pulse and a numeric data output
    private static final class ReceiveComponent implements ComponentType {
        @Override
        public ComponentTypeId id() {
            return RECEIVE_ID;
        }

        @Override
        public ComponentSchema schema(ComponentConfig config) {
            return RECEIVE_SCHEMA;
        }

        @Override
        public ComponentConfig defaultConfig() {
            return new SynaxisNamedEventConfig("");
        }

        @Override
        public ComponentMemory createMemory(ComponentConfig config) {
            return new ReceiveMemory();
        }

        @Override
        public ComponentSemantics semantics(ComponentConfig config) {
            return ComponentSemantics.temporal(RECEIVE_SCHEMA);
        }

        @Override
        public void step(EvalContext context, ComponentConfig componentConfig, ComponentMemory componentMemory,
                         SignalReader reader, SignalWriter writer) {
            if (!(componentMemory instanceof ReceiveMemory memory)) {
                return;
            }
            register(memory, config(componentConfig), context);
            Double value = memory.poll();
            writer.real(RECEIVE_VALUE, value == null ? memory.lastValue : value);
            writer.bool(RECEIVE_PULSE, value != null);
            if (value != null) {
                memory.lastValue = value;
            }
        }
    }

    // Store the previous trigger level for one sender instance
    private static final class SendMemory implements ComponentMemory {
        private boolean pulsed;
    }

    // Queue a bounded number of numeric events for one receiver instance
    private static final class ReceiveMemory implements ComponentMemory {
        private static final int MAXIMUM_QUEUED_EVENTS = 32;
        private final java.util.ArrayDeque<Double> values = new java.util.ArrayDeque<>();
        private double lastValue;

        private synchronized void offer(double value) {
            if (values.size() >= MAXIMUM_QUEUED_EVENTS) {
                values.removeFirst();
            }
            values.addLast(value);
        }

        private synchronized Double poll() {
            return values.pollFirst();
        }
    }

    // Identify one active Synaxis endpoint/topic subscription without retaining its component memory
    private record Receiver(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                            Vec3 position, String endpointId, String name) {
        private boolean matches(NamedEvent event) {
            return server == event.source().server() && name.equals(event.name())
                    && (event.maximumDistance() <= 0 || (dimension.equals(event.source().dimension())
                    && event.source().position().distanceToSqr(position)
                    <= event.maximumDistance() * (double) event.maximumDistance()));
        }
    }
}
