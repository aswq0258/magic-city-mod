package io.github.aswq0258.magiccity.quest;

import io.github.aswq0258.magiccity.api.ChapterFirstClearEvent;
import io.github.aswq0258.magiccity.api.NpcActionEvent;
import io.github.aswq0258.magiccity.api.QuestStepStartEvent;
import io.github.aswq0258.magiccity.boss.Story;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 개인 퀘스트 서버 처리 (초안 2·5절, 연동설계 D-2~D-4 최소판).
 * 모든 판정은 서버: 선택지 클릭은 {@code /quest npc ...} 명령으로 들어오지만 NPC 표식·거리 6칸·지금 단계를 다시 확인한다.
 */
public final class QuestService {
    private QuestService() {}

    /** NPC 선택지를 누를 수 있는 거리 */
    public static final double TALK_RANGE = 6.0;
    /** 진행 중 목표를 액션바로 다시 보여 주는 간격 (틱) */
    private static final int REMIND_TICKS = 600;

    private static QuestData data(ServerPlayer p) {
        return QuestData.get(p.server);
    }

    public static int state(ServerPlayer p, String quest) {
        return data(p).state(p.getUUID(), quest);
    }

    public static boolean done(ServerPlayer p, String quest) {
        QuestDef q = QuestManager.quest(quest);
        return q != null && QuestRules.done(q, state(p, quest));
    }

    private static Set<String> doneQuests(ServerPlayer p) {
        Set<String> out = new HashSet<>();
        for (QuestDef q : QuestManager.quests()) if (QuestRules.done(q, state(p, q.id()))) out.add(q.id());
        return out;
    }

    private static Set<Integer> clearedChapters(ServerPlayer p) {
        Set<Integer> out = new HashSet<>();
        for (int c = 1; c <= 12; c++) if (Story.cleared(p, c)) out.add(c);
        return out;
    }

    // ───────────────────────── 진행 ─────────────────────────

    /** 관리자·내부용: 진행 값을 바로 바꾸고, 진행 중 단계면 그 단계 시작 처리 */
    public static void setState(ServerPlayer p, QuestDef q, int state) {
        data(p).setState(p.getUUID(), q.id(), QuestRules.clamp(q, state));
        if (QuestRules.started(state)) enter(p, q, false);
    }

    private static void advance(ServerPlayer p, QuestDef q) {
        int s = state(p, q.id());
        if (!QuestRules.inProgress(q, s)) return;
        data(p).setState(p.getUUID(), q.id(), QuestRules.advance(q, s));
        enter(p, q, false);
    }

    /** 지금 단계 시작(또는 접속 때 다시 알림): 안내 → 이벤트 → 이미 만족한 조건 확인 */
    private static void enter(ServerPlayer p, QuestDef q, boolean login) {
        int s = state(p, q.id());
        if (QuestRules.done(q, s)) {
            if (!login) complete(p, q);
            return;
        }
        if (!QuestRules.inProgress(q, s)) return;
        QuestDef.Step st = q.steps().get(s);
        if (!login) {
            p.sendSystemMessage(Component.literal("§e[퀘스트] §f" + q.title() + " §7(" + (s + 1) + "/" + q.size() + ") §e목표: §f" + st.goal() + where(p, st)));
            p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        NeoForge.EVENT_BUS.post(new QuestStepStartEvent(p, q.id(), s, st.type().name().toLowerCase(Locale.ROOT), st.signal(), login));
        if (state(p, q.id()) != s) return;                    // 이벤트 안에서 다른 모드가 신호를 보내 이미 넘어감
        if (QuestRules.chapterCompletes(q, s, clearedChapters(p))) advance(p, q);
    }

    private static void complete(ServerPlayer p, QuestDef q) {
        QuestManager.NpcDef giver = QuestManager.npc(q.giver());
        for (String line : q.complete()) p.sendSystemMessage(say(giver, line));
        p.sendSystemMessage(Component.literal("§6[퀘스트 완료] §f" + q.title()));
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1F);
    }

