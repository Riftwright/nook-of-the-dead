package dev.nookofthedead;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.infection.Infection;
import dev.nookofthedead.registry.ZwBlocks;
import dev.nookofthedead.registry.ZwEffects;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.registry.ZwItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Nook of the Dead: Nookworks' If remade as a world whose civilisation fell to a zombie plague overnight.
 * The If's ground is a town of ruins on flat land ({@link dev.nookofthedead.world.FallenTown}); fast zombies that do not
 * burn come out by day and night and only a roof and a light keep them off; whoever steps in is infected and dies of it
 * unless a suppressant, found only in the ruins, is drunk every twenty minutes ({@link Infection}). Everywhere else the game
 * is as it was.
 */
@Mod(NookOfTheDead.MODID)
public final class NookOfTheDead {
    public static final String MODID = "nookofthedead";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NookOfTheDead(IEventBus modEventBus, ModContainer modContainer) {
        ZwBlocks.BLOCKS.register(modEventBus);
        ZwBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);
        ZwItems.ITEMS.register(modEventBus);
        ZwEffects.EFFECTS.register(modEventBus);
        ZwEffects.POTIONS.register(modEventBus);
        ZwItems.TABS.register(modEventBus);
        ZwEntities.ENTITY_TYPES.register(modEventBus);
        Infection.ATTACHMENT_TYPES.register(modEventBus);
        registerGameTests(modEventBus);
    }

    /** The GameTests are in the dev build only (the jar leaves their package out): looked up by name, not linked. */
    private static void registerGameTests(IEventBus modEventBus) {
        try {
            Class.forName("dev.nookofthedead.gametest.ZwGameTests").getMethod("register", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException notShipped) {
            // the published jar
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not register the GameTests", e);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /** Whether {@code level} is the fallen world: Nookworks' If. */
    public static boolean fallen(Level level) {
        return level.dimension().equals(IfWorld.KEY);
    }
}
