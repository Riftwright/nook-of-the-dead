package dev.nookofthedead.registry;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZwItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NookOfTheDead.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NookOfTheDead.MODID);

    /** A scrap of a map; four make the scrawled map to an institute ({@link dev.nookofthedead.item.ScrawledMapItem}). */
    public static final DeferredItem<net.minecraft.world.item.Item> MAP_SCRAP = ITEMS.registerSimpleItem("map_scrap", p -> p.stacksTo(16));
    public static final DeferredItem<dev.nookofthedead.item.ScrawledMapItem> SCRAWLED_MAP = ITEMS.registerItem("scrawled_map",
            dev.nookofthedead.item.ScrawledMapItem::new, p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    /** The Vandal's blood ({@link dev.nookofthedead.item.VandalBloodItem}), and what it makes of rotten flesh. */
    public static final DeferredItem<dev.nookofthedead.item.VandalBloodItem> VANDAL_BLOOD = ITEMS.registerItem("vandal_blood",
            dev.nookofthedead.item.VandalBloodItem::new, p -> p.durability(dev.nookofthedead.item.VandalBloodItem.USES)
                    .rarity(net.minecraft.world.item.Rarity.EPIC));
    /**
     * A launch key of the vaccine's console ({@link dev.nookofthedead.ending.VaccineBlock}): four of them start it. Found in
     * the second institute - on two who died there, in its desk and a locker - and now and then on the thin-air dead,
     * who carried theirs up.
     */
    public static final DeferredItem<dev.nookofthedead.item.LaunchKeyItem> LAUNCH_KEY = ITEMS.registerItem("launch_key",
            dev.nookofthedead.item.LaunchKeyItem::new, p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON).fireResistant());
    public static final DeferredItem<net.minecraft.world.item.Item> CLEANSED_FLESH = ITEMS.registerSimpleItem("cleansed_flesh", p -> p);
    /** A makeshift grenade ({@link dev.nookofthedead.item.GrenadeItem}): the dead round where it lands, put down at once, loudly. */
    public static final DeferredItem<dev.nookofthedead.item.GrenadeItem> GRENADE = ITEMS.registerItem("grenade",
            dev.nookofthedead.item.GrenadeItem::new, p -> p.stacksTo(8));

    public static final DeferredItem<SpawnEggItem> MUTATED_ZOMBIE_SPAWN_EGG = ITEMS.registerItem("mutated_zombie_spawn_egg", SpawnEggItem::new,
            p -> p.spawnEgg(ZwEntities.MUTATED_ZOMBIE.get()));

    public static final DeferredItem<SpawnEggItem> MUTATED_ZOMBIE_VILLAGER_SPAWN_EGG = ITEMS.registerItem("mutated_zombie_villager_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get()));
    public static final DeferredItem<SpawnEggItem> VANDAL_SPAWN_EGG = ITEMS.registerItem("vandal_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.VANDAL.get()));
    public static final DeferredItem<SpawnEggItem> REMAINS_SPAWN_EGG = ITEMS.registerItem("remains_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.REMAINS.get()));
    public static final DeferredItem<SpawnEggItem> ZOMBIE_COW_SPAWN_EGG = ITEMS.registerItem("zombie_cow_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.ZOMBIE_COW.get()));
    public static final DeferredItem<SpawnEggItem> ZOMBIE_WOLF_SPAWN_EGG = ITEMS.registerItem("zombie_wolf_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.ZOMBIE_WOLF.get()));
    public static final DeferredItem<SpawnEggItem> INFECTED_CAT_SPAWN_EGG = ITEMS.registerItem("infected_cat_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.INFECTED_CAT.get()));
    public static final DeferredItem<SpawnEggItem> MUTATED_ZOMBIE_HORSE_SPAWN_EGG = ITEMS.registerItem("mutated_zombie_horse_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.MUTATED_ZOMBIE_HORSE.get()));
    public static final DeferredItem<SpawnEggItem> INFECTED_BAT_SPAWN_EGG = ITEMS.registerItem("infected_bat_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.INFECTED_BAT.get()));
    public static final DeferredItem<SpawnEggItem> CAPITAL_CROW_SPAWN_EGG = ITEMS.registerItem("capital_crow_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.CAPITAL_CROW.get()));
    public static final DeferredItem<SpawnEggItem> INFECTED_RAT_SPAWN_EGG = ITEMS.registerItem("infected_rat_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.INFECTED_RAT.get()));
    public static final DeferredItem<SpawnEggItem> MUTATED_HUSK_SPAWN_EGG = ITEMS.registerItem("mutated_husk_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.MUTATED_HUSK.get()));
    public static final DeferredItem<SpawnEggItem> INFECTED_CAMEL_SPAWN_EGG = ITEMS.registerItem("infected_camel_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.INFECTED_CAMEL.get()));
    public static final DeferredItem<SpawnEggItem> SEWER_BLOOM_SPAWN_EGG = ITEMS.registerItem("sewer_bloom_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.SEWER_BLOOM.get()));
    public static final DeferredItem<SpawnEggItem> THIN_AIR_ZOMBIE_SPAWN_EGG = ITEMS.registerItem("thin_air_zombie_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.THIN_AIR_ZOMBIE.get()));
    public static final DeferredItem<SpawnEggItem> SENTRY_GOLEM_SPAWN_EGG = ITEMS.registerItem("sentry_golem_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.SENTRY_GOLEM.get()));
    public static final DeferredItem<SpawnEggItem> INFECTED_SCORPION_SPAWN_EGG = ITEMS.registerItem("infected_scorpion_spawn_egg",
            SpawnEggItem::new, p -> p.spawnEgg(ZwEntities.INFECTED_SCORPION.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nookofthedead"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ZwEffects.bottle(Items.POTION))
                    .displayItems((parameters, output) -> {
                        output.accept(ZwEffects.bottle(Items.POTION));
                        output.accept(ZwEffects.bottle(Items.SPLASH_POTION));
                        output.accept(ZwEffects.bottle(Items.LINGERING_POTION));
                        for (var kind : new net.minecraft.world.item.Item[] {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
                            output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(kind, ZwEffects.LONG_SUPPRESSANT_POTION));
                        }
                        output.accept(MUTATED_ZOMBIE_SPAWN_EGG.get());
                        output.accept(MUTATED_ZOMBIE_VILLAGER_SPAWN_EGG.get());
                        output.accept(VANDAL_SPAWN_EGG.get());
                        output.accept(GRENADE.get());
                        output.accept(MAP_SCRAP.get());
                        output.accept(SCRAWLED_MAP.get());
                        output.accept(VANDAL_BLOOD.get());
                        output.accept(LAUNCH_KEY.get());
                        output.accept(CLEANSED_FLESH.get());
                        output.accept(REMAINS_SPAWN_EGG.get());
                        output.accept(ZOMBIE_COW_SPAWN_EGG.get());
                        output.accept(ZOMBIE_WOLF_SPAWN_EGG.get());
                        output.accept(INFECTED_CAT_SPAWN_EGG.get());
                        output.accept(MUTATED_ZOMBIE_HORSE_SPAWN_EGG.get());
                        output.accept(INFECTED_BAT_SPAWN_EGG.get());
                        output.accept(INFECTED_RAT_SPAWN_EGG.get());
                        output.accept(CAPITAL_CROW_SPAWN_EGG.get());
                        output.accept(MUTATED_HUSK_SPAWN_EGG.get());
                        output.accept(INFECTED_CAMEL_SPAWN_EGG.get());
                        output.accept(INFECTED_SCORPION_SPAWN_EGG.get());
                        output.accept(SEWER_BLOOM_SPAWN_EGG.get());
                        output.accept(THIN_AIR_ZOMBIE_SPAWN_EGG.get());
                        output.accept(SENTRY_GOLEM_SPAWN_EGG.get());
                    })
                    .build());

    private ZwItems() {}
}
