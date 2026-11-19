package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 메인 스토리 12챕터 진행 (초안).
 *  - 챕터 보스를 처음 쓰러뜨릴 때 64칸 안에 있던 플레이어 모두에게 그 챕터가 "완료"로 기록됨 (서버 저장)
 *  - 2·3·5·6·9장 보스 = 다섯 봉인 (숲·생명·대지·바다·하늘), 11장 = 왕관 봉인
 *  - 입장 조건: 1장(서장)은 처음에 반드시 진행. 2~10장은 서장을 끝낸 뒤 자유 순서.
 *    11장 고룡은 1~10장 모두 완료 + 봉인 5개, 12장 마계문은 왕관 봉인(11장 완료)이 있어야 깨어남
 *  - 진행도(처음 완료한 챕터 수)로 등급·레벨 상한·작업대 크기가 정해짐 → {@link Progression}, {@link ProgressionRules}
 *  - /magiccity story 로 진행도·다음 목적지 확인
 */
public final class Story {
    private Story() {}

    /** 챕터: 번호, 제목, 지역, 보스, 목적지 좌표 {x, z}, 봉인 이름 (없으면 null), 봉인 아이템 */
    public record Chapter(int n, String title, String place, String boss, int x, int z, String seal, Item sealItem) {}

    public static final List<Chapter> CHAPTERS = List.of(
            new Chapter(1, "서장: 왕의 소명", "왕성도시 지하 하수도 납골당", "하수도의 역병 술사", 260, 259, null, null),
            new Chapter(2, "시드는 숲", "저주받은 숲 제단", "저주의 마녀왕", io.github.aswq0258.magiccity.site.CursedLands.ALTAR_X, io.github.aswq0258.magiccity.site.CursedLands.ALTAR_Z, "숲의 봉인", Items.EMERALD),
            new Chapter(3, "썩어가는 뿌리", "세계수 뿌리 던전 기생충의 둥지", "세계수 기생충", io.github.aswq0258.magiccity.site.RootDungeon.OX, io.github.aswq0258.magiccity.site.RootDungeon.OZ, "생명의 봉인", Items.AMETHYST_SHARD),
            new Chapter(4, "서리의 행군", "엘프 관문 북쪽 서리 결투장", "서리 거인 요툰", io.github.aswq0258.magiccity.site.FrostArena.AX, io.github.aswq0258.magiccity.site.FrostArena.AZ, null, null),
            new Chapter(5, "꺼지지 않는 용광로", "드워프 심층 광산 고대 용광로 홀", "용광로의 거신", NewBosses.Furnace.FX, NewBosses.Furnace.FZ, "대지의 봉인", Items.RAW_GOLD),
            new Chapter(6, "잠긴 성소", "등대 섬 앞바다 잠긴 성소", "심해의 수호자", NewBosses.DeepGuardian.SX, NewBosses.DeepGuardian.SZ, "바다의 봉인", Items.PRISMARINE_CRYSTALS),
            new Chapter(7, "잊혀진 왕", "고대 왕국 유적 왕릉", "고대왕 아르케온", io.github.aswq0258.magiccity.site.AncientRuins.CX, io.github.aswq0258.magiccity.site.AncientRuins.CZ, null, null),
            new Chapter(8, "국경의 밤", "남부 국경도시 남쪽 들판", "약탈 군주 그림발드", NewBosses.Siege.FX, NewBosses.Siege.FZ, null, null),
            new Chapter(9, "하늘의 시련", "부유섬 유적 결투장", "폭풍의 수호자", io.github.aswq0258.magiccity.site.SkyRuins.ARENA_X, io.github.aswq0258.magiccity.site.SkyRuins.ARENA_Z, "하늘의 봉인", Items.FEATHER),
            new Chapter(10, "심연의 속삭임", "심연 균열 바닥", "심연의 감시자", io.github.aswq0258.magiccity.site.CursedLands.RX, io.github.aswq0258.magiccity.site.CursedLands.RZ, null, null),
            new Chapter(11, "용의 불꽃", "고룡 봉우리 보물 동굴", "고룡 이그나르", io.github.aswq0258.magiccity.site.DragonLair.MX, io.github.aswq0258.magiccity.site.DragonLair.MZ, "왕관 봉인", Items.NETHER_STAR),
            new Chapter(12, "마계문", "몰락한 옛 왕도", "심연의 문지기", io.github.aswq0258.magiccity.site.OldCapital.OX, io.github.aswq0258.magiccity.site.OldCapital.OZ, null, null));

