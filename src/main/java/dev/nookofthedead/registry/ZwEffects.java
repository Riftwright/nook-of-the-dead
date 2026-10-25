package dev.nookofthedead.registry;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.infection.SuppressantEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The suppressant as a potion: its effect ({@link SuppressantEffect}) and the potion of it, so it is a bottle as any other
 * and the brewing stand makes it a splash or a lingering one ({@code data/nookofthedead/recipe/brewing/}). Made from
 * nothing in a brewing stand: it is found, not brewed.
 */
public final class ZwEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, NookOfTheDead.MODID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, NookOfTheDead.MODID);

    public static final DeferredHolder<MobEffect, SuppressantEffect> SUPPRESSANT = EFFECTS.register("suppressant", SuppressantEffect::new);
    public static final DeferredHolder<Potion, Potion> SUPPRESSANT_POTION = POTIONS.register("suppressant",
            () -> new Potion("suppressant", new MobEffectInstance(SUPPRESSANT, 1)));
    /**
     * The long one: redstone on a brewing stand, thirty minutes held back instead of fifteen (the user, 2026-10-19:
     * "醸造台でレッドストーンで強化すると、効果が30分に伸びる") - the effect's level says which.
     */
    public static final DeferredHolder<Potion, Potion> LONG_SUPPRESSANT_POTION = POTIONS.register("long_suppressant",
            () -> new Potion("long_suppressant", new MobEffectInstance(SUPPRESSANT, 1, 1)));

    /** A bottle of {@code kind} (a potion, a splash or a lingering one) holding the suppressant. */
    public static ItemStack bottle(Item kind) {
        ItemStack stack = new ItemStack(kind);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents((Holder<Potion>) SUPPRESSANT_POTION));
        return stack;
    }

    /**
     * Marked as brewed: a suppressant a brewing stand has just made carries it, so Bottled Hope is for one brewed, not one
     * found - left in a stand, or put in one and taken out again (the review, 2026-10-19: both gave it).
     */
    public static final String BREWED = "nookofthedead_brewed";

    public static ItemStack brewed(ItemStack stack) {
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(BREWED, true));
        return stack;
    }

    public static boolean isBrewed(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(BREWED, false);
    }

    /** Whether {@code stack} holds the suppressant (drunk, splash or lingering). */
    public static boolean isSuppressant(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(SUPPRESSANT_POTION) || contents.is(LONG_SUPPRESSANT_POTION));
    }

    private ZwEffects() {}
}
