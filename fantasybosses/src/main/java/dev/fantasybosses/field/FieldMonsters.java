package dev.fantasybosses.field;

import dev.fantasybosses.FantasyBosses;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overworld field monsters (model pack v1.2: 20 normal + 10 elite).
 *
 * Hitbox = body core only (torso, head without crown/hat, legs). Decorations, weapons,
 * capes, banners, tails, wings and crowns are excluded. Minecraft AABBs do not rotate,
 * so long bodies (spiders, wyverns, rift beasts) use a compromise between width and length.
 *
 * No natural spawning is registered here: summon with /summon or /fantasybosses field.
 * Stats are [실험] test values only.
 */
public final class FieldMonsters {
    private FieldMonsters() {}

    /** idleClip/runClip let a few families reuse their extra loops (float, skitter). */
    public record Spec(String id, boolean elite, float width, float height, String idleClip, String runClip) {}

    public static final List<Spec> SPECS = List.of(
            // 0 frost
            n("snow_wanderer", 0.70F, 2.10F),
            new Spec("blizzard_wraith", false, 0.80F, 2.10F, "float", "run"),
            e("frost_executioner", 1.20F, 2.80F),
            // 1 forest / web
            new Spec("venom_forest_spider", false, 1.60F, 1.00F, "idle", "skitter"),
            new Spec("web_hunter_spider", false, 1.70F, 1.00F, "idle", "skitter"),
            new Spec("spider_nest_guardian", true, 2.60F, 1.75F, "idle", "skitter"),
            // 2 mine
            n("haunted_miner", 0.70F, 2.05F),
            n("wraith_digger", 0.75F, 2.05F),
            e("cursed_mine_overseer", 1.05F, 2.50F),
            // 3 cliffs
            n("young_wyvern", 0.90F, 1.05F),
            n("cliff_wyvern", 1.40F, 1.45F),
            e("wyvern_hunter", 0.95F, 2.55F),
            // 4 corrupted
            n("corrupted_swordsman", 0.75F, 2.10F),
            n("corrupted_crossbowman", 0.75F, 2.10F),
            e("corrupted_captain", 1.20F, 2.55F),
            // 5 plague
            n("plague_infected", 0.70F, 2.05F),
            n("plague_rat", 0.70F, 0.50F),
            e("plague_devourer", 1.15F, 2.70F),
            // 6 drowned
            n("drowned_variant", 0.70F, 2.05F),
            new Spec("river_wraith", false, 0.80F, 2.05F, "float", "run"),
            e("drowned_commander", 1.05F, 2.60F),
            // 7 swamp
            n("bog_walker", 0.80F, 2.20F),
            n("swamp_leech", 0.80F, 0.45F),
            e("swamp_hexer", 1.00F, 2.60F),
            // 8 border
            n("border_swordsman", 0.75F, 2.10F),
            n("border_archer", 0.75F, 2.10F),
            e("border_captain", 1.20F, 2.55F),
            // 9 rift
            n("rift_beast", 1.30F, 1.30F),
            n("lesser_demon", 0.70F, 2.35F),
            e("rift_devourer", 2.20F, 2.10F));

    private static Spec n(String id, float w, float h) { return new Spec(id, false, w, h, "idle", "run"); }
    private static Spec e(String id, float w, float h) { return new Spec(id, true, w, h, "idle", "run"); }

    private static final Map<String, Spec> BY_ID = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<FieldMonster>>> TYPES = new LinkedHashMap<>();

    /** Registers onto the mod's existing entity register (call before it is bound to the bus). */
    public static void register(DeferredRegister<EntityType<?>> register) {
        for (Spec spec : SPECS) {
            BY_ID.put(spec.id(), spec);
            TYPES.put(spec.id(), register.register(spec.id(), () -> EntityType.Builder.<FieldMonster>of(
                            FieldMonster::new, MobCategory.MONSTER)
                    .sized(spec.width(), spec.height())
                    .clientTrackingRange(10)
                    .build(FantasyBosses.MOD_ID + ":" + spec.id())));
        }
    }

    public static Spec spec(String id) {
        Spec spec = BY_ID.get(id);
        if (spec == null) throw new IllegalArgumentException("Unknown field monster: " + id);
        return spec;
    }
}
