package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The add-on's client entry: Mods > Nook of the Dead > Config opens NeoForge's screen for its settings
 * ({@link dev.nookofthedead.ZwConfig}), as Nookworks' and Nook Technology's do. Its words are the lang files'
 * {@code nookofthedead.configuration.*}.
 */
@Mod(value = NookOfTheDead.MODID, dist = Dist.CLIENT)
public final class ZwClientMod {
    public ZwClientMod(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
