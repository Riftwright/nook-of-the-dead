package dev.nookofthedead.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Development only (this package stays out of the jar): the guide site's icons, drawn by the game as Nookworks' own
 * IconSheetScreen draws them - every item of this mod's creative tab, then the vanilla items listed one id a line in
 * {@code run-agent/icon_list.txt}, each 64 px wide on a flat key colour in cells of 80 px, 16 x 9 a page (a 1280 x 720
 * window). {@code /nzwicons <page> [green]} opens a page and writes {@code icon_sheet.txt}: a line per cell with the
 * id tools/make_guide.py names the icon by - the suppressant bottles, all potions to the registry, by their own names.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class IconSheet extends Screen {
    private static final int CELL = 80;
    private static final int COLUMNS = 16;
    private static final int ROWS = 9;
    private static final int KEY = 0xFFFF00FF;
    private static final int KEY_GREEN = 0xFF00FF00;

    private final int page;
    private final int key;
    private final List<ItemStack> items = new ArrayList<>();

    private IconSheet(int page, boolean green) {
        super(Component.literal("icon sheet"));
        this.page = page;
        this.key = green ? KEY_GREEN : KEY;
    }

    @SubscribeEvent
    static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nzwicons")
                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                        .executes(c -> open(IntegerArgumentType.getInteger(c, "page"), false))
                        .then(Commands.literal("green")
                                .executes(c -> open(IntegerArgumentType.getInteger(c, "page"), true)))));
    }

    private static int open(int page, boolean green) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new IconSheet(page, green)));
        return 1;
    }

    /**
     * The id a stack's icon is saved under: the item's, or for a bottle with a potion in it, the potion's own, its kind
     * before it ({@code nookofthedead:splash_suppressant}, {@code minecraft:awkward_potion}).
     */
    private static String idOf(ItemStack stack) {
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.potion().isPresent()) {
            Identifier id = potion.potion().get().unwrapKey().orElseThrow().identifier();
            String kind = stack.is(Items.SPLASH_POTION) ? "splash_" : stack.is(Items.LINGERING_POTION) ? "lingering_" : "";
            return id.getNamespace().equals(NookOfTheDead.MODID) ? id.getNamespace() + ":" + kind + id.getPath()
                    : id.getNamespace() + ":" + kind + id.getPath() + "_potion";
        }
        return stack.typeHolder().getRegisteredName();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
        List<ItemStack> all = new ArrayList<>(ZwItems.TAB.get().getDisplayItems());
        all.add(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)); // what the suppressant is brewed from
        try {
            for (String line : Files.readAllLines(Path.of("icon_list.txt"), StandardCharsets.UTF_8)) {
                var id = Identifier.tryParse(line.trim());
                if (id != null && !line.isBlank()) {
                    BuiltInRegistries.ITEM.get(id).ifPresent(item -> all.add(new ItemStack(item)));
                }
            }
        } catch (IOException e) {
            NookOfTheDead.LOGGER.info("[icons] no icon_list.txt: this mod's tab only");
        }
        int from = this.page * COLUMNS * ROWS;
        this.items.clear();
        this.items.addAll(all.subList(Math.min(from, all.size()), Math.min(from + COLUMNS * ROWS, all.size())));
        StringBuilder out = new StringBuilder("page ").append(this.page).append(" of ")
                .append((all.size() + COLUMNS * ROWS - 1) / (COLUMNS * ROWS)).append(" total ").append(all.size()).append('\n');
        this.items.forEach(stack -> out.append(idOf(stack)).append('\n'));
        try {
            Files.writeString(Path.of("icon_sheet.txt"), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NookOfTheDead.LOGGER.warn("[icons] could not write icon_sheet.txt", e);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, this.key);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractBackground(graphics, mouseX, mouseY, a);
        // cells in window pixels: the GUI scale is undone, so the sheet is the same at any setting
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float scale = 4.0F / gui;
        for (int i = 0; i < this.items.size(); i++) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((i % COLUMNS) * CELL / gui + 8 / gui, (i / COLUMNS) * CELL / gui + 8 / gui);
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(this.items.get(i), 0, 0);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
