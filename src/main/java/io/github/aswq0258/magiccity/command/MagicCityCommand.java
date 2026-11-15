package io.github.aswq0258.magiccity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.aswq0258.magiccity.build.CityBuildJob;
import io.github.aswq0258.magiccity.build.CityBuildManager;
import io.github.aswq0258.magiccity.city.CityLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * /magiccity build [radius]  — 현재 위치를 중심으로 도시 건설 시작 (기본 600, 450~700. 발밑 블록 높이가 가장 바깥 단의 지면)
 * /magiccity status          — 진행률 확인
 * /magiccity cancel          — 건설 중단
 */
public final class MagicCityCommand {
    private MagicCityCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("magiccity")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx, 600))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(450, 700))
                                .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(CityBuildManager.status()), false);
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    boolean was = CityBuildManager.cancel();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            was ? "§e[마법 도시] 건설을 중단했습니다." : "§7[마법 도시] 진행 중인 건설이 없습니다."), true);
                    return 1;
                })));
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        BlockPos feet = BlockPos.containing(src.getPosition());
        int base = feet.getY() - 1; // 발밑 블록 = 도시 지면 높이

        if (base + 180 > level.getMaxBuildHeight()) {
            int maxY = level.getMaxBuildHeight() - 181;
            src.sendFailure(Component.literal("[마법 도시] 너무 높은 곳입니다. 중앙 대성이 높이 제한을 넘습니다. Y=" + maxY + " 이하에서 실행하세요."));
            return 0;
        }

        long seed = level.getSeed() ^ (feet.asLong() * 0x9E3779B97F4A7C15L);
        CityLayout layout = new CityLayout(feet.getX(), feet.getZ(), base, radius, seed);
        CityBuildJob job = new CityBuildJob(level, layout);
        CityBuildManager.start(job);

        src.sendSuccess(() -> Component.literal(String.format(
                "§b[마법 도시] 건설 시작! 중심 (%d, %d), 지면 Y=%d, 반지름 %d, 건물 %d개, 청크 %d개",
                feet.getX(), feet.getZ(), base, radius, layout.featureCount(), job.chunkCount())), true);
        src.sendSuccess(() -> Component.literal(
                "§7진행률은 채팅으로 알려드립니다. 렉을 줄이려면 건설이 끝날 때까지 도시 근처에 머물러 주세요."), false);
        return 1;
    }
}
