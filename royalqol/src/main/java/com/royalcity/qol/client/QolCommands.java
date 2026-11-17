package com.royalcity.qol.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.royalcity.qol.nav.NavMath;
import com.royalcity.qol.nav.Waypoint;
import com.royalcity.qol.nav.WaypointBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/** 클라이언트 명령 /rq (서버 권한 필요 없음, 이 컴퓨터에만 저장) */
public final class QolCommands {
    private QolCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rq")
                .executes(QolCommands::help)
                .then(Commands.literal("list").executes(QolCommands::list))
                .then(Commands.literal("near").executes(QolCommands::near))
                .then(Commands.literal("go").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            for (Waypoint w : QolState.BOOK.all()) b.suggest(w.name());
                            return b.buildFuture();
                        })
                        .executes(QolCommands::go)))
                .then(Commands.literal("stop").executes(QolCommands::stop))
                .then(Commands.literal("info").executes(QolCommands::info))
                .then(Commands.literal("add")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> add(c, IntegerArgumentType.getInteger(c, "radius")))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> add(c, 16))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            for (Waypoint w : QolState.BOOK.user()) b.suggest(w.name());
                            return b.buildFuture();
                        })
                        .executes(QolCommands::remove)))
                .then(Commands.literal("hud").executes(QolCommands::hud))
                .then(Commands.literal("reload").executes(QolCommands::reload)));
    }

    private static void say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static String dim() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    private static LocalPlayer me() {
        return Minecraft.getInstance().player;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        say(c, "/rq list · near · go <이름> · stop · info · add [반경] <이름> · remove <이름> · hud · reload\n"
                + "단축키: H = HUD 켜기/끄기, Y = 가까운 지역 5곳을 목적지로 차례대로");
        return 1;
    }

    private static String line(Waypoint w, LocalPlayer p) {
        String d = p == null ? "" : " " + NavMath.distance(w.distanceTo(p.getX(), p.getZ())) + " " + NavMath.compassTo(p.getX(), p.getZ(), w.x(), w.z());
        return (w.builtin() ? "· " : "★ ") + w.name() + " (" + w.x() + ", " + w.z() + ")" + d;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        LocalPlayer p = me();
        List<Waypoint> here = QolState.BOOK.inDimension(dim());
        long elsewhere = QolState.BOOK.all().size() - here.size();
        StringBuilder sb = new StringBuilder("이 차원의 웨이포인트 " + here.size() + "개 (· 내장, ★ 내가 추가)");
        for (Waypoint w : here) sb.append('\n').append(line(w, p));
        if (elsewhere > 0) sb.append("\n다른 차원에 ").append(elsewhere).append("개 더 있음");
        say(c, sb.toString());
        return here.size();
    }

    private static int near(CommandContext<CommandSourceStack> c) {
        LocalPlayer p = me();
        if (p == null) return 0;
        List<Waypoint> n = QolState.BOOK.nearest(dim(), p.getX(), p.getZ(), 5);
        if (n.isEmpty()) return fail(c, "이 차원에는 웨이포인트가 없습니다 (내장 지역은 magiccity:kingdom 차원)");
        say(c, "가까운 곳:\n" + n.stream().map(w -> line(w, p)).collect(Collectors.joining("\n")));
        return n.size();
    }

    private static int go(CommandContext<CommandSourceStack> c) {
        String q = StringArgumentType.getString(c, "name");
        WaypointBook.Match m = QolState.BOOK.find(q);
        if (m.chosen().isEmpty()) {
            if (m.candidates().isEmpty()) return fail(c, "'" + q + "' 웨이포인트가 없습니다. /rq list 로 확인하세요");
            return fail(c, "여러 곳이 맞습니다: " + m.candidates().stream().map(Waypoint::name).collect(Collectors.joining(", ")));
        }
        QolState.destination = m.chosen().get();
        LocalPlayer p = me();
        say(c, "목적지: " + line(QolState.destination, p).substring(2));
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        if (QolState.destination == null) return fail(c, "목적지가 없습니다");
        QolState.destination = null;
        say(c, "목적지 안내를 끝냈습니다");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        LocalPlayer p = me();
        if (p == null) return 0;
        String region = QolState.BOOK.regionAt(dim(), p.getX(), p.getZ()).map(Waypoint::name).orElse("-");
        String dest = QolState.destination().map(w -> line(w, p).substring(2)).orElse("-");
        say(c, "위치 " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + " (" + dim() + "), " + NavMath.facing(p.getYRot()) + "쪽을 봄\n"
                + "지역: " + region + "\n목적지: " + dest + "\nHUD: " + (QolState.hud ? "켜짐" : "꺼짐")
                + "\n웨이포인트: 내장 " + (QolState.BOOK.all().size() - QolState.BOOK.user().size()) + "개, 내가 추가 " + QolState.BOOK.user().size() + "개"
                + "\n파일: " + QolState.file());
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> c, int radius) {
        LocalPlayer p = me();
        if (p == null) return 0;
        String name = StringArgumentType.getString(c, "name").strip();
        Waypoint w;
        try {
            w = new Waypoint(name, dim(), p.getBlockX(), p.getBlockY(), p.getBlockZ(), radius, false, "");
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        if (QolState.BOOK.add(w) == WaypointBook.AddResult.DUPLICATE) return fail(c, "같은 이름이 이미 있습니다: " + name);
        QolState.save();
        say(c, "추가했습니다: " + line(w, p));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        return switch (QolState.BOOK.remove(name)) {
            case REMOVED -> {
                if (QolState.destination != null && QolState.destination.name().equals(name.strip())) QolState.destination = null;
                QolState.save();
                say(c, "지웠습니다: " + name);
                yield 1;
            }
            case BUILTIN -> fail(c, "내장 지역은 지울 수 없습니다: " + name);
            case NOT_FOUND -> fail(c, "없는 이름입니다 (정확한 이름을 쓰세요): " + name);
        };
    }

    private static int hud(CommandContext<CommandSourceStack> c) {
        QolState.hud = !QolState.hud;
        QolState.save();
        say(c, "HUD " + (QolState.hud ? "켜짐" : "꺼짐"));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        say(c, QolState.load());
        return 1;
    }
}
