package io.github.aswq0258.magiccity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.aswq0258.magiccity.build.CityBuildJob;
import io.github.aswq0258.magiccity.build.CityBuildManager;
import io.github.aswq0258.magiccity.city.CityLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /magiccity build      — 현재 위치를 중심으로 약 395 x 395 성채 도시 + 다층 왕성 건설.
 *                          도시 범위의 지형 높이를 재서 가장 알맞은(중간값) 높이를 지면으로 자동 결정
 * /magiccity build here — 자동 대신 발밑 블록 높이를 지면으로 사용
 * /magiccity status          — 진행률 확인
 * /magiccity cancel          — 건설 중단
 */
public final class MagicCityCommand {
    private MagicCityCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("magiccity")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx, false))
                        .then(Commands.literal("here").executes(ctx -> build(ctx, true))))
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

    /** 도시 범위(성벽 안)를 12칸 간격으로 표본 조사해 지면 높이 중간값을 구함. 청크를 만들지 않고 지형 생성기로 계산 */
    private static int autoBase(ServerLevel level, int cx, int cz) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        List<Integer> hs = new ArrayList<>();
        int half = 260 - CityLayout.W0;
        for (int dx = -half; dx <= half; dx += 12) {
            for (int dz = -half; dz <= half; dz += 12) {
                hs.add(gen.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1);
            }
        }
        Collections.sort(hs);
        return hs.get(hs.size() / 2);
    }

    private static int build(CommandContext<CommandSourceStack> ctx, boolean here) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        BlockPos feet = BlockPos.containing(src.getPosition());
        int base = here ? feet.getY() - 1 : autoBase(level, feet.getX(), feet.getZ());

        if (base + 145 > level.getMaxBuildHeight()) {
            int maxY = level.getMaxBuildHeight() - 146;
            src.sendFailure(Component.literal("[마법 도시] 너무 높은 곳입니다. 왕성 첨탑이 높이 제한을 넘습니다. 지면 Y=" + maxY + " 이하에서 실행하세요 (지금 지면 Y=" + base + ")."));
            return 0;
        }

        long seed = level.getSeed() ^ (feet.asLong() * 0x9E3779B97F4A7C15L);
        CityLayout layout = new CityLayout(feet.getX(), feet.getZ(), base, seed);
        CityBuildJob job = new CityBuildJob(level, layout);
        CityBuildManager.start(job);

        src.sendSuccess(() -> Component.literal(String.format(
                "§b[마법 도시] 건설 시작! 중심 (%d, %d), 지면 Y=%d%s, 도시 약 395x395, 건물 %d개, 청크 %d개",
                feet.getX(), feet.getZ(), base, here ? "(발밑)" : "(자동)", layout.featureCount(), job.chunkCount())), true);
        src.sendSuccess(() -> Component.literal(
                "§7진행률은 채팅으로 알려드립니다. 15~30분 정도 걸릴 수 있습니다. 끝날 때까지 서버(게임)를 켜 두세요."), false);
        return 1;
    }
}
