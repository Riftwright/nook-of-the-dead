package dev.nookofthedead.registry;

import java.util.function.Supplier;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.entity.MutatedZombieHorse;
import dev.nookofthedead.entity.MutatedZombieVillager;
import dev.nookofthedead.entity.Vandal;
import dev.nookofthedead.entity.ZombieCow;
import dev.nookofthedead.entity.ZombieWolf;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class ZwEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, NookOfTheDead.MODID);

    /** Sized and tracked as vanilla's zombie. */
    public static final Supplier<EntityType<MutatedZombie>> MUTATED_ZOMBIE = ENTITY_TYPES.register("mutated_zombie",
            () -> EntityType.Builder.<MutatedZombie>of(MutatedZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F)
                    .clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("mutated_zombie"))));

    /** Sized and tracked as vanilla's zombie villager. */
    public static final Supplier<EntityType<MutatedZombieVillager>> MUTATED_ZOMBIE_VILLAGER = ENTITY_TYPES.register("mutated_zombie_villager",
            () -> EntityType.Builder.<MutatedZombieVillager>of(MutatedZombieVillager::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).passengerAttachments(2.125F).ridingOffset(-0.7F).eyeHeight(1.74F)
                    .clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("mutated_zombie_villager"))));

    /** The animals the plague took ({@link dev.nookofthedead.entity.Infected}), sized as vanilla's. */
    public static final Supplier<EntityType<ZombieCow>> ZOMBIE_COW = ENTITY_TYPES.register("zombie_cow",
            () -> EntityType.Builder.<ZombieCow>of(ZombieCow::new, MobCategory.MONSTER).sized(0.9F, 1.4F).eyeHeight(1.3F)
                    .passengerAttachments(1.36875F).clientTrackingRange(10).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("zombie_cow"))));
    public static final Supplier<EntityType<ZombieWolf>> ZOMBIE_WOLF = ENTITY_TYPES.register("zombie_wolf",
            () -> EntityType.Builder.<ZombieWolf>of(ZombieWolf::new, MobCategory.MONSTER).sized(0.6F, 0.85F).eyeHeight(0.68F)
                    .passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.81875, -0.0625)).clientTrackingRange(10).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("zombie_wolf"))));
    public static final Supplier<EntityType<MutatedZombieHorse>> MUTATED_ZOMBIE_HORSE = ENTITY_TYPES.register("mutated_zombie_horse",
            () -> EntityType.Builder.<MutatedZombieHorse>of(MutatedZombieHorse::new, MobCategory.MONSTER).sized(1.3964844F, 1.6F).eyeHeight(1.52F)
                    .passengerAttachments(1.31875F).clientTrackingRange(10).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("mutated_zombie_horse"))));

    /** The desert's ({@link dev.nookofthedead.world.Desert}): the mutated husk as vanilla's husk, the camel as its camel husk. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.MutatedHusk>> MUTATED_HUSK = ENTITY_TYPES.register("mutated_husk",
            () -> EntityType.Builder.<dev.nookofthedead.entity.MutatedHusk>of(dev.nookofthedead.entity.MutatedHusk::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.075F).ridingOffset(-0.7F)
                    .clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("mutated_husk"))));
    public static final Supplier<EntityType<dev.nookofthedead.entity.InfectedCamel>> INFECTED_CAMEL = ENTITY_TYPES.register("infected_camel",
            () -> EntityType.Builder.<dev.nookofthedead.entity.InfectedCamel>of(dev.nookofthedead.entity.InfectedCamel::new, MobCategory.MONSTER)
                    .sized(1.7F, 2.375F).eyeHeight(2.275F).clientTrackingRange(10).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("infected_camel"))));
    public static final Supplier<EntityType<dev.nookofthedead.entity.InfectedScorpion>> INFECTED_SCORPION = ENTITY_TYPES.register("infected_scorpion",
            () -> EntityType.Builder.<dev.nookofthedead.entity.InfectedScorpion>of(dev.nookofthedead.entity.InfectedScorpion::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.5F).eyeHeight(0.3F).clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("infected_scorpion"))));

    /** A makeshift grenade in flight or lying, sized and tracked as a thrown potion. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.Grenade>> GRENADE = ENTITY_TYPES.register("grenade",
            () -> EntityType.Builder.<dev.nookofthedead.entity.Grenade>of(dev.nookofthedead.entity.Grenade::new, MobCategory.MISC)
                    .noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("grenade"))));

    /** The boss (a zombie's frame, 2.4 times its size by its scale attribute). */
    public static final Supplier<EntityType<Vandal>> VANDAL = ENTITY_TYPES.register("vandal",
            () -> EntityType.Builder.<Vandal>of(Vandal::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F)
                    .passengerAttachments(2.0125F).clientTrackingRange(10).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("vandal"))));

    /** A survivor's remains ({@link dev.nookofthedead.entity.Remains}): low, as one sitting. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.Remains>> REMAINS = ENTITY_TYPES.register("remains",
            () -> EntityType.Builder.<dev.nookofthedead.entity.Remains>of(dev.nookofthedead.entity.Remains::new, MobCategory.MISC)
                    .sized(0.7F, 0.9F).eyeHeight(0.8F).clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("remains"))));

    /** The small things the plague took: a bat that bites, sized as vanilla's; a rat, as small as a silverfish. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.InfectedBat>> INFECTED_BAT = ENTITY_TYPES.register("infected_bat",
            () -> EntityType.Builder.<dev.nookofthedead.entity.InfectedBat>of(dev.nookofthedead.entity.InfectedBat::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("infected_bat"))));
    /**
     * The capital's crows (CapitalCrow): small, flying, ambient - brought only by their own flocking over the capital
     * (Crows), never by the game's spawning, so nothing of the dead's share is theirs.
     */
    public static final Supplier<EntityType<dev.nookofthedead.entity.CapitalCrow>> CAPITAL_CROW = ENTITY_TYPES.register("capital_crow",
            () -> EntityType.Builder.<dev.nookofthedead.entity.CapitalCrow>of(dev.nookofthedead.entity.CapitalCrow::new, MobCategory.AMBIENT)
                    .sized(0.5F, 0.45F).eyeHeight(0.3F).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("capital_crow"))));
    /** A village's cat the plague took: a cat's size. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.InfectedCat>> INFECTED_CAT = ENTITY_TYPES.register("infected_cat",
            () -> EntityType.Builder.<dev.nookofthedead.entity.InfectedCat>of(dev.nookofthedead.entity.InfectedCat::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.7F).eyeHeight(0.35F).passengerAttachments(0.5125F).clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("infected_cat"))));
    public static final Supplier<EntityType<dev.nookofthedead.entity.InfectedRat>> INFECTED_RAT = ENTITY_TYPES.register("infected_rat",
            () -> EntityType.Builder.<dev.nookofthedead.entity.InfectedRat>of(dev.nookofthedead.entity.InfectedRat::new, MobCategory.MONSTER)
                    .sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("infected_rat"))));

    /** The sewers' mini-boss ({@link dev.nookofthedead.entity.SewerBloom}): rooted, big, drawn twice its model's size. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.SewerBloom>> SEWER_BLOOM = ENTITY_TYPES.register("sewer_bloom",
            () -> EntityType.Builder.<dev.nookofthedead.entity.SewerBloom>of(dev.nookofthedead.entity.SewerBloom::new, MobCategory.MONSTER)
                    .sized(2.0F, 3.5F).eyeHeight(2.7F).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("sewer_bloom"))));

    /** The sky base's dead and its guards (0.3): the thin air's own zombie, sized as vanilla's; the Sentry Golem, as the iron golem. */
    public static final Supplier<EntityType<dev.nookofthedead.entity.ThinAirZombie>> THIN_AIR_ZOMBIE = ENTITY_TYPES.register("thin_air_zombie",
            () -> EntityType.Builder.<dev.nookofthedead.entity.ThinAirZombie>of(dev.nookofthedead.entity.ThinAirZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F)
                    .clientTrackingRange(8).notInPeaceful()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("thin_air_zombie"))));
    public static final Supplier<EntityType<dev.nookofthedead.entity.SentryGolem>> SENTRY_GOLEM = ENTITY_TYPES.register("sentry_golem",
            () -> EntityType.Builder.<dev.nookofthedead.entity.SentryGolem>of(dev.nookofthedead.entity.SentryGolem::new, MobCategory.MONSTER)
                    .sized(1.4F, 2.7F).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, NookOfTheDead.id("sentry_golem"))));

    @SubscribeEvent
    static void attributes(EntityAttributeCreationEvent event) {
        event.put(INFECTED_BAT.get(), dev.nookofthedead.entity.InfectedBat.createAttributes().build());
        event.put(CAPITAL_CROW.get(), dev.nookofthedead.entity.CapitalCrow.createAttributes().build());
        event.put(INFECTED_RAT.get(), dev.nookofthedead.entity.InfectedRat.createAttributes().build());
        event.put(INFECTED_CAT.get(), dev.nookofthedead.entity.InfectedCat.createAttributes().build());
        event.put(MUTATED_HUSK.get(), MutatedZombie.createAttributes().build());
        event.put(INFECTED_CAMEL.get(), dev.nookofthedead.entity.InfectedCamel.createAttributes().build());
        event.put(INFECTED_SCORPION.get(), dev.nookofthedead.entity.InfectedScorpion.createAttributes().build());
        event.put(REMAINS.get(), dev.nookofthedead.entity.Remains.createAttributes().build());
        event.put(VANDAL.get(), Vandal.createAttributes().build());
        event.put(SEWER_BLOOM.get(), dev.nookofthedead.entity.SewerBloom.createAttributes().build());
        event.put(THIN_AIR_ZOMBIE.get(), dev.nookofthedead.entity.ThinAirZombie.createAttributes().build());
        event.put(SENTRY_GOLEM.get(), dev.nookofthedead.entity.SentryGolem.createAttributes().build());
        event.put(ZOMBIE_COW.get(), ZombieCow.createAttributes().build());
        event.put(ZOMBIE_WOLF.get(), ZombieWolf.createAttributes().build());
        event.put(MUTATED_ZOMBIE_HORSE.get(), MutatedZombieHorse.createAttributes().build());
        event.put(MUTATED_ZOMBIE.get(), MutatedZombie.createAttributes().build());
        event.put(MUTATED_ZOMBIE_VILLAGER.get(), MutatedZombieVillager.createAttributes().build());
    }

    @SubscribeEvent
    static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(MUTATED_ZOMBIE.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MUTATED_ZOMBIE_VILLAGER.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MUTATED_HUSK.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(INFECTED_CAMEL.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(INFECTED_SCORPION.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ZOMBIE_COW.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ZOMBIE_WOLF.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MUTATED_ZOMBIE_HORSE.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(THIN_AIR_ZOMBIE.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(INFECTED_BAT.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                dev.nookofthedead.entity.InfectedBat::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(INFECTED_RAT.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(INFECTED_CAT.get(), dev.nookofthedead.entity.Footing.NATURAL, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MutatedZombie::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ZwEntities() {}
}
