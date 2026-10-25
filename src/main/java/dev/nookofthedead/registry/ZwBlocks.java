package dev.nookofthedead.registry;

import java.util.function.Supplier;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.ZombieMarkerBlock;
import dev.nookofthedead.world.ZombieMarkerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZwBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NookOfTheDead.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NookOfTheDead.MODID);

    /** Where a ruin's {@code [zombie]} sign stood: unseen, it lets its zombies out when someone comes near. */
    public static final DeferredBlock<ZombieMarkerBlock> ZOMBIE_MARKER = BLOCKS.registerBlock("zombie_marker", ZombieMarkerBlock::new,
            p -> p.noCollision().noLootTable().strength(-1.0F, 3600000.0F).noOcclusion().replaceable().pushReaction(PushReaction.POPPED));

    public static final Supplier<BlockEntityType<ZombieMarkerBlockEntity>> ZOMBIE_MARKER_ENTITY = BLOCK_ENTITY_TYPES.register("zombie_marker",
            () -> new BlockEntityType<>(ZombieMarkerBlockEntity::new, ZOMBIE_MARKER.get()));

    /** The vaccine's dispersal console at the top of the way ({@link dev.nookofthedead.ending.VaccineBlock}): nothing breaks it. */
    public static final DeferredBlock<dev.nookofthedead.ending.VaccineBlock> VACCINE = BLOCKS.registerBlock("vaccine",
            dev.nookofthedead.ending.VaccineBlock::new, p -> p.noLootTable().strength(-1.0F, 3600000.0F).lightLevel(s -> 6).noOcclusion()
                    .sound(net.minecraft.world.level.block.SoundType.METAL));

    /** The vaccine's vessel and tanks behind the console ({@link dev.nookofthedead.ending.VaccineTankBlock}): lit while full. */
    public static final DeferredBlock<dev.nookofthedead.ending.VaccineTankBlock> VACCINE_TANK = BLOCKS.registerBlock("vaccine_tank",
            dev.nookofthedead.ending.VaccineTankBlock::new, p -> p.noLootTable().strength(-1.0F, 3600000.0F).noOcclusion()
                    .lightLevel(s -> s.getValue(dev.nookofthedead.ending.VaccineTankBlock.FULL) ? 12 : 0)
                    .pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, at, type) -> false).isRedstoneConductor((s, l, at) -> false)
                    .isSuffocating((s, l, at) -> false)
                    .sound(net.minecraft.world.level.block.SoundType.GLASS));

    /**
     * The plate the last researchers sealed their refuge in, all round it in the rock ({@link dev.nookofthedead.world.Refuge#hull}):
     * nothing breaks it - no tool, no blast, no Vandal, no Wither - and nothing moves it. Not an item: it is only ever found
     * where they put it (the user, 2026-10-19: "このmod用の破壊不可能なブロック…岩盤使うとダサいし").
     */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> BULKHEAD = BLOCKS.registerSimpleBlock("bulkhead",
            p -> p.noLootTable().strength(-1.0F, 3600000.0F).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, at, type) -> false)
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK));

    private ZwBlocks() {}
}
