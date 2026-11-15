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
 * /magiccity build      — 현재 위치를 중심으로 지름 약 520 의 방사형 이중 성곽 도시 + 동화성 왕성 건설.
 *                          도시 범위의 지형 높이를 재서 가장 알맞은(중간값) 높이를 지면으로 자동 결정
 * /magiccity build here — 자동 대신 발밑 블록 높이를 지면으로 사용
 * /magiccity kingdom    — 왕국 대륙 차원의 왕성도시 터(260, 260) 로 이동
 * /magiccity site oldcapital — 왕국 대륙에 몰락한 옛 왕도(네더 포탈이 열려 멸망한 수도) 건설
 * /magiccity goto oldcapital — 옛 왕도 앞 연합군 봉쇄 요새로 이동
 * /magiccity boss reset      — 심연의 문지기 재도전 (마계문 닫힘)
 * /magiccity site roads      — 길 10개의 쉼터·몬스터 둥지·필드보스 결투장 건설
 * /magiccity roads           — 길(지역) 목록과 필드보스 위치
 * /magiccity map [x z]       — 가 보지 않은 곳까지 그려진 지역 지도 (2048 x 2048)
 * /magiccity map world       — 대륙 전도
 * /magiccity site worldtree  — 세계수 성역 (세계수·정령호·엘프 나무집 마을) 건설
 * /magiccity goto worldtree  — 세계수 광장으로 이동
 * /magiccity site dwarf      — 드워프 광산왕국 (산의 문·대전당·옥좌실·대장간·광산) 건설
 * /magiccity goto dwarf      — 드워프 교역 마당으로 이동
 * /magiccity site hamlets    — 대륙 곳곳의 테마 촌락 건설 (바닐라 마을 대신)
 * /magiccity hamlets         — 가까운 촌락 목록
 * /magiccity remove <hamlets|hamlet|roads|worldtree|dwarf|oldcapital> — 왕국 대륙 건축물 철거 (원래 지형으로)
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
                .then(Commands.literal("kingdom").executes(MagicCityCommand::kingdom))
                .then(Commands.literal("site").then(Commands.literal("oldcapital").executes(MagicCityCommand::oldCapital))
                        .then(Commands.literal("roads").executes(MagicCityCommand::roads))
                        .then(Commands.literal("worldtree").executes(MagicCityCommand::worldTree))
                        .then(Commands.literal("dwarf").executes(MagicCityCommand::dwarf))
                        .then(Commands.literal("hamlets").executes(MagicCityCommand::hamlets)))
                .then(Commands.literal("remove")
                        .then(Commands.literal("hamlets").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.hamlets())))
                        .then(Commands.literal("hamlet").executes(ctx -> {
                            var src = ctx.getSource();
                            return remove(ctx, io.github.aswq0258.magiccity.site.Restore.nearestHamlet(src.getPosition().x, src.getPosition().z));
                        }))
                        .then(Commands.literal("roads").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.roads())))
                        .then(Commands.literal("worldtree").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.worldTree())))
                        .then(Commands.literal("dwarf").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.dwarf())))
                        .then(Commands.literal("oldcapital").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.oldCapital()))))
                .then(Commands.literal("hamlets").executes(ctx -> {
                    var src = ctx.getSource();
                    double px = io.github.aswq0258.magiccity.world.KingdomTerrain.CX, pz = io.github.aswq0258.magiccity.world.KingdomTerrain.CZ;
                    if (src.getEntity() != null && src.getLevel().dimension() == io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM) {
                        px = src.getPosition().x;
                        pz = src.getPosition().z;
                    }
                    String msg = "§b[마법 도시] 촌락 " + io.github.aswq0258.magiccity.site.Hamlets.spots().size() + "곳 중 가까운 곳:"
                            + io.github.aswq0258.magiccity.site.Hamlets.describe(px, pz, 8);
                    src.sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                }))
                .then(Commands.literal("goto").then(Commands.literal("dwarf").executes(ctx -> {
                    ServerLevel k = ctx.getSource().getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
                    if (k == null) return 0;
                    int x = io.github.aswq0258.magiccity.site.DwarfHold.CX, z = io.github.aswq0258.magiccity.site.DwarfHold.CZ - 40;
                    int y = io.github.aswq0258.magiccity.world.KingdomTerrain.column(io.github.aswq0258.magiccity.site.DwarfHold.CX,
                            io.github.aswq0258.magiccity.site.DwarfHold.CZ - 50).h() + 1;
                    ctx.getSource().getPlayerOrException().teleportTo(k, x + 0.5, y, z + 0.5, 180F, 0F);
                    return 1;
                })))
                .then(Commands.literal("goto").then(Commands.literal("worldtree").executes(ctx -> {
                    ServerLevel k = ctx.getSource().getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
                    if (k == null) return 0;
                    int x = io.github.aswq0258.magiccity.site.WorldTree.OX, z = io.github.aswq0258.magiccity.site.WorldTree.OZ + 46;
                    int y = io.github.aswq0258.magiccity.world.KingdomTerrain.column(x, z).h() + 2;
                    ctx.getSource().getPlayerOrException().teleportTo(k, x + 0.5, y, z + 0.5, 180F, 0F);
                    return 1;
                })))
                .then(Commands.literal("map")
                        .executes(ctx -> giveMap(ctx, null))
                        .then(Commands.literal("world").executes(ctx -> giveWorldMap(ctx)))
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(ctx -> giveMap(ctx, new int[]{com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")})))))
                .then(Commands.literal("roads").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("§b[마법 도시] 길(지역) 과 필드보스:");
                    for (int i = 0; i < io.github.aswq0258.magiccity.world.KingdomRoads.ROADS.length; i++) {
                        var r = io.github.aswq0258.magiccity.world.KingdomRoads.ROADS[i];
                        sb.append("\n§e").append(r.name()).append(" §7(").append(Math.round(r.length())).append("블록) §f")
                                .append(io.github.aswq0258.magiccity.site.RoadTheme.THEMES[i].region());
                    }
                    sb.append("\n§b필드보스 결투장:").append(io.github.aswq0258.magiccity.boss.FieldBosses.describe());
                    String msg = sb.toString();
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                }))
                .then(Commands.literal("goto").then(Commands.literal("oldcapital").executes(ctx -> {
                    ServerLevel k = ctx.getSource().getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
                    if (k == null) return 0;
                    ctx.getSource().getPlayerOrException().teleportTo(k, io.github.aswq0258.magiccity.world.KingdomTerrain.FORT_X + 0.5,
                            io.github.aswq0258.magiccity.world.KingdomTerrain.OLD_Y + 2, io.github.aswq0258.magiccity.world.KingdomTerrain.FORT_Z + 0.5, 0F, 0F);
                    return 1;
                })))
                .then(Commands.literal("boss").then(Commands.literal("reset").executes(ctx -> {
                    io.github.aswq0258.magiccity.boss.GateKeeper.reset(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("§e[마법 도시] 심연의 문지기를 다시 소환할 수 있게 했습니다 (마계문은 닫힘)."), true);
                    return 1;
                })))
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
        int half = CityLayout.SURVEY_R;
        for (int dx = -half; dx <= half; dx += 12) {
            for (int dz = -half; dz <= half; dz += 12) {
                if (dx * dx + dz * dz > half * half) continue;
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

        if (base + 156 > level.getMaxBuildHeight()) {
            int maxY = level.getMaxBuildHeight() - 157;
            src.sendFailure(Component.literal("[마법 도시] 너무 높은 곳입니다. 왕성 첨탑이 높이 제한을 넘습니다. 지면 Y=" + maxY + " 이하에서 실행하세요 (지금 지면 Y=" + base + ")."));
            return 0;
        }

        long seed = level.getSeed() ^ (feet.asLong() * 0x9E3779B97F4A7C15L);
        CityLayout layout = new CityLayout(feet.getX(), feet.getZ(), base, seed);
        CityBuildJob job = new CityBuildJob(level, layout);
        CityBuildManager.start(job);

        src.sendSuccess(() -> Component.literal(String.format(
                "§b[마법 도시] 건설 시작! 중심 (%d, %d), 지면 Y=%d%s, 원형 도시 지름 약 520, 건물 %d개, 청크 %d개",
                feet.getX(), feet.getZ(), base, here ? "(발밑)" : "(자동)", layout.featureCount(), job.chunkCount())), true);
        src.sendSuccess(() -> Component.literal(
                "§7진행률은 채팅으로 알려드립니다. 1~2시간 정도 걸릴 수 있습니다. 끝날 때까지 서버(게임)를 켜 두세요."), false);
        return 1;
    }

    /** 왕국 대륙 차원의 왕성도시 터(250, 250) 로 이동 */
    private static int kingdom(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel target = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (target == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다. 새 월드에서 다시 시도하세요."));
            return 0;
        }
        int x = io.github.aswq0258.magiccity.world.KingdomTerrain.CX, z = io.github.aswq0258.magiccity.world.KingdomTerrain.CZ;
        target.getChunk(x >> 4, z >> 4);
        int y = target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        src.getPlayerOrException().teleportTo(target, x + 0.5, y, z + 0.5, 0F, 0F);
        src.sendSuccess(() -> Component.literal("§b[마법 도시] 왕국 대륙의 왕성도시 터 (" + x + ", " + z + ") 에 도착했습니다. "
                + "여기서 /magiccity build 를 입력하면 왕성도시와 주변 왕국이 지어집니다."), true);
        return 1;
    }

    /** 왕국 대륙에 몰락한 옛 왕도 + 연합군 봉쇄 요새 건설 */
    private static int oldCapital(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        io.github.aswq0258.magiccity.site.OldCapital site = new io.github.aswq0258.magiccity.site.OldCapital(k.getSeed() ^ 0x0DCA717AL);
        CityBuildJob job = new CityBuildJob(k, site);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] 몰락한 옛 왕도 건설 시작! 중심 (%d, %d), 건물 %d개, 청크 %d개. "
                + "/magiccity goto oldcapital 로 봉쇄 요새에 갈 수 있습니다.", site.cx(), site.cz(), site.featureCount(), job.chunkCount())), true);
        return 1;
    }

    /** 왕국 대륙의 길 10개에 쉼터·몬스터 둥지·필드보스 결투장 건설 */
    private static int roads(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        io.github.aswq0258.magiccity.site.RoadSites layout = new io.github.aswq0258.magiccity.site.RoadSites();
        CityBuildJob job = new CityBuildJob(k, layout);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] 길 거점 건설 시작! 쉼터·몬스터 둥지·필드보스 결투장 %d곳, 청크 %d개. "
                + "/magiccity roads 로 위치를 볼 수 있습니다.", layout.featureCount(), job.chunkCount())), true);
        return 1;
    }

    /** 가 보지 않은 곳까지 그려진 지역 지도 (2048 x 2048). 왕국 대륙에 있으면 내 위치 중심, 아니면 왕성 중심 */
    private static int giveMap(CommandContext<CommandSourceStack> ctx, int[] at) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        net.minecraft.server.level.ServerPlayer player = src.getPlayerOrException();
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) return 0;
        int x, z;
        if (at != null) {
            x = at[0];
            z = at[1];
        } else if (player.level().dimension() == io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM) {
            x = player.getBlockX();
            z = player.getBlockZ();
        } else {
            x = io.github.aswq0258.magiccity.world.KingdomTerrain.CX;
            z = io.github.aswq0258.magiccity.world.KingdomTerrain.CZ;
        }
        net.minecraft.world.item.ItemStack map = io.github.aswq0258.magiccity.world.KingdomMap.region(k, x, z);
        if (!player.getInventory().add(map)) player.drop(map, false);
        src.sendSuccess(() -> Component.literal("§b[마법 도시] 왕국 대륙 지도를 받았습니다 (2048 x 2048). "
                + "§9파란 표시§b = 쉼터, §c빨간 표시§b = 몬스터 둥지, §c✕§b = 필드보스, §f흰 점§b = 마스터플랜 지역"), false);
        return 1;
    }

    /** 대륙 전체가 한 장에 그려진 전도 */
    private static int giveWorldMap(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        net.minecraft.server.level.ServerPlayer player = src.getPlayerOrException();
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) return 0;
        net.minecraft.world.item.ItemStack map = io.github.aswq0258.magiccity.world.KingdomMap.world(k);
        if (!player.getInventory().add(map)) player.drop(map, false);
        src.sendSuccess(() -> Component.literal("§6[마법 도시] 왕국 대륙 전도를 받았습니다. 흰 점 = 마스터플랜 지역, 빨간 점 = 필드보스, 검붉은 점 = 몰락한 옛 왕도 "
                + "(전도에는 내 위치가 표시되지 않습니다. 내 위치는 /magiccity map)"), false);
        return 1;
    }

    /** 세계수 성역 건설 */
    private static int worldTree(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        io.github.aswq0258.magiccity.site.WorldTree site = new io.github.aswq0258.magiccity.site.WorldTree(k.getSeed() ^ 0x5E1F7EEL);
        CityBuildJob job = new CityBuildJob(k, site);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§a[마법 도시] 세계수 성역 건설 시작! 중심 (%d, %d), 건물 %d개, 청크 %d개. "
                + "/magiccity goto worldtree 로 갈 수 있습니다.", site.cx(), site.cz(), site.featureCount(), job.chunkCount())), true);
        return 1;
    }

    /** 드워프 광산왕국 건설 */
    private static int dwarf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        io.github.aswq0258.magiccity.site.DwarfHold site = new io.github.aswq0258.magiccity.site.DwarfHold(k.getSeed() ^ 0xD3A2FL);
        CityBuildJob job = new CityBuildJob(k, site);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§6[마법 도시] 드워프 광산왕국 건설 시작! 산의 문 (%d, %d, %d), 청크 %d개. "
                + "/magiccity goto dwarf 로 갈 수 있습니다.", io.github.aswq0258.magiccity.site.DwarfHold.CX, site.floorY() + 1,
                io.github.aswq0258.magiccity.site.DwarfHold.CZ - 95, job.chunkCount())), true);
        return 1;
    }

    /** 대륙 곳곳의 테마 촌락 건설 */
    private static int hamlets(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        io.github.aswq0258.magiccity.site.Hamlets layout = new io.github.aswq0258.magiccity.site.Hamlets();
        CityBuildJob job = new CityBuildJob(k, layout);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] 촌락 %d곳 건설 시작! 청크 %d개. /magiccity hamlets 로 가까운 촌락 위치를 볼 수 있습니다.",
                layout.count(), job.chunkCount())), true);
        return 1;
    }

    /** 왕국 대륙의 건축물 철거 (원래 지형으로 되돌림) */
    private static int remove(CommandContext<CommandSourceStack> ctx, io.github.aswq0258.magiccity.site.Restore restore) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설(철거) 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 차원을 찾을 수 없습니다."));
            return 0;
        }
        if (restore.areaCount() == 0) {
            src.sendFailure(Component.literal("[마법 도시] 철거할 대상이 없습니다."));
            return 0;
        }
        CityBuildJob job = new CityBuildJob(k, restore);
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§e[마법 도시] %s 철거 시작! %d곳, 청크 %d개를 원래 지형으로 되돌립니다. (나무·풀은 되살아나지 않습니다)",
                restore.label(), restore.areaCount(), job.chunkCount())), true);
        return 1;
    }
}
