package dev.nookofthedead.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.ZwConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The add-on's own loot conditions. */
public final class ZwLoot {
    public static final DeferredRegister<MapCodec<? extends LootItemCondition>> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, NookOfTheDead.MODID);

    /**
     * The suppressant in a chest: {@code "chance"} as the table has it, times the setting {@link ZwConfig#SUPPRESSANT_FINDS}
     * (the user, 2026-11-19: 「抑制剤の出現頻度を、設定で変更できないかな。人によっては渋すぎてゲームにならないかも」), no more
     * than certain. Every chest table's suppressant pool has it in place of {@code minecraft:random_chance}; rolled as the
     * chest is first opened, so a change counts in every chest not opened yet.
     */
    public record SuppressantChance(float chance) implements LootItemCondition {
        public static final MapCodec<SuppressantChance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(SuppressantChance::chance)).apply(i, SuppressantChance::new));

        /** The chance now, with the setting. */
        public float now() {
            return (float) Math.min(1.0, this.chance * ZwConfig.suppressantFinds());
        }

        @Override
        public boolean test(LootContext context) {
            return context.getRandom().nextFloat() < this.now();
        }

        @Override
        public MapCodec<SuppressantChance> codec() {
            return CODEC;
        }
    }

    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<SuppressantChance>> SUPPRESSANT_CHANCE =
            CONDITIONS.register("suppressant_chance", () -> SuppressantChance.CODEC);

    private ZwLoot() {}
}