    /** 다섯 봉인 챕터 */
    private static final int[] SEALS = {2, 3, 5, 6, 9};

    // ───────────────────────── 저장 ─────────────────────────

    public static final class Data extends SavedData {
        final Map<UUID, Integer> done = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider p) {
            Data d = new Data();
            for (String k : tag.getAllKeys()) {
                try {
                    d.done.put(UUID.fromString(k), tag.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            for (var e : done.entrySet()) tag.putInt(e.getKey().toString(), e.getValue());
            return tag;
        }
    }

    private static Data data(MinecraftServer server) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        ServerLevel l = k != null ? k : server.overworld();
        return l.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "magiccity_story");
    }

    public static int mask(ServerPlayer p) {
        return data(p.server).done.getOrDefault(p.getUUID(), 0);
    }

    public static boolean cleared(ServerPlayer p, int chapter) {
        return (mask(p) & (1 << chapter)) != 0;
    }

    public static int seals(ServerPlayer p) {
        int n = 0;
        for (int c : SEALS) if (cleared(p, c)) n++;
        return n;
    }

    public static void set(ServerPlayer p, int chapter, boolean on) {
        Data d = data(p.server);
        int m = d.done.getOrDefault(p.getUUID(), 0);
        d.done.put(p.getUUID(), on ? m | (1 << chapter) : m & ~(1 << chapter));
        d.setDirty();
    }

