package dev.nookofthedead.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.Vandal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Each institute's Vandal kept to one, and kept (the user, 2026-10-23: the refuge's door must open only for its death -
 * "倒してないのに空いちゃうから、いなくなったときはヴァンダルを再び出すのが正しいのでは？…2体になっちゃうか？"):
 * <ul>
 * <li>Its death is written down with the world, by its institute; the door opens for that and nothing else
 *     ({@link RefugeRooms#open}).</li>
 * <li>Gone without dying - moved off by a command or another mod, lost somewhere - and a player come to its hall finds
 *     none: a new one stands where the first did, and the institute's count of Vandals goes up one. Every Vandal knows
 *     its institute and its count ({@link Vandal#home}); one whose count is behind is taken away the moment it is loaded,
 *     or at once if it is about now. So there are never two.</li>
 * <li>Not on Peaceful, where nothing is put down: the Vandal itself stays there, harmless as every mob is on Peaceful
 *     ({@link Vandal#checkDespawn}), and a world turned Peaceful is no longer one with no Vandal.</li>
 * </ul>
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class VandalKeeper {
    /** The Vandals' deaths and counts, by institute ({@link #key}). */
    static final class Kept extends SavedData {
        static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("counts").forGetter(k -> k.counts),
                Codec.STRING.listOf().fieldOf("dead").forGetter(k -> List.copyOf(k.dead))
        ).apply(i, (counts, dead) -> new Kept(new HashMap<>(counts), new HashSet<>(dead))));
        static final SavedDataType<Kept> TYPE = new SavedDataType<>(NookOfTheDead.id("vandals"),
                () -> new Kept(new HashMap<>(), new HashSet<>()), CODEC);
        final Map<String, Integer> counts;
        final Set<String> dead;

        Kept(Map<String, Integer> counts, Set<String> dead) {
            this.counts = counts;
            this.dead = dead;
        }
    }

    static Kept kept(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(Kept.TYPE);
    }

    /** The institute of block ({@code bx}, {@code bz}) in this If. */
    public static String key(ServerLevel level, int bx, int bz) {
        return dev.nookworks.ifworld.IfWorld.get(level.getServer()).number() + ":" + bx + "," + bz;
    }

    /** The institute of the block {@code at} is in. */
    public static String key(ServerLevel level, BlockPos at) {
        return key(level, Math.floorDiv(at.getX(), Town.BLOCK), Math.floorDiv(at.getZ(), Town.BLOCK));
    }

    /** Whether the Vandal of {@code key} is dead. */
    public static boolean dead(ServerLevel level, String key) {
        return kept(level).dead.contains(key);
    }

    /** The count of Vandals {@code key} has had (the first is 0). */
    public static int count(ServerLevel level, String key) {
        return kept(level).counts.getOrDefault(key, 0);
    }

    /** Whether {@code vandal} is behind its institute's count: one that was put back after it went. */
    public static boolean stale(ServerLevel level, Vandal vandal) {
        return vandal.home() != null && vandal.count() < count(level, vandal.home());
    }

    /** A Vandal coming into the fallen world: given its institute if it has none, or taken away if it is behind. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Vandal vandal) || !(event.getLevel() instanceof ServerLevel level)
                || !NookOfTheDead.fallen(level)) {
            return;
        }
        if (vandal.home() == null) {
            String home = key(level, vandal.blockPosition());
            vandal.home(home, count(level, home));
        } else if (stale(level, vandal)) {
            event.setCanceled(true);
            NookOfTheDead.LOGGER.info("A Vandal of {} put back after it went: the old one at {} taken away", vandal.home(),
                    vandal.blockPosition());
        }
    }

    /** Its death written down; then its refuge's door opened. */
    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Vandal vandal) || !(vandal.level() instanceof ServerLevel level)
                || !NookOfTheDead.fallen(level) || stale(level, vandal)) {
            return;
        }
        String home = vandal.home() != null ? vandal.home() : key(level, vandal.blockPosition());
        Kept kept = kept(level);
        kept.dead.add(home);
        kept.setDirty();
        BlockPos block = block(home);
        RefugeRooms.open(level, block != null ? block : vandal.blockPosition());
    }

    /** A block of the institute {@code key} names, its corner; null if it names none. */
    static @Nullable BlockPos block(String key) {
        try {
            String[] xz = key.substring(key.indexOf(':') + 1).split(",");
            return new BlockPos(Integer.parseInt(xz[0]) * Town.BLOCK, 0, Integer.parseInt(xz[1]) * Town.BLOCK);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Whether a Vandal of {@code key} is about now (loaded). */
    static boolean about(ServerLevel level, String key) {
        for (Vandal vandal : level.getEntities(dev.nookofthedead.registry.ZwEntities.VANDAL.get(), v -> v.isAlive())) {
            if (key.equals(vandal.home()) && !stale(level, vandal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Vandal of {@code key}, gone without dying, put back where the first stood (its {@code [vandal]} marker in the
     * deep part placed at {@code under}): the count up one, and any of the old one's still about taken away. Not on
     * Peaceful. Whether one was.
     */
    static boolean putBack(ServerLevel level, String key, StructureTemplate deep, BlockPos under, StructurePlaceSettings settings) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        Markers.Marker sign = Markers.of(deep).stream().filter(m -> m.text().toLowerCase(java.util.Locale.ROOT).startsWith("[vandal"))
                .findFirst().orElse(null);
        if (sign == null) {
            return false;
        }
        BlockPos at = StructureTemplate.calculateRelativePosition(settings, sign.local()).offset(under);
        Vandal vandal = dev.nookofthedead.registry.ZwEntities.VANDAL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (vandal == null) {
            return false;
        }
        Kept kept = kept(level);
        int count = count(level, key) + 1;
        kept.counts.put(key, count);
        kept.setDirty();
        for (Vandal old : level.getEntities(dev.nookofthedead.registry.ZwEntities.VANDAL.get(), v -> key.equals(v.home()))) {
            old.discard();
        }
        vandal.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        vandal.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
        vandal.setPersistenceRequired();
        vandal.home(key, count);
        level.addFreshEntity(vandal);
        NookOfTheDead.LOGGER.info("The Vandal of {} gone without dying: put back at {} (the {} since the first)", key, at, count);
        return true;
    }

    private VandalKeeper() {}
}
