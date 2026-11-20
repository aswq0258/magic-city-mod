package io.github.aswq0258.magiccity.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 진행도 기반 챕터 보스 등급 (게임 쪽). 규칙 계산은 {@link ProgressionRules}.
 *
 *  - 보스가 깨어날 때(전투 시작) 그 자리 플레이어들 중 가장 높은 진행도로 등급을 정하고, 전투가 끝날 때까지 바꾸지 않는다.
 *  - 등급은 보스 엔티티의 저장 데이터에 남는다 (magiccity_grade, magiccity_dmg_scale).
 *  - 보스(와 탈것)가 플레이어에게 주는 피해는 모두 등급 배율을 곱한다 (근접·기술·소환 투사체의 주인이 보스인 경우).
 *  - 등급별 보스 기본 공격·최대 체력 배율은 데이터 data/magiccity/boss_stats/_grades.json (기본 8/11/14/15/17/18, 체력 1.0).
 *    설정 파일 config/magiccity-progression.json 에는 enabled(등급 조정 켜고 끄기)만 남음.
 *
 * 다른 모드(예: fantasyrpg)가 진행도를 쓰려면 {@link #completed(ServerPlayer)}, {@link #grade(ServerPlayer)},
 * {@link #levelCap(ServerPlayer)}, {@link #craftGrid(ServerPlayer)} 를 부르면 된다.
 */
public final class Progression {
    private Progression() {}

    public static final String GRADE_KEY = "magiccity_grade";
    public static final String SCALE_KEY = "magiccity_dmg_scale";

    /**
     * 설정 파일 내용. 2026-11-20부터 등급별 보스 기본 공격·체력 배율은 데이터 {@code data/magiccity/boss_stats/_grades.json} 으로 옮겼고
     * 설정 파일에는 켜고 끄기만 남는다. 예전 파일에 있는 boss_basic·hp_scale 은 읽기만 하고 쓰지 않는다(로그 안내).
     */
    public static final class Config {
        /** false 면 보스 등급 조정을 하지 않음 (기존 수치 그대로) */
        public boolean enabled = true;
        /** 예전 항목 (쓰지 않음) */
        public double[] boss_basic;
        /** 예전 항목 (쓰지 않음) */
        public double[] hp_scale;
        /** 보스전 투표 때 파티원 등급이 보스보다 이만큼 이상 낮으면 경고 [초안], 0 = 끔 (2026-11-21) */
        public int warn_grade_gap = 2;
    }

    private static Config config;

    public static Config config() {
        if (config == null) load();
        return config;
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("magiccity-progression.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Config c = null;
        try {
            if (Files.exists(file)) c = gson.fromJson(Files.readString(file), Config.class);
        } catch (Exception e) {
            MagicCityMod.LOGGER.warn("magiccity-progression.json 을 읽지 못해 기본값을 씁니다: {}", e.toString());
        }
        if (c != null && (c.boss_basic != null || c.hp_scale != null))
            MagicCityMod.LOGGER.info("magiccity-progression.json 의 boss_basic·hp_scale 은 이제 쓰지 않습니다 — 데이터 boss_stats/_grades.json 으로 옮겨졌습니다 (데이터팩으로 덮어쓰기).");
        if (c == null) {
            c = new Config();
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, gson.toJson(c));
            } catch (Exception e) {
                MagicCityMod.LOGGER.warn("magiccity-progression.json 을 쓰지 못했습니다: {}", e.toString());
            }
        }
        config = c;
    }

    // ── 플레이어 진행도 (다른 모드도 쓸 수 있는 공개 API)

    public static int completed(ServerPlayer p) {
        return ProgressionRules.completed(Story.mask(p));
    }

    public static int grade(ServerPlayer p) {
        return ProgressionRules.grade(completed(p));
    }

    public static int levelCap(ServerPlayer p) {
        return ProgressionRules.levelCap(completed(p));
    }

    public static int craftGrid(ServerPlayer p) {
        return ProgressionRules.craftGrid(completed(p));
    }

    // ── 보스 등급

    /** 전투 시작 시 한 번: 파티 최고 진행도로 등급을 정해 보스(와 탈것)에 기록하고 체력을 맞춘다. 정한 등급을 돌려준다. */
    public static int apply(Mob boss, int chapter, List<ServerPlayer> party) {
        return apply(boss, chapter, party, true);
    }

    /** scaling=false 면 등급만 기록하고 배율은 곱하지 않음 (외부 보스 연결의 grade_scaling) */
    public static int apply(Mob boss, int chapter, List<ServerPlayer> party, boolean scaling) {
        return apply(boss, chapter, party, scaling, null);
    }

    /** design = 등급 배율 기준 기본 공격 (외부 보스 design_attack, null 이면 공격력 속성) */
    public static int apply(Mob boss, int chapter, List<ServerPlayer> party, boolean scaling, Double design) {
        int best = 0;
        for (ServerPlayer p : party) best = Math.max(best, completed(p));
        int grade = ProgressionRules.bossGrade(chapter, best);
        Config c = config();
        if (!c.enabled || !scaling) {
            boss.getPersistentData().putInt(GRADE_KEY, grade);
            return grade;
        }
        apply(boss, grade, c, design);
        Entity v = boss.getVehicle();
        if (v instanceof Mob mount) apply(mount, grade, c, null);
        return grade;
    }

    private static void apply(Mob m, int grade, Config c, Double designOverride) {
        BossDataRules.Grades g = BossData.grades();                                          // data/magiccity/boss_stats/_grades.json
        AttributeInstance atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        double design = designOverride != null ? designOverride : atk != null ? atk.getBaseValue() : 0;
        double scale = g.damageScale(grade, design);
        CompoundTag t = m.getPersistentData();
        t.putInt(GRADE_KEY, grade);
        t.putDouble(SCALE_KEY, scale);
        AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
        double hs = g.hpScale(grade);
        if (hp != null && hs > 0 && hs != 1.0) {
            hp.setBaseValue(Math.max(1, hp.getBaseValue() * hs));
            m.setHealth(m.getMaxHealth());
        }
    }

    /** 보스에 기록된 등급 (없으면 0) */
    public static int gradeOf(Entity e) {
        return e == null ? 0 : e.getPersistentData().getInt(GRADE_KEY);
    }

    /** 보스가 플레이어에게 주는 피해에 등급 배율을 곱함 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (BossPatterns.isFixed(event.getSource())) return; // 고정 피해 패턴은 크기를 따로 정함 (밸런스 6-1절)
        Entity src = event.getSource().getEntity();
        if (src == null) src = event.getSource().getDirectEntity();
        if (src == null) return;
        // 외부 보스(라디키온 등)의 방어구 무시 마법 공격은 배율 없음 — 고정 피해 패턴과 같은 원칙 (2026-11-20 사용자 결정)
        if (BossData.isExternal(src) && ProgressionRules.skipScaling(true, event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR))) return;
        CompoundTag t = src.getPersistentData();
        if (!t.contains(SCALE_KEY)) return;
        double s = t.getDouble(SCALE_KEY);
        if (s > 0 && s != 1.0) event.setAmount((float) (event.getAmount() * s));
    }
}