    /**
     * 처음 완료일 때만 기록하고 {@link io.github.aswq0258.magiccity.api.ChapterFirstClearEvent} 를 발행한다.
     * 이미 완료한 챕터면 아무것도 하지 않고 false. 서버 스레드에서만 불리므로 같은 완료가 두 번 발행되지 않는다.
     */
    public static boolean recordFirstClear(ServerPlayer p, int chapter) {
        if (chapter < 1 || chapter > 12 || cleared(p, chapter)) return false;
        int before = ProgressionRules.completed(mask(p));
        set(p, chapter, true);
        int after = ProgressionRules.completed(mask(p));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new io.github.aswq0258.magiccity.api.ChapterFirstClearEvent(p, chapter,
                before, after,
                ProgressionRules.grade(before), ProgressionRules.grade(after),
                ProgressionRules.levelCap(before), ProgressionRules.levelCap(after),
                ProgressionRules.craftGrid(before), ProgressionRules.craftGrid(after)));
        return true;
    }

    public static void reset(ServerPlayer p) {
        data(p.server).done.remove(p.getUUID());
        data(p.server).setDirty();
    }

    // ───────────────────────── 보스 처치·입장 조건 ─────────────────────────

    /** 챕터 보스를 쓰러뜨림: 64칸 안 플레이어에게 완료 기록 + 처음이면 봉인 아이템과 화면 제목 */
    public static void credit(ServerLevel level, BlockPos at, int chapter) {
        List<ServerPlayer> near = new java.util.ArrayList<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.blockPosition().distSqr(at) <= 64 * 64) near.add(p);
        creditPlayers(near, chapter);
    }

    /** 전장 참가자에게 장 완료 기록 (2026-11-19 인스턴스 전장: 관전 중인 쓰러진 참가자 포함) */
    public static void creditPlayers(List<ServerPlayer> players, int chapter) {
        Chapter ch = CHAPTERS.get(chapter - 1);
        for (ServerPlayer p : players) {
            if (!recordFirstClear(p, chapter)) continue;                                // 반복 처치는 진행도에 다시 반영하지 않음
            int after = Progression.completed(p);
            int before = after - 1;
            title(p, "§6" + chapter + "장 완료", "§f" + ch.title());
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
            if (ch.seal() != null) {
                p.getInventory().placeItemBackInInventory(sealItem(ch));
                p.sendSystemMessage(Component.literal("§e[봉인] " + ch.seal() + "(을)를 되찾았다! §7(" + seals(p) + "/5)"));
            }
            progressMessages(p, before, after);
            Chapter next = nextFor(p);
            if (next != null) p.sendSystemMessage(Component.literal("§7다음 목적지: §f" + next.n() + "장 " + next.title() + " §7— " + next.place() + " (" + next.x() + ", " + next.z() + ")"));
        }
    }

    /**
     * 보스를 깨워도 되는지. 11장 = 봉인 5개, 12장 = 11장 완료. 조건이 안 되는 플레이어에게는 액션바로 알림.
     * @param trigger 보스 자리에 들어선 플레이어들
     */
    public static boolean mayWake(ServerLevel level, List<ServerPlayer> trigger, int chapter) {
        boolean ok = false;
        for (ServerPlayer p : trigger) {
            if (requirementMet(p, chapter)) ok = true;
            else if (level.getGameTime() % 60 == 0) p.displayClientMessage(Component.literal(requirementText(p, chapter)), true);
        }
        return ok;
    }

    public static boolean requirementMet(ServerPlayer p, int chapter) {
        int m = mask(p);
        if (chapter == 1) return true;
        if (chapter <= 10) return ProgressionRules.freeChapterOpen(m);
        if (chapter == 11) return ProgressionRules.chapter11Open(m, seals(p));
        if (chapter == 12) return cleared(p, 11);
        return true;
    }

    /** 1~10장 중 완료한 수 */
    public static int clearedMain(ServerPlayer p) {
        int n = 0;
        for (int c = 1; c <= 10; c++) if (cleared(p, c)) n++;
        return n;
    }

    public static String requirementText(ServerPlayer p, int chapter) {
        if (chapter <= 10) return "§e먼저 서장을 끝내야 한다 §7(1장 — 왕성도시 지하 하수도 납골당)";
        if (chapter == 11) return "§6고룡은 모든 시련을 마치고 다섯 봉인을 가진 자에게만 깨어난다 §7(1~10장 " + clearedMain(p) + "/10, 봉인 " + seals(p) + "/5)";
        return "§5마계문을 닫으려면 왕관 봉인이 필요하다 §7(11장 고룡 이그나르)";
    }

    /** 처음 완료로 진행도가 올랐을 때: 등급·레벨 상한·작업대 해금 알림 */
    private static void progressMessages(ServerPlayer p, int before, int after) {
        int g0 = ProgressionRules.grade(before), g1 = ProgressionRules.grade(after);
        if (g1 > g0) p.sendSystemMessage(Component.literal("§b[성장] 진행 단계 " + g1 + "급 — 레벨 상한 Lv." + ProgressionRules.levelCap(after)
                + " §7(다음 챕터 보스는 " + g1 + "급으로 맞섭니다)"));
        int w0 = ProgressionRules.craftGrid(before), w1 = ProgressionRules.craftGrid(after);
        if (w1 > w0) p.sendSystemMessage(Component.literal("§b[성장] " + w1 + "×" + w1 + " 작업대 제작법이 해금되었다!"));
    }

    public static ItemStack sealItem(Chapter ch) {
        ItemStack s = new ItemStack(ch.sealItem());
        s.set(DataComponents.CUSTOM_NAME, Component.literal("§6" + ch.seal()));
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7" + ch.n() + "장 " + ch.title() + " — " + ch.boss() + "에게서 되찾음"),
                Component.literal("§8마계문을 닫는 데 필요한 봉인의 조각"))));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private static void title(ServerPlayer p, String t, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
    }

    /** 아직 안 끝낸 챕터 중 갈 수 있는 가장 앞 번호 */
    public static Chapter nextFor(ServerPlayer p) {
        for (Chapter c : CHAPTERS) if (!cleared(p, c.n()) && requirementMet(p, c.n())) return c;
        return null;
    }

    /** /magiccity story 출력 */
    public static Component describe(ServerPlayer p) {
        int done0 = Progression.completed(p);
        StringBuilder sb = new StringBuilder("§6§l[마법 왕국 대륙 — 메인 스토리]§r §7봉인 " + seals(p) + "/5");
        sb.append("\n§b진행 ").append(done0).append("/12장 · ").append(ProgressionRules.grade(done0)).append("급 · 레벨 상한 Lv.")
                .append(ProgressionRules.levelCap(done0)).append(" · 작업대 ").append(ProgressionRules.craftGrid(done0)).append("×")
                .append(ProgressionRules.craftGrid(done0));
        for (Chapter c : CHAPTERS) {
            boolean done = cleared(p, c.n());
            boolean open = requirementMet(p, c.n());
            sb.append("\n").append(done ? "§a✔ " : open ? "§f○ " : "§8✖ ").append(c.n()).append("장 ").append(c.title())
                    .append(" §7— ").append(c.boss()).append(" §8(").append(c.x()).append(", ").append(c.z()).append(")");
            if (c.seal() != null) sb.append(done ? " §e" : " §8").append("[").append(c.seal()).append("]");
        }
        Chapter next = nextFor(p);
        sb.append(next == null ? "\n§6모든 챕터를 끝냈다! 마계문 너머(2부)가 기다린다." : "\n§e다음: " + next.n() + "장 " + next.place());
        return Component.literal(sb.toString());
    }
}
