package dev.nookofthedead.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import dev.nookofthedead.NookOfTheDead;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The add-on's headless tests ({@code .\tools\gradle.ps1 runGameTestServer}; the build fails if any does), by theme:
 * {@link TownTests} (the ground, the lots, the buildings and their markers) and {@link PlagueTests} (the runners, the
 * infection). Arena: {@code nookofthedead:arena}, Nookworks' own - 7 x 10 x 7, a stone floor at y 0, open above.
 */
public final class ZwGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, NookOfTheDead.MODID);
    private static final Map<String, Integer> TIMEOUTS = new HashMap<>();

    static {
        TownTests.register();
        PlagueTests.register();
        SiegeTests.register();
        SensesTests.register();
        RegionTests.register();
        RegionTests.underground();
        BossTests.register();
        ShelterTests.register();
        PropsTests.register();
        EndingTests.register();
        HardnessTests.register();
        HardnessTests.registerVandal();
        CatTests.register();
        CapitalTests.register();
        RegressionTests.register();
        EndingTests.registerLoopholes();
        ShelterTests.registerTraces();
    }

    public static void register(IEventBus modEventBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ZwGameTests::onRegisterTests);
        Bench.register();
    }

    static void test(String name, Consumer<GameTestHelper> body) {
        FUNCTIONS.register(name, () -> body);
    }

    static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        TIMEOUTS.put(name, maxTicks);
        test(name, body);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(NookOfTheDead.id("default"));
        FUNCTIONS.getEntries().forEach(fn -> {
            ResourceKey<Consumer<GameTestHelper>> key = fn.getKey();
            String name = key.identifier().getPath();
            // the If and the runners' chases each run alone: the If is the server's, and a runner sees far past its arena (and a
            // bell, a scream or a grenade carries far past it: runner_)
            Holder<TestEnvironmentDefinition<?>> batch = name.startsWith("if_") || name.startsWith("runner_") || name.startsWith("mutated_")
                    ? event.registerEnvironment(NookOfTheDead.id(name)) : shared;
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key,
                    new TestData<>(batch, NookOfTheDead.id("arena"), TIMEOUTS.getOrDefault(name, 100), 0, true)));
        });
    }

    /** {@code zombie} with the plain traits (the common pace, sight and breaking), so a test does not hang on a roll. */
    static <T extends net.minecraft.world.entity.monster.zombie.Zombie> T plain(T zombie) {
        dev.nookofthedead.entity.Mind mind = dev.nookofthedead.entity.Mind.of(zombie);
        if (mind != null) {
            mind.set(dev.nookofthedead.entity.Traits.PLAIN);
        }
        return zombie;
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(Component.literal(message));
        }
    }

    /** A player in the test's level, in {@code mode}, taken out again when the test ends (as Nookworks' MockPlayers). */
    static ServerPlayer player(GameTestHelper helper, GameType mode) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        return player;
    }

    static void leave(GameTestHelper helper, ServerPlayer player) {
        MinecraftServer server = helper.getLevel().getServer();
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
            server.getPlayerList().remove(player);
        }
    }

    private ZwGameTests() {}
}
