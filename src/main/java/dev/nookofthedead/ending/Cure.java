package dev.nookofthedead.ending;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookofthedead.NookOfTheDead;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The If cured (the user, 2026-10-19: "最終的にこの世界をゾンビが発生しない（夜になると普通のゾンビやスケルトンは湧くけど、
 * オーバーワールドと同じ挙動に戻る）ようにできる。建物などは戻らないので、この世界の再興はここから始まる"): once the vaccine
 * is let go over it ({@link Ending}), that If is cured for good - no mutated dead spawn in it, no horde, no plague;
 * at night ordinary zombies, skeletons, spiders and creepers come as in the overworld; the dead of it that are left go (one
 * loaded later is gone as it loads). The ruins stay. Kept with the server, for the If of that number: a new If (Nookworks'
 * reset) is fallen again. The clients are told, for the clear air ({@code client/CuredSky}).
 * <p>
 * Only while the console keeps the vaccine going (the user, 2026-10-21: "ワクチン散布は持続散布が必要であり、スイッチをオフに
 * するとまた変異ゾンビが湧く" - a change to a world that cannot be undone is not Minecraft's, and "もう戻せない" takes away
 * the wish to finish): stopped, the If is fallen again until it is started once more ({@link #stopped}).
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Cure extends SavedData {
    private static final Codec<Cure> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("cured_if").forGetter(c -> c.curedIf),
            Codec.BOOL.optionalFieldOf("stopped", false).forGetter(c -> c.stopped)).apply(i, Cure::new));
    public static final SavedDataType<Cure> TYPE = new SavedDataType<>(NookOfTheDead.id("cure"), Cure::new, CODEC);

    /** The number of the If the vaccine was let go over (its film seen); empty for none. */
    private String curedIf;
    /** Whether its console has been stopped since. */
    private boolean stopped;

    private Cure() {
        this("", false);
    }

    private Cure(String curedIf, boolean stopped) {
        this.curedIf = curedIf;
        this.stopped = stopped;
    }

    static Cure get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether the If of {@code server} is cured: the vaccine let go over it, and going out still. */
    public static boolean cured(MinecraftServer server) {
        return released(server) && !get(server).stopped;
    }

    /** Whether the vaccine has ever been let go over the If of {@code server} (its tanks run down, its film seen). */
    public static boolean released(MinecraftServer server) {
        IfWorld world = IfWorld.get(server);
        return world.rolled() && get(server).curedIf.equals(world.number());
    }

    /** Whether {@code level} is the If, cured. On a client, as the server last said. */
    public static boolean cured(Level level) {
        if (!NookOfTheDead.fallen(level)) {
            return false;
        }
        return level instanceof ServerLevel server ? cured(server.getServer()) : clientCured;
    }

    /** Cures the If of {@code server} - the first time, or the console started again - and tells everyone. */
    public static void cure(MinecraftServer server) {
        Cure cure = get(server);
        cure.curedIf = IfWorld.get(server).number();
        cure.stopped = false;
        cure.setDirty();
        tell(server, true);
    }

    /** The console stopped: the If of {@code server} fallen again, till it is started once more. */
    public static void stop(MinecraftServer server) {
        Cure cure = get(server);
        if (released(server) && !cure.stopped) {
            cure.stopped = true;
            cure.setDirty();
            tell(server, false);
        }
    }

    private static void tell(MinecraftServer server, boolean cured) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FilmPayloads.send(player, new FilmPayloads.Cured(cured));
        }
    }

    /** What the clients were told (set by the payload's handler). */
    public static volatile boolean clientCured;

    // what comes at night in a cured If: the overworld's plains' monsters
    private static final List<Weighted<MobSpawnSettings.SpawnerData>> NIGHT = List.of(
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE, net.minecraft.util.valueproviders.UniformInt.of(4, 4)), 95),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE_VILLAGER, net.minecraft.util.valueproviders.UniformInt.of(1, 1)), 5),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.SKELETON, net.minecraft.util.valueproviders.UniformInt.of(4, 4)), 100),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.SPIDER, net.minecraft.util.valueproviders.UniformInt.of(4, 4)), 100),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.CREEPER, net.minecraft.util.valueproviders.UniformInt.of(4, 4)), 100),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.ENDERMAN, net.minecraft.util.valueproviders.UniformInt.of(1, 4)), 10),
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityTypes.WITCH, net.minecraft.util.valueproviders.UniformInt.of(1, 1)), 5));

    @SubscribeEvent
    static void onSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !NookOfTheDead.fallen(level) || !cured(level.getServer())) {
            return;
        }
        if (event.getMobCategory() == MobCategory.MONSTER) {
            for (var spawn : List.copyOf(event.getSpawnerDataList())) {
                event.removeSpawnerData(spawn);
            }
            NIGHT.forEach(event::addSpawnerData);
        }
    }

    /** The plague's dead, loaded into a cured If: gone. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && NookOfTheDead.fallen(level)
                && plague(event.getEntity()) && cured(level.getServer()) && !Ending.staging) {
            event.setCanceled(true);
        }
    }

    /** Whether {@code entity} is one of the plague's: a monster of this mod's (the dead, the animals it took, what grew of it). */
    public static boolean plague(net.minecraft.world.entity.Entity entity) {
        // the dead and everything the plague took - its cows, wolves, horses, camels too (the review, 2026-10-19) - but not the
        // sky base's guards, which are iron, nor the remains
        return entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
                && !(entity instanceof dev.nookofthedead.entity.SentryGolem)
                && !(entity instanceof dev.nookofthedead.entity.Vandal) && !(entity instanceof dev.nookofthedead.entity.SewerBloom)
                && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(NookOfTheDead.MODID);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FilmPayloads.send(player, new FilmPayloads.Cured(cured(player.level().getServer())));
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FilmPayloads.send(player, new FilmPayloads.Cured(cured(player.level().getServer())));
        }
    }
}
