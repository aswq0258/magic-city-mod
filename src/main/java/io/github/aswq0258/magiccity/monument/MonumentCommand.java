package io.github.aswq0258.magiccity.monument;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.aswq0258.magiccity.build.CityBuildManager;
import io.github.aswq0258.magiccity.build.CityRecord;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * /magiccity monument list                — 웅장화 묶음과 적용 기록
 * /magiccity monument survey <id>         — 월드를 바꾸지 않고 요소별 가능/막힘 보고
 * /magiccity monument apply <id>          — 검사를 통과한 요소만 덧짓기 (이전 블록 백업)
 * /magiccity monument rollback <id>       — 백업대로 되돌리기 (적용 뒤 바뀐 칸은 그대로)
 * /magiccity monument status | cancel
 */
public final class MonumentCommand {
    private MonumentCommand() {}

    public static final List<Monument> ALL = List.of(new CapitalMonuments());

    static Monument find(String id) {
        for (Monument m : ALL) if (m.id().equals(id)) return m;
        return null;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("magiccity").requires(s -> s.hasPermission(2))
                .then(Commands.literal("monument")
                        .then(Commands.literal("list").executes(MonumentCommand::list))
                        .then(Commands.literal("status").executes(ctx -> {
                            String st = MonumentRunner.status();
                            ctx.getSource().sendSuccess(() -> Component.literal(st == null ? "[웅장화] 진행 중인 작업 없음" : "[웅장화] " + st), false);
                            return 1;
                        }))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            boolean c = MonumentRunner.cancel();
                            if (!c) ctx.getSource().sendSuccess(() -> Component.literal("[웅장화] 진행 중인 작업 없음"), false);
                            return 1;
                        }))
                        .then(Commands.literal("survey").then(Commands.argument("id", StringArgumentType.word()).executes(MonumentCommand::survey)))
                        .then(Commands.literal("apply").then(Commands.argument("id", StringArgumentType.word()).executes(MonumentCommand::apply)))
                        .then(Commands.literal("rollback").then(Commands.argument("id", StringArgumentType.word()).executes(MonumentCommand::rollback)))));
    }

    /** 왕성도시가 있는 차원 (기록이 없으면 왕국 대륙) */
    static ServerLevel levelFor(MinecraftServer s) {
        CityRecord rec = CityRecord.get(s);
        if (rec.present()) {
            ResourceLocation rl = ResourceLocation.tryParse(rec.dim);
            if (rl != null) {
                ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (l != null) return l;
            }
        }
        return s.getLevel(ModWorldgen.KINGDOM);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MonumentRecord rec = MonumentRecord.get(src.getServer());
        for (Monument m : ALL) {
            String line = String.format("§6%s§r — %s (설계 %d판) : %s", m.id(), m.title(), m.version(),
                    rec.has(m.id()) ? "적용됨 " + rec.summary(m.id()) : "적용 안 함");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static Monument arg(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Monument m = find(id);
        if (m == null) ctx.getSource().sendFailure(Component.literal("[웅장화] 없는 이름: " + id + " (/magiccity monument list)"));
        return m;
    }

    private static int survey(CommandContext<CommandSourceStack> ctx) {
        Monument m = arg(ctx);
        if (m == null) return 0;
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = levelFor(src.getServer());
        if (level == null) return 0;
        String bad = m.checkWorld(level);
        src.sendSuccess(() -> Component.literal("§6[웅장화] " + m.title() + " 검사 — " + (bad == null ? "월드 확인 통과" : "§c월드 확인 실패: " + bad)), false);
        MonumentRunner.Pick pick = MonumentRunner.survey(level, m);
        for (String line : pick.report()) src.sendSuccess(() -> Component.literal(line), false);
        int blocks = pick.ok().stream().mapToInt(MonumentPlan::size).sum();
        src.sendSuccess(() -> Component.literal(String.format("§6[웅장화] 지을 수 있는 요소 %d개, 약 %d칸. 월드는 바뀌지 않았습니다.", pick.ok().size(), blocks)), false);
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> ctx) {
        Monument m = arg(ctx);
        if (m == null) return 0;
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[웅장화] 건설 작업이 진행 중입니다. 끝난 뒤에 하세요."));
            return 0;
        }
        ServerLevel level = levelFor(src.getServer());
        if (level == null) return 0;
        String err = MonumentRunner.apply(level, m, line -> src.sendSuccess(() -> Component.literal(line), true));
        if (err != null) {
            src.sendFailure(Component.literal("[웅장화] " + err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§6[웅장화] " + m.title() + " 적용 시작 — 진행: /magiccity monument status"), true);
        return 1;
    }

    private static int rollback(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = levelFor(src.getServer());
        if (level == null) return 0;
        String err = MonumentRunner.rollback(level, id, line -> src.sendSuccess(() -> Component.literal(line), true));
        if (err != null) {
            src.sendFailure(Component.literal("[웅장화] " + err));
            return 0;
        }
        return 1;
    }
}
