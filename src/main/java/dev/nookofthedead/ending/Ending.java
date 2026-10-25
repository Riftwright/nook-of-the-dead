package dev.nookofthedead.ending;

import java.util.ArrayList;
import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Ascent;
import dev.nookofthedead.world.SkyBase;
import dev.nookofthedead.world.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The end of the If (the user, 2026-10-19: "最深部まで行くと、そこにはワクチンが完成していた。散布型のワクチンで、世界を元に
 * 戻せる…天空基地からワクチンの霧が噴出し、外にいるゾンビが燃え、霧が晴れて美しい世界になったところでスタッフロール、その後
 * 通常プレイに戻る"): the vaccine let go from the laboratory at the top of the Tower. The If is cured at once
 * ({@link Cure}); every dead of the plague there catches fire and is gone within seconds; and each player in the If
 * watches it ({@link Film.Kind#ENDING}): the console's alarm, the mist bursting in the laboratory and pouring off the base,
 * carried away white over the world, coming down over the town by the If's mirror where the dead burn in it and fall, the
 * fog lifting off a clear morning as the camera rises, the monologue telling what happened, the credits - and then back
 * where they stood, to play on (the user, 2026-10-21: the town rather than the second institute's yard; the alarm; a
 * monologue long enough to say what happened).
 * <p>
 * The film plays the first time only: the console stopped and started again ({@link VaccineBlock}) lets the vaccine go
 * again without it ({@link #resume}).
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Ending {
    /** The film's moments (ticks), as the client's camera has them too. */
    public static final int BURST = 60;
    /** Out in the air, round the base. */
    public static final int OUT = 160;
    /** Falling away into the mist, the white filling the screen. */
    public static final int DIVE = 300;
    /** Under the white: the one watching held at the town now, its ground loading round them. */
    public static final int TO_TOWN = 350;
    /** The white lifting off the town. */
    public static final int TOWN = 420;
    public static final int BURN = 460;
    /** The burning dead falling, one after another, till {@link #WHITE}. */
    public static final int FALL = 510;
    public static final int WHITE = 590;
    /**
     * The night, if it is night, turned to morning under the white: the fog lifts on a clear sky (in game, 2026-10-19:
     * at sunrise itself the sky and the fog were orange and the clear air did not read).
     */
    public static final int DAWN = 605;
    /** The white lifting off a clear morning; the camera rising. */
    public static final int RISE = 620;

    /** The dead of the plague set burning, to be gone at a time; the staged ones in the town. */
    private static final List<Burning> BURNING = new ArrayList<>();
    private static final List<Entity> STAGED = new ArrayList<>();

    private record Burning(Entity entity, long until) {}

    /** True while the town's dead are put down: the cured If lets these in ({@link Cure}), and no others. */
    static boolean staging;

    /** The game tick the film's doings in the world were last done: once a tick for all who watch. */
    private static long done = Long.MIN_VALUE;

    /** The vaccine let go at {@code vaccine} by {@code by}, the first time: cured, and the film. */
    public static void start(ServerLevel level, ServerPlayer by, BlockPos vaccine) {
        Cure.cure(level.getServer());
        burnAll(level);
        Ascent.Site site = Ascent.site(level.getSeed());
        BlockPos c = Tower.centre(site);
        List<Vec3> town = town(level);
        List<Vec3> anchors = List.of(
                Vec3.atBottomCenterOf(vaccine),                                        // 0: the vaccine
                new Vec3(c.getX() + 0.5, Tower.BASE + 1, c.getZ() + 0.5),               // 1: the base
                town.get(0),                                                          // 2: the town, by the If's mirror
                town.get(1),                                                          // 3: where the camera comes down there
                town.get(2),                                                          // 4: and where it goes, round it
                well(level, town.get(0)),                                             // 5: a shelter's well near it (or NONE)
                orNone(Opening.camp(level, town.get(0), Opening.CAMP_REACH)));         // 6: a holdout's roof near it (or NONE)
        for (ServerPlayer player : level.players()) {
            if (player.isAlive()) {
                Film.start(player, Film.Kind.ENDING, anchors);
                // held at the base, so what the first shots show is loaded round them (the review, 2026-10-19: a player
                // far off saw empty sky)
                Film.holdAt(player, anchors.get(0).add(0, 0, 4));
            }
        }
        NookOfTheDead.LOGGER.info("The If is cured ({} let the vaccine go); the film comes down over the town at {}", by.getName().getString(),
                BlockPos.containing(town.get(0)));
    }

    /** The console started again after it was stopped: cured again, at once, without the film. */
    public static void resume(ServerLevel level, ServerPlayer by, BlockPos vaccine) {
        Cure.cure(level.getServer());
        burnAll(level);
        level.playSound(null, vaccine, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD, vaccine.getX() + 0.5, vaccine.getY() + 1.5, vaccine.getZ() + 0.5, 80, 2.0, 1.5, 2.0, 0.04);
        NookOfTheDead.LOGGER.info("The vaccine goes out again ({} started the console)", by.getName().getString());
    }

    /** Every dead of the plague in the If: alight, and gone in a little while. */
    private static void burnAll(ServerLevel level) {
        long now = level.getGameTime();
        for (Entity entity : level.getAllEntities()) {
            if (Cure.plague(entity)) {
                entity.igniteForSeconds(8);
                BURNING.add(new Burning(entity, now + 60 + level.getRandom().nextInt(80)));
            }
        }
    }

    /**
     * The town's shot: by the If's mirror, where everyone first came in (the world's spawn if it has none) - the ground
     * there; and two places for the camera, the same distance out on the most open side, so it looks in over the
     * streets rather than through a house.
     */
    static List<Vec3> town(ServerLevel level) {
        BlockPos mirror = dev.nookworks.ifworld.IfWorld.get(level.getServer()).ifMirror();
        BlockPos at = mirror != null ? mirror : level.getRespawnData().pos();
        int ground = mirror != null ? at.getY() : height(level, at.getX(), at.getZ());
        Vec3 m = new Vec3(at.getX() + 0.5, ground, at.getZ() + 0.5);
        double best = Double.MAX_VALUE;
        double heading = 0;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            double over = Math.max(over(level, m, a), over(level, m, a + ARC));
            if (over < best) {
                best = over;
                heading = a;
            }
        }
        double y = Math.max(best + 3, m.y + 7);
        return List.of(m, m.add(Math.cos(heading) * OUT_FROM, y - m.y, Math.sin(heading) * OUT_FROM),
                m.add(Math.cos(heading + ARC) * OUT_FROM, y - m.y, Math.sin(heading + ARC) * OUT_FROM));
    }

    /** An anchor there is nothing for: under the world. */
    public static final Vec3 NONE = new Vec3(0, -1000, 0);

    private static Vec3 orNone(Vec3 v) {
        return v != null ? v : NONE;
    }

    /**
     * The rim of the nearest shelter's well within {@link Opening#CAMP_REACH} of {@code at}, or {@link #NONE} (the user,
     * 2026-10-24: the credits going back over what the town left - the well they went down, a roof they held).
     */
    static Vec3 well(ServerLevel level, Vec3 at) {
        int bx0 = Math.floorDiv((int) at.x, dev.nookofthedead.world.Town.BLOCK);
        int bz0 = Math.floorDiv((int) at.z, dev.nookofthedead.world.Town.BLOCK);
        Vec3 best = NONE;
        for (int bx = bx0 - 1; bx <= bx0 + 1; bx++) {
            for (int bz = bz0 - 1; bz <= bz0 + 1; bz++) {
                var shelter = dev.nookofthedead.world.Shelters.at(level.getSeed(), bx, bz);
                // there in the world: its roof's post (not on ground made before the shelters were, 0.3 and before)
                if (shelter.isPresent() && level.getBlockState(shelter.get().well().atY(shelter.get().top() + 2).east())
                        .is(net.minecraft.world.level.block.Blocks.SPRUCE_FENCE)) {
                    Vec3 v = Vec3.atBottomCenterOf(shelter.get().well().atY(shelter.get().top() + 1));
                    if (v.distanceTo(at) < Opening.CAMP_REACH && v.distanceTo(at) < best.distanceTo(at)) {
                        best = v;
                    }
                }
            }
        }
        return best;
    }

    /** How far out the town's camera stands, and how far round it goes. */
    static final double OUT_FROM = 16;
    static final double ARC = 0.45;

    /** The highest ground between the town's middle and a camera out along {@code heading}. */
    private static double over(ServerLevel level, Vec3 m, double heading) {
        int top = (int) m.y;
        for (int d = 2; d <= OUT_FROM + 2; d += 2) {
            top = Math.max(top, height(level, (int) Math.floor(m.x + Math.cos(heading) * d), (int) Math.floor(m.z + Math.sin(heading) * d)));
        }
        return top;
    }

    /**
     * The ground's height at ({@code x}, {@code z}), its chunk made first (in game, 2026-10-21: a chunk not loaded reads as
     * the bottom of the world, and the town's shot was set in the void).
     */
    private static int height(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** One moment of the film for {@code player}: the burst; to the town; the dead there set alight, and falling. */
    static void at(ServerPlayer player, Film.Session session) {
        ServerLevel level = player.level();
        int t = session.tick;
        if (t == TO_TOWN) {
            Film.holdAt(player, session.anchors.get(2).add(0, 1, 0));    // each one watching, held at the town
        }
        if (level.getGameTime() == done) {
            return;                                                      // the world's part done this tick already
        }
        done = level.getGameTime();
        if (t == BURST) {
            Vec3 v = session.anchors.get(0);
            level.playSound(null, BlockPos.containing(v), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.6F);
            level.playSound(null, BlockPos.containing(v), SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 2.0F, 0.5F);
            level.sendParticles(ParticleTypes.CLOUD, v.x, v.y + 1.5, v.z, 200, 3.0, 2.0, 3.0, 0.05);
        }
        if (t >= BURST + 20 && t <= BURST + 68 && (t - BURST - 20) % 12 == 0) {
            SkyBase.drain(level, (t - BURST - 20) / 12);                 // the vessel running down as the camera pulls back
        }
        if (t == TO_TOWN) {
            SkyBase.drain(level, 99);
        } else if (t == TO_TOWN + 10 && STAGED.isEmpty()) {
            stage(level, session.anchors.get(2), session.anchors.get(3));
        } else if (t == BURN) {
            for (Entity dead : STAGED) {
                dead.igniteForSeconds(12);
            }
        } else if (t > FALL && t < WHITE && !STAGED.isEmpty() && (t - FALL) % 6 == 0) {
            Entity dead = STAGED.remove(0);                              // one after another, as each burns out
            dead.kill(level);
        } else if (t == DAWN) {
            long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
            if (day >= 12500 || day < 500) {
                var server = level.getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
                        "time set 1000");
            }
        } else if (t == WHITE + 10) {
            gone(level);
        }
    }

    /**
     * The plague's own dead out in the town's streets about {@code m} (the visual review, 2026-10-19: plain zombies were not
     * the If's), on the ground near its level, on the side the camera looks in from.
     */
    private static void stage(ServerLevel level, Vec3 m, Vec3 camera) {
        var random = level.getRandom();
        double facing = Math.atan2(camera.z - m.z, camera.x - m.x);
        for (int tries = 0; tries < 60 && STAGED.size() < 14; tries++) {
            double a = facing + Math.PI + (random.nextDouble() - 0.5) * 2.4;
            double r = 3 + random.nextDouble() * 9;
            double x = m.x + Math.cos(a) * r;
            double z = m.z + Math.sin(a) * r;
            int y = height(level, (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(y - m.y) > 2) {
                continue;                                               // on a roof, or down a hole: not in the street
            }
            Zombie dead = (STAGED.size() % 4 == 3 ? dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE_VILLAGER.get()
                    : dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get()).create(level, EntitySpawnReason.TRIGGERED);
            if (dead != null) {
                dead.snapTo(x, y, z, random.nextFloat() * 360, 0);
                dead.setPersistenceRequired();
                dead.addTag(STAGED_TAG);
                staging = true;
                try {
                    level.addFreshEntity(dead);
                } finally {
                    staging = false;
                }
                STAGED.add(dead);
            }
        }
    }

    /** What marks the film's own dead: nothing of theirs is left when they fall. */
    static final String STAGED_TAG = "nookofthedead_film";

    @SubscribeEvent
    static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().entityTags().contains(STAGED_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onExperience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().entityTags().contains(STAGED_TAG)) {
            event.setCanceled(true);
        }
    }

    /** The staged dead, any left, gone. */
    private static void gone(ServerLevel level) {
        for (Entity dead : STAGED) {
            if (!dead.isRemoved()) {
                dead.kill(level);
            }
        }
        STAGED.clear();
    }

    /** The server stopping: the town's dead gone with it, nothing kept of this world. */
    static void forget(net.minecraft.server.MinecraftServer server) {
        for (Entity dead : STAGED) {
            if (!dead.isRemoved() && dead.level() instanceof ServerLevel level) {
                dead.kill(level);
            }
        }
        STAGED.clear();
        BURNING.clear();
    }

    /** Esc during the ending: what it was to do, done (the dead in the town gone; the rest burn on). */
    static void skipped(ServerLevel level) {
        SkyBase.drain(level, 99);
        gone(level);
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !NookOfTheDead.fallen(level) || BURNING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        BURNING.removeIf(b -> {
            if (b.entity.isRemoved()) {
                return true;
            }
            if (now >= b.until) {
                b.entity.kill(level);
                return true;
            }
            if (b.entity.getRemainingFireTicks() < 20) {
                b.entity.igniteForSeconds(4);
            }
            return false;
        });
    }

    private Ending() {}
}