    /** 다른 모드 신호 ({@link io.github.aswq0258.magiccity.api.MagicCityApi#questSignal}) */
    public static boolean signal(ServerPlayer p, String signal) {
        boolean any = false;
        for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
            if (QuestRules.signalMatches(q, state(p, q.id()), signal)) {
                advance(p, q);
                any = true;
            }
        }
        return any;
    }

    // ───────────────────────── 대화 ─────────────────────────

    /** NPC 오른쪽 클릭 */
    public static void talk(ServerPlayer p, Entity npc, String npcId) {
        QuestManager.NpcDef def = QuestManager.npc(npcId);
        // 1) 진행 중 퀘스트의 지금 단계가 이 NPC
        for (QuestDef q : QuestManager.quests()) {
            int s = state(p, q.id());
            QuestDef.Step st = QuestRules.current(q, s);
            if (st == null || !st.npc().equals(npcId)) continue;
            for (String line : st.lines()) p.sendSystemMessage(say(def, line));
            if (QuestRules.talkCompletes(q, s, npcId)) {
                advance(p, q);
            } else if (st.hasAction()) {
                p.sendSystemMessage(Component.literal("  ").append(button(st.actionLabel(), "/quest npc " + npc.getUUID() + " act " + q.id() + " " + st.actionId(), ChatFormatting.GREEN)));
            }
            return;
        }
        // 2) 이 NPC 가 건넬 수 있는 새 퀘스트
        Set<String> done = doneQuests(p);
        for (QuestDef q : QuestManager.quests()) {
            if (!QuestRules.offerable(q, state(p, q.id()), npcId, done)) continue;
            for (String line : q.offer()) p.sendSystemMessage(say(def, line));
            p.sendSystemMessage(Component.literal("  ")
                    .append(button(q.accept(), "/quest npc " + npc.getUUID() + " accept " + q.id(), ChatFormatting.GREEN))
                    .append(Component.literal("   "))
                    .append(button(q.decline(), "/quest npc " + npc.getUUID() + " decline", ChatFormatting.GRAY)));
            return;
        }
        // 3) 이 NPC 가 준 퀘스트가 진행 중이면 지금 목표를 다시 알려 줌
        for (QuestDef q : QuestManager.quests()) {
            int s = state(p, q.id());
            if (!q.giver().equals(npcId) || !QuestRules.inProgress(q, s)) continue;
            QuestDef.Step st = q.steps().get(s);
            p.sendSystemMessage(say(def, "할 일을 잊지 말게: " + st.goal() + where(p, st)));
            return;
        }
        // 4) 평소 대사
        List<String> idle = def.idle();
        p.sendSystemMessage(say(def, idle.isEmpty() ? "..." : idle.get(p.getRandom().nextInt(idle.size()))));
    }

    /**
     * 선택지 클릭 ({@code /quest npc <엔티티> accept|act|decline ...}). 명령으로 들어오므로 모든 조건을 다시 확인한다.
     * @return 실패 이유 (성공이면 null)
     */
    public static String choose(ServerPlayer p, UUID entity, String kind, String questId, String actionId) {
        Entity npc = p.serverLevel().getEntity(entity);
        String npcId = npc == null ? null : QuestNpcs.npcId(npc);
        if (npcId == null || !npc.isAlive()) return "그 NPC 를 찾을 수 없습니다.";
        if (npc.distanceToSqr(p) > TALK_RANGE * TALK_RANGE) return "NPC 에게 더 가까이 가야 합니다.";
        if (kind.equals("decline")) {
            p.sendSystemMessage(Component.literal("§7(마음이 정해지면 다시 말을 걸면 된다)"));
            return null;
        }
        QuestDef q = QuestManager.quest(questId);
        if (q == null) return "모르는 퀘스트입니다: " + questId;
        int s = state(p, q.id());
        switch (kind) {
            case "accept" -> {
                if (!QuestRules.offerable(q, s, npcId, doneQuests(p))) return "지금은 받을 수 없는 퀘스트입니다.";
                p.sendSystemMessage(Component.literal("§e[퀘스트] §f" + q.title() + " §e수락"));
                setState(p, q, 0);
                return null;
            }
            case "act" -> {
                if (!QuestRules.actionAllowed(q, s, npcId, actionId)) return "지금은 할 수 없는 선택입니다.";
                NeoForge.EVENT_BUS.post(new NpcActionEvent(p, npcId, q.id(), actionId));
                return null;
            }
            default -> {
                return "모르는 선택입니다.";
            }
        }
    }

    // ───────────────────────── 이벤트 ─────────────────────────

    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long t = server.overworld().getGameTime();
        if (t % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            String dim = p.level().dimension().location().toString();
            QuestDef remind = null;
            for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
                int s = state(p, q.id());
                if (!QuestRules.inProgress(q, s)) continue;
                if (QuestRules.areaCompletes(q, s, dim, p.getX(), p.getY(), p.getZ())) {
                    advance(p, q);
                    continue;
                }
                if (remind == null) remind = q;
            }
            if (remind != null && (t + p.getId() * 20L) % REMIND_TICKS == 0) {
                QuestDef.Step st = remind.steps().get(state(p, remind.id()));
                p.displayClientMessage(Component.literal("§e[" + remind.title() + "] §f" + st.goal() + where(p, st)), true);
            }
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
            if (QuestRules.inProgress(q, state(p, q.id()))) enter(p, q, true);
        }
        hint(p);
    }

    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) hint(p);
    }

    public static void onChapterClear(ChapterFirstClearEvent event) {
        ServerPlayer p = event.getPlayer();
        Set<Integer> cleared = clearedChapters(p);
        for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
            if (QuestRules.chapterCompletes(q, state(p, q.id()), cleared)) advance(p, q);
        }
    }

    /** 왕국 대륙에 들어올 때: 받지 않은 시작 퀘스트(hint 있는 것)의 NPC 를 찾아가라는 안내. NPC 자리를 알게 된 뒤로는 한 번만 */
    private static void hint(ServerPlayer p) {
        if (p.level().dimension() != ModWorldgen.KINGDOM) return;
        QuestData d = data(p);
        if (d.hinted.contains(p.getUUID())) return;
        for (QuestDef q : QuestManager.quests()) {
            if (q.hint().isEmpty() || QuestRules.started(state(p, q.id()))) continue;
            BlockPos at = QuestNpcs.position(p.server, q.giver());
            p.sendSystemMessage(Component.literal("§e[퀘스트] §f" + q.hint()
                    + (at != null ? " §7(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")" : "")
                    + " §8— /quest 로 진행 확인"));
            if (at != null) {                                   // NPC 가 아직 없으면(도시 건설 전) 다음에 다시 안내
                d.hinted.add(p.getUUID());
                d.setDirty();
            }
            return;
        }
    }

    // ───────────────────────── 표시 ─────────────────────────

    /** /quest: 진행 중·완료 퀘스트 */
    public static Component describe(ServerPlayer p) {
        StringBuilder sb = new StringBuilder("§6── 퀘스트 ──");
        int n = 0;
        for (QuestDef q : QuestManager.quests()) {
            int s = state(p, q.id());
            if (!QuestRules.started(s)) continue;
            n++;
            if (QuestRules.done(q, s)) {
                sb.append("\n§a✔ ").append(q.title());
            } else {
                QuestDef.Step st = q.steps().get(s);
                sb.append("\n§f○ ").append(q.title()).append(" §7(").append(s + 1).append("/").append(q.size()).append(") §e")
                        .append(st.goal()).append(where(p, st));
            }
        }
        if (n == 0) {
            sb.append("\n§7받은 퀘스트가 없습니다.");
            for (QuestDef q : QuestManager.quests()) {
                if (q.hint().isEmpty()) continue;
                BlockPos at = QuestNpcs.position(p.server, q.giver());
                sb.append("\n§7").append(q.hint()).append(at != null ? " (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")" : "");
            }
        }
        return Component.literal(sb.toString());
    }

    /** 목표 좌표: 단계에 좌표가 있으면 그것, 아니면 단계 NPC 자리 */
    private static String where(ServerPlayer p, QuestDef.Step st) {
        if (st.hasPos()) return " §7(" + st.x() + ", " + st.z() + ")";
        if (!st.npc().isEmpty()) {
            BlockPos at = QuestNpcs.position(p.server, st.npc());
            if (at != null) return " §7(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
        }
        return "";
    }

    private static Component say(QuestManager.NpcDef npc, String line) {
        return Component.literal("§6[" + npc.name() + "] §f" + line);
    }

    private static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("클릭해서 선택"))));
    }
}
