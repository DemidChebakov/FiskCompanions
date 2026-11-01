package com.dchebakov.fiskcompanions.client.render;

import java.util.HashMap;
import java.util.Map;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.client.render.hero.HeroRenderer;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Whether a character gets the Steve body under its suit. Suits drawn on a normal player body (including ones with
 * gaps meant to show skin) get it; suits whose renderer builds the body from custom models (robots, creatures, other
 * body shapes) don't, or the default arms and legs stick out beside the suit's own. Cached per suit.
 */
@SideOnly(Side.CLIENT)
public final class BaseBody {

    private static final Map<String, Boolean> CACHE = new HashMap<>();

    private BaseBody() {}

    public static boolean show(EntityFiskHero entity) {
        HeroIteration iter = entity.getWornSuit();
        if (iter == null) {
            return true;
        }
        return CACHE.computeIfAbsent(FiskBridge.keyOf(iter), k -> !usesCustomBody(iter, entity));
    }

    private static boolean usesCustomBody(HeroIteration iter, EntityFiskHero entity) {
        try {
            HeroRenderer renderer = HeroRenderer.get(iter, entity);
            return renderer.hasEffect(com.fiskmods.heroes.client.render.hero.effect.js.HeroEffectModel.class)
                || renderer.hasEffect(com.fiskmods.heroes.client.render.hero.effect.HeroEffectModel.class);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
