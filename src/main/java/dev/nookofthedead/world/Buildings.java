package dev.nookofthedead.world;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Buildings a data pack adds to the town, beside the mod's own: one file each under
 * {@code data/<namespace>/nookofthedead/buildings/}, read on every (re)load -
 * <pre>
 * {
 *   "template": "mypack:town/bakery",           a structure template, built as the mod's are (front to the north)
 *   "kind": "s",                                 xs (16 x 16), s (32 x 32), m (64 x 64), court (a ring's middle),
 *                                                rare_s or rare_m (the places to find supplies in)
 *   "districts": {"centre": 3, "residential": 1} where it may stand, and how often (the mod's own weigh 1 to 5):
 *                                                centre, residential, outskirts, old_town, waste, settlement, farm, wild
 * }
 * </pre>
 * The template's signs are the mod's markers ([zombie], [loot food], [remains]... see {@link Markers}). A world's town is
 * laid out from these as it is made: add them before a world is made, or only its new ground will have them.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Buildings implements ResourceManagerReloadListener {
    /** The most a building may weigh (the mod's own weigh 1 to 5). */
    public static final int MAX_WEIGHT = 1000;
    /** Where in a data pack the files are. */
    public static final String FOLDER = "nookofthedead/buildings";

    /** The lists of a district a building can join. */
    public enum Kind {
        XS, S, M, COURT, RARE_S, RARE_M
    }

    @SubscribeEvent
    static void onAddListeners(AddServerReloadListenersEvent event) {
        event.addListener(NookOfTheDead.id("buildings"), new Buildings());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<District, Map<Kind, List<District.Weighted>>> added = new EnumMap<>(District.class);
        int read = 0;
        for (Map.Entry<Identifier, Resource> file : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Identifier template = Identifier.parse(json.get("template").getAsString());
                Kind kind = Kind.valueOf(json.get("kind").getAsString().toUpperCase(Locale.ROOT));
                Map<District, Integer> weights = new EnumMap<>(District.class);    // the whole file read before any of it counts
                for (Map.Entry<String, JsonElement> d : json.getAsJsonObject("districts").entrySet()) {
                    weights.put(District.valueOf(d.getKey().toUpperCase(Locale.ROOT)), Math.min(MAX_WEIGHT, d.getValue().getAsInt()));
                }
                weights.forEach((district, weight) -> {
                    if (weight > 0) {
                        added.computeIfAbsent(district, x -> new EnumMap<>(Kind.class)).computeIfAbsent(kind, x -> new ArrayList<>())
                                .add(new District.Weighted(template, weight));
                    }
                });
                read++;
            } catch (Exception e) {
                NookOfTheDead.LOGGER.warn("Skipping the building {}: {}", file.getKey(), e.toString());
            }
        }
        District.extend(added);
        Town.forget();
        if (read > 0) {
            NookOfTheDead.LOGGER.info("Added {} building(s) from data packs to the town", read);
        }
    }
}
