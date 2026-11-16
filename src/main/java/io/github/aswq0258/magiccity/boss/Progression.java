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
 *  - 최대 체력 배율은 설정 파일 config/magiccity-progression.json 의 hp_scale (기본 1.0 = 기존 체력 그대로, 미정 수치).
 *
 * 다른 모드(예: fantasyrpg)가 진행도를 쓰려면 {@link #completed(ServerPlayer)}, {@link #grade(ServerPlayer)},
 * {@link #levelCap(ServerPlayer)}, {@link #craftGrid(ServerPlayer)} 를 부르면 된다.
 */
public final class Progression {
    private Progression() {}

    public static final String GRADE_KEY = "magiccity_grade";
    public static final String SCALE_KEY = "magiccity_dmg_scale";

    /** 설정 파일 내용 */
    public static final class Config {
        /** false 면 보스 등급 조정을 하지 않음 (기존 수치 그대로) */
        public boolean enabled = true;
        /** 등급 1~6 의 보스 기본 공격 원본 피해 [초안] */
        public double[] boss_basic = ProgressionRules.BOSS_BASIC.clone();
        /** 등급 1~6 의 보스 최대 체력 배율 (보스마다 정해 둔 체력에 곱함) [미정 — 기본 1.0] */
        public double[] hp_scale = {1, 1, 1, 1, 1, 1};
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
        if (c == null || c.boss_basic == null || c.boss_basic.length < 6 || c.hp_scale == null || c.hp_scale.length < 6) {
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
        int best = 0;
        for (ServerPlayer p : party) best = Math.max(best, completed(p));
        int grade = ProgressionRules.bossGrade(chapter, best);
        Config c = config();
        if (!c.enabled) {
            boss.getPersistentData().putInt(GRADE_KEY, grade);
            return grade;
        }
        apply(boss, grade, c);
        Entity v = boss.getVehicle();
        if (v instanceof Mob mount) apply(mount, grade, c);
        return grade;
    }

    private static void apply(Mob m, int grade, Config c) {
        AttributeInstance atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        double design = atk != null ? atk.getBaseValue() : 0;
        double scale = design > 0 ? c.boss_basic[grade - 1] / design : 1.0;
        CompoundTag t = m.getPersistentData();
        t.putInt(GRADE_KEY, grade);
        t.putDouble(SCALE_KEY, scale);
        AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
        double hs = c.hp_scale[grade - 1];
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
        Entity src = event.getSource().getEntity();
        if (src == null) src = event.getSource().getDirectEntity();
        if (src == null) return;
        CompoundTag t = src.getPersistentData();
        if (!t.contains(SCALE_KEY)) return;
        double s = t.getDouble(SCALE_KEY);
        if (s > 0 && s != 1.0) event.setAmount((float) (event.getAmount() * s));
    }
}
