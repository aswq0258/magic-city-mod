package io.github.aswq0258.magiccity.quest;

import io.github.aswq0258.magiccity.api.ChapterFirstClearEvent;
import io.github.aswq0258.magiccity.api.NpcActionEvent;
import io.github.aswq0258.magiccity.api.QuestStepCompleteEvent;
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
        if (QuestRules.started(state)) data(p).setVersion(p.getUUID(), q.id(), q.version());
        if (QuestRules.started(state)) enter(p, q, false);
    }

    /** 플레이어가 지금 단계를 끝냄: 단계 완료 이벤트 → 다음 단계 */
    private static void advance(ServerPlayer p, QuestDef q) {
        int s = state(p, q.id());
        if (!QuestRules.inProgress(q, s)) return;
        QuestDef.Step st = q.steps().get(s);
        NeoForge.EVENT_BUS.post(new QuestStepCompleteEvent(p, q.id(), s, st.type().name().toLowerCase(Locale.ROOT), q.chapter(), QuestRules.midStep(q, s)));
        if (state(p, q.id()) != s) return;                    // 이벤트 안에서 이미 바뀜
        data(p).setState(p.getUUID(), q.id(), QuestRules.advance(q, s));
        enter(p, q, false);
    }

    /** 장을 깨서 남은 단계를 건너뛰고 완료 (중간 단계 보상·이벤트 없음) */
    private static boolean skipIfCleared(ServerPlayer p, QuestDef q, Set<Integer> cleared) {
        int s = state(p, q.id());
        int ns = QuestRules.skipOnChapter(q, s, cleared);
        if (ns == s) return false;
        data(p).setState(p.getUUID(), q.id(), ns);
        complete(p, q);
        return true;
    }

    /** 여러 개 중 하나를 끝냄: 표시값 저장, 진행 알림, 모두 끝났으면 다음 단계 */
    private static void part(ServerPlayer p, QuestDef q, int newMask, String what) {
        QuestData d = data(p);
        int old = d.mark(p.getUUID(), q.id());
        if (newMask == old) return;
        int s = state(p, q.id());
        QuestDef.Step st = q.steps().get(s);
        d.setMark(p.getUUID(), q.id(), newMask);
        p.sendSystemMessage(Component.literal("§e[" + q.title() + "] §f" + what + " §7(" + QuestRules.partsCount(newMask) + "/" + st.parts() + ")"));
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.5F);
        if (QuestRules.partsDone(q, s, newMask)) advance(p, q);
    }

    /** 블록 오른쪽 클릭: use_blocks 단계의 자리면 표시 (레버 당기기 등은 그대로 일어남) */
    public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (p.level().dimension() != ModWorldgen.KINGDOM) return;
        BlockPos b = event.getPos();
        for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
            int s = state(p, q.id());
            int nm = QuestRules.useBlocksMark(q, s, data(p).mark(p.getUUID(), q.id()), b.getX(), b.getY(), b.getZ());
            if (nm >= 0) part(p, q, nm, q.steps().get(s).goal());
        }
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
        Set<Integer> cleared = clearedChapters(p);
        if (skipIfCleared(p, q, cleared)) return;
        if (QuestRules.chapterCompletes(q, s, cleared)) advance(p, q);
    }

    private static void complete(ServerPlayer p, QuestDef q) {
        QuestManager.NpcDef giver = QuestManager.npc(q.giver());
        for (String line : q.complete()) p.sendSystemMessage(say(giver, line));
        p.sendSystemMessage(Component.literal("§6[퀘스트 완료] §f" + q.title()));
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1F);
        announceAvailable(p);
    }

    /** 받을 수 있는 퀘스트 (어느 NPC 든) */
    private static List<QuestDef> available(ServerPlayer p) {
        Set<String> done = doneQuests(p);
        Set<Integer> cleared = clearedChapters(p);
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : QuestManager.quests()) if (QuestRules.available(q, state(p, q.id()), done, cleared)) out.add(q);
        return out;
    }

    private static void announceAvailable(ServerPlayer p) {
        int n = available(p).size();
        if (n > 0) p.sendSystemMessage(Component.literal("§e[퀘스트] 왕국 곳곳에 그대를 찾는 사람들이 있다 — 받을 수 있는 의뢰 " + n + "개 §8(/quest)"));
    }

    /** NPC 자리: 소환된 자리, 없으면 데이터의 거점 좌표 */
    private static String npcWhere(net.minecraft.server.MinecraftServer server, String npcId) {
        BlockPos at = QuestNpcs.position(server, npcId);
        if (at != null) return " (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
        QuestManager.NpcDef def = QuestManager.npc(npcId);
        int[] a = def.spawnAnchor().isEmpty() ? null : QuestAnchors.resolve(def.spawnAnchor());
        return a == null ? "" : " (" + a[0] + ", " + a[1] + ")";
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

    /** NPC 오른쪽 클릭: 퀘스트 대화 뒤에 상시 선택지(services)를 붙임 */
    public static void talk(ServerPlayer p, Entity npc, String npcId) {
        talkQuest(p, npc, npcId);
        QuestManager.NpcDef def = QuestManager.npc(npcId);
        if (def.services().isEmpty()) return;
        MutableComponent row = Component.literal("  ");
        for (QuestManager.Service s : def.services()) {
            row.append(button(s.label(), "/quest npc " + npc.getUUID() + " service " + s.id(), ChatFormatting.AQUA)).append(Component.literal("  "));
        }
        p.sendSystemMessage(row);
    }

    private static void talkQuest(ServerPlayer p, Entity npc, String npcId) {
        QuestManager.NpcDef def = QuestManager.npc(npcId);
        // 0) 여러 NPC 를 만나는 단계 (구출 등): 이 NPC 의 평소 대사 + 표시
        for (QuestDef q : new ArrayList<>(QuestManager.quests())) {
            int s = state(p, q.id());
            int mask = data(p).mark(p.getUUID(), q.id());
            int nm = QuestRules.talkNpcsMark(q, s, mask, npcId);
            if (nm < 0) continue;
            List<String> idle = def.idle();
            if (nm != mask) {
                for (String line : idle) p.sendSystemMessage(say(def, line));
                part(p, q, nm, def.name());
            } else {
                p.sendSystemMessage(say(def, idle.isEmpty() ? "..." : idle.get(idle.size() - 1)));
            }
            return;
        }
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
        Set<Integer> cleared = clearedChapters(p);
        for (QuestDef q : QuestManager.quests()) {
            if (!QuestRules.offerable(q, state(p, q.id()), npcId, done, cleared)) continue;
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
        if (kind.equals("service")) {
            if (!QuestManager.npc(npcId).offers(actionId)) return "이 NPC 에게는 그런 선택지가 없습니다.";
            NeoForge.EVENT_BUS.post(new NpcActionEvent(p, npcId, "", actionId));
            return null;
        }
        if (kind.equals("decline")) {
            p.sendSystemMessage(Component.literal("§7(마음이 정해지면 다시 말을 걸면 된다)"));
            return null;
        }
        QuestDef q = QuestManager.quest(questId);
        if (q == null) return "모르는 퀘스트입니다: " + questId;
        int s = state(p, q.id());
        switch (kind) {
            case "accept" -> {
                if (!QuestRules.offerable(q, s, npcId, doneQuests(p), clearedChapters(p))) return "지금은 받을 수 없는 퀘스트입니다.";
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
        if (t % 40 == 0) QuestNpcs.tickPlacement(server);
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
            int s = state(p, q.id());
            if (QuestRules.needsRestart(q, s, data(p).version(p.getUUID(), q.id()))) {
                p.sendSystemMessage(Component.literal("§e[퀘스트] §f" + q.title() + " §7— 의뢰 내용이 바뀌어 첫 단계부터 다시 진행합니다."));
                data(p).setState(p.getUUID(), q.id(), 0);
                data(p).setVersion(p.getUUID(), q.id(), q.version());
                enter(p, q, false);
                continue;
            }
            if (QuestRules.inProgress(q, s)) enter(p, q, true);
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
            if (skipIfCleared(p, q, cleared)) continue;
            if (QuestRules.chapterCompletes(q, state(p, q.id()), cleared)) advance(p, q);
        }
        if (event.getChapter() == 10 || event.getChapter() == 11) announceAvailable(p);   // 11·12장 의뢰가 새로 열림
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
                if (st.parts() > 0) sb.append(" §7[").append(QuestRules.partsCount(data(p).mark(p.getUUID(), q.id()))).append("/").append(st.parts()).append("]");
            }
        }
        if (n == 0) sb.append("\n§7받은 퀘스트가 없습니다.");
        List<QuestDef> av = available(p);
        if (!av.isEmpty()) {
            sb.append("\n§6── 받을 수 있는 의뢰 ──");
            for (QuestDef q : av) {
                sb.append("\n§e◇ ").append(q.title()).append(" §7— ").append(QuestManager.npc(q.giver()).name())
                        .append(npcWhere(p.server, q.giver()));
            }
        }
        return Component.literal(sb.toString());
    }

    /** 목표 좌표: 단계에 좌표가 있으면 그것, 아니면 단계 NPC 자리 */
    private static String where(ServerPlayer p, QuestDef.Step st) {
        if (st.hasPos()) return " §7(" + st.x() + ", " + st.z() + ")";
        if (st.type() == QuestDef.Type.USE_BLOCKS && !st.blockPos().isEmpty()) {
            int[] b = st.blockPos().get(0);
            return " §7(" + b[0] + ", " + b[1] + ", " + b[2] + " 근처)";
        }
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
