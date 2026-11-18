package io.github.aswq0258.magiccity.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

/**
 * 퀘스트 명령.
 * <ul>
 *   <li>{@code /quest} (권한 0) — 내 퀘스트 진행</li>
 *   <li>{@code /quest npc <엔티티> accept <퀘스트> | act <퀘스트> <행동> | decline} (권한 0) — 대화 선택지가 보내는 명령.
 *       서버가 NPC 표식·거리·단계를 다시 확인하므로 직접 쳐도 할 수 있는 것은 같음 (D-3 보안 규칙)</li>
 *   <li>{@code /magiccity quest list|start|set|reset|signal} (권한 2) — 시험용</li>
 * </ul>
 */
public final class QuestCommand {
    private QuestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("quest")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> QuestService.describe(p), false);
                    return 1;
                })
                .then(Commands.literal("npc").then(Commands.argument("npc", UuidArgument.uuid())
                        .then(Commands.literal("decline").executes(c -> choose(c, "decline", "", "")))
                        .then(Commands.literal("accept").then(Commands.argument("quest", word())
                                .executes(c -> choose(c, "accept", getString(c, "quest"), ""))))
                        .then(Commands.literal("act").then(Commands.argument("quest", word()).then(Commands.argument("action", word())
                                .executes(c -> choose(c, "act", getString(c, "quest"), getString(c, "action")))))))));
    }

    private static int choose(CommandContext<CommandSourceStack> c, String kind, String quest, String action) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String err = QuestService.choose(p, UuidArgument.getUuid(c, "npc"), kind, quest, action);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    /** /magiccity quest ... (권한은 /magiccity 가 2) */
    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("quest")
                .then(Commands.literal("list").executes(c -> {
                    StringBuilder sb = new StringBuilder("§6[퀘스트] 정의 " + QuestManager.quests().size() + "개");
                    ServerPlayer p = c.getSource().getPlayer();
                    for (QuestDef q : QuestManager.quests()) {
                        sb.append("\n§f").append(q.id()).append(" §7— ").append(q.title()).append(", 단계 ").append(q.size())
                                .append(", NPC ").append(q.giver());
                        if (p != null) sb.append(", 내 진행 ").append(QuestService.state(p, q.id()));
                    }
                    c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("start").then(questArg().executes(c -> set(c, 0))))
                .then(Commands.literal("set").then(questArg().then(Commands.argument("step", integer(-1, 64))
                        .executes(c -> set(c, getInteger(c, "step"))))))
                .then(Commands.literal("reset").then(questArg().executes(c -> set(c, QuestRules.NOT_STARTED))))
                .then(Commands.literal("signal").then(Commands.argument("signal", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(c -> {
                            boolean any = QuestService.signal(c.getSource().getPlayerOrException(), getString(c, "signal"));
                            c.getSource().sendSuccess(() -> Component.literal(any ? "§e[퀘스트] 신호로 단계를 넘김" : "§7[퀘스트] 이 신호를 기다리는 단계가 없음"), false);
                            return any ? 1 : 0;
                        })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> questArg() {
        return Commands.argument("quest", word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(QuestManager.quests().stream().map(QuestDef::id), b));
    }

    private static int set(CommandContext<CommandSourceStack> c, int step) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestDef q = QuestManager.quest(getString(c, "quest"));
        if (q == null) {
            c.getSource().sendFailure(Component.literal("모르는 퀘스트입니다. /magiccity quest list"));
            return 0;
        }
        QuestService.setState(p, q, step);
        int now = QuestService.state(p, q.id());
        c.getSource().sendSuccess(() -> Component.literal("§e[퀘스트] " + q.id() + " 진행 값 = " + now + " (−1 받지 않음, " + q.size() + " 완료)"), true);
        return 1;
    }
}
