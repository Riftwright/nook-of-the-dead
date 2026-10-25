package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The mutated zombie looks as vanilla's zombie does, the one that was a villager as vanilla's zombie villager. The infection has no clock on the screen
 * (the user, 2026-10-13: "感染タイマーとか…左上に出てくる必要はないよ。説明的すぎる"): the body tells the time left.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class ZwClient {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZwEntities.MUTATED_ZOMBIE.get(), MutatedRenderer::new);
        event.registerEntityRenderer(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), ZombieVillagerRenderer::new);
        // the Vandal in its own look (VandalModel)
        event.registerEntityRenderer(ZwEntities.VANDAL.get(), VandalRenderer::new);
        event.registerEntityRenderer(ZwEntities.REMAINS.get(), RemainsRenderer::new);
        event.registerEntityRenderer(ZwEntities.GRENADE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        // the animals the plague took: vanilla's own pictures, by name, grey and sick - but the cow, rotting in its own skin
        event.registerEntityRenderer(ZwEntities.ZOMBIE_COW.get(), ZombieCowRenderer::new);
        event.registerEntityRenderer(ZwEntities.ZOMBIE_WOLF.get(), context -> new net.minecraft.client.renderer.entity.WolfRenderer(context) {
            @Override
            protected int getModelTint(net.minecraft.client.renderer.entity.state.WolfRenderState state) {
                return SICK;
            }
        });
        event.registerEntityRenderer(ZwEntities.INFECTED_CAT.get(), context -> new net.minecraft.client.renderer.entity.CatRenderer(context) {
            @Override
            protected int getModelTint(net.minecraft.client.renderer.entity.state.CatRenderState state) {
                return SICK;
            }
        });
        event.registerEntityRenderer(ZwEntities.MUTATED_ZOMBIE_HORSE.get(), context -> new net.minecraft.client.renderer.entity.UndeadHorseRenderer(context,
                net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE,
                net.minecraft.client.model.geom.ModelLayers.ZOMBIE_HORSE_SADDLE,
                net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.ZOMBIE, net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.ZOMBIE_BABY));
    }

    /** The small things: a bat gone rusty dark, the rat its own model and skin (RatModel). */
    @SubscribeEvent
    static void smallRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZwEntities.INFECTED_BAT.get(), context -> new net.minecraft.client.renderer.entity.BatRenderer(context) {
            @Override
            protected int getModelTint(net.minecraft.client.renderer.entity.state.BatRenderState state) {
                return 0xFFB08478;
            }
        });
        event.registerEntityRenderer(ZwEntities.INFECTED_RAT.get(), RatRenderer::new);
        event.registerEntityRenderer(ZwEntities.CAPITAL_CROW.get(), CrowRenderer::new);
        // the desert's: the husk as the game's (a screamer in its own skin), the camel as its camel husk, the scorpion its own
        event.registerEntityRenderer(ZwEntities.MUTATED_HUSK.get(), MutatedHuskRenderer::new);
        event.registerEntityRenderer(ZwEntities.INFECTED_CAMEL.get(), net.minecraft.client.renderer.entity.CamelHuskRenderer::new);
        event.registerEntityRenderer(ZwEntities.INFECTED_SCORPION.get(), ScorpionRenderer::new);
        // the sewers' bloom, its own
        event.registerEntityRenderer(ZwEntities.SEWER_BLOOM.get(), BloomRenderer::new);
        // the sky base's: the thin air's zombie and the Sentry Golem, each in its own skin and light (ThinAirRenderer,
        // SentryRenderer)
        event.registerEntityRenderer(ZwEntities.THIN_AIR_ZOMBIE.get(), ThinAirRenderer::new);
        event.registerEntityRenderer(ZwEntities.SENTRY_GOLEM.get(), SentryRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RatModel.LAYER, RatModel::createBodyLayer);
        event.registerLayerDefinition(CrowModel.LAYER, CrowModel::createBodyLayer);
        event.registerLayerDefinition(ScorpionModel.LAYER, ScorpionModel::createBodyLayer);
        event.registerLayerDefinition(VandalModel.LAYER, VandalModel::createBodyLayer);
        event.registerLayerDefinition(BloomModel.LAYER, BloomModel::createBodyLayer);
        event.registerLayerDefinition(SentryModel.LAYER, SentryModel::createBodyLayer);
        event.registerLayerDefinition(ThinAirModel.LAYER, ThinAirModel::createBodyLayer);
    }

    /** The grey-green of an animal the plague took, multiplied over its own colours. */
    static final int SICK = 0xFF9EAA92;

    private ZwClient() {}
}
