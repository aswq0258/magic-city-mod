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
 * /magiccity build expansion — 이미 지은 원형 도시 바깥에 신시가지·외곽 생활권만 덧지음
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
 * /magiccity site hamlet     — 가장 가까운 촌락 한 곳만 건설 (remove hamlet 뒤 다시 지을 때)
 * /magiccity hamlets         — 가까운 촌락 목록
 * /magiccity remove <hamlets|hamlet|roads|worldtree|dwarf|oldcapital> — 왕국 대륙 건축물 철거 (원래 지형으로)
 * /magiccity fix roads      — 이미 생성된 대륙에서 길·강·길가 나무만 그 자리에서 고침 (+ 신시가지 다시 짓기)
 * /magiccity fix lava [반지름]   — 내 주변(기본 512) 지표의 용암 호수·용암 샘을 물로 바꿈
 * /magiccity fix forests [반지름] — 이미 생성된 땅에 새 숲 배치(산기슭·산비탈 침엽수·들판 작은 숲)를 입힘 (기본 1024)
 * /magiccity fix nature [반지름] — 내 주변(기본 256) 빈 땅에 바이옴에 맞는 나무·풀을 다시 심음
 * /magiccity remove city     — 왕성도시(구시가지 + 신시가지) 철거
 * /magiccity remove all      — 왕국 대륙의 모든 건축물 + 왕성도시 철거 (차례로)
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
                        .then(Commands.literal("here").executes(ctx -> build(ctx, true)))
                        .then(Commands.literal("expansion").executes(MagicCityCommand::buildExpansion)))
                .then(Commands.literal("kingdom").executes(MagicCityCommand::kingdom))
                .then(Commands.literal("site").then(Commands.literal("oldcapital").executes(MagicCityCommand::oldCapital))
                        .then(Commands.literal("roads").executes(MagicCityCommand::roads))
                        .then(Commands.literal("worldtree").executes(MagicCityCommand::worldTree))
                        .then(Commands.literal("dwarf").executes(MagicCityCommand::dwarf))
                        .then(Commands.literal("hamlets").executes(MagicCityCommand::hamlets))
                        .then(Commands.literal("hamlet").executes(MagicCityCommand::oneHamlet)))
                .then(Commands.literal("fix").then(Commands.literal("roads").executes(MagicCityCommand::fixRoads))
                        .then(Commands.literal("lava").executes(ctx -> fixLava(ctx, 512))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 4096))
                                        .executes(ctx -> fixLava(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("forests").executes(ctx -> fixForests(ctx, 1024))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 4096))
                                        .executes(ctx -> fixForests(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("nature").executes(ctx -> fixNature(ctx, 256))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 2048))
                                        .executes(ctx -> fixNature(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("hamlets").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.hamlets())))
                        .then(Commands.literal("hamlet").executes(ctx -> {
                            var src = ctx.getSource();
                            return remove(ctx, io.github.aswq0258.magiccity.site.Restore.nearestHamlet(src.getPosition().x, src.getPosition().z));
                        }))
                        .then(Commands.literal("roads").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.roads())))
                        .then(Commands.literal("worldtree").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.worldTree())))
                        .then(Commands.literal("dwarf").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.dwarf())))
                        .then(Commands.literal("oldcapital").executes(ctx -> remove(ctx, io.github.aswq0258.magiccity.site.Restore.oldCapital())))
                        .then(Commands.literal("city").executes(ctx -> removeCity(ctx, false)))
                        .then(Commands.literal("all").executes(ctx -> removeCity(ctx, true))))
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
                    for (int i = 0; i < io.github.aswq0258.magiccity.world.KingdomRoads.REGIONS; i++) {
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
        io.github.aswq0258.magiccity.build.CityRecord rec = io.github.aswq0258.magiccity.build.CityRecord.get(src.getServer());
        rec.cx = feet.getX();
        rec.cz = feet.getZ();
        rec.base = base;
        rec.seed = seed;
        rec.dim = level.dimension().location().toString();
        rec.setDirty();

        src.sendSuccess(() -> Component.literal(String.format(
                "§b[마법 도시] 건설 시작! 중심 (%d, %d), 지면 Y=%d%s, 원형 구시가지 + 신시가지(약 1240 x 1240), 건물 %d개, 청크 %d개",
                feet.getX(), feet.getZ(), base, here ? "(발밑)" : "(자동)", layout.featureCount(), job.chunkCount())), true);
        src.sendSuccess(() -> Component.literal(
                "§7진행률은 채팅으로 알려드립니다. 1~2시간 정도 걸릴 수 있습니다. 끝날 때까지 서버(게임)를 켜 두세요."), false);
        return 1;
    }

    /**
     * 이미 지어진 원형 도시 바깥에 신시가지·외곽 생활권만 덧지음.
     * 이 버전으로 지은 도시는 기록을 쓰고, 기록이 없으면 왕국 대륙(260, 260) 또는 지금 서 있는 곳을 도시 중심으로 봄.
     */
    private static int buildExpansion(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        io.github.aswq0258.magiccity.build.CityRecord rec = io.github.aswq0258.magiccity.build.CityRecord.get(src.getServer());
        ServerLevel level;
        int cx, cz, base;
        long seed;
        String how;
        if (rec.present()) {
            level = src.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(rec.dim)));
            if (level == null) {
                src.sendFailure(Component.literal("[마법 도시] 도시가 있는 차원을 찾을 수 없습니다: " + rec.dim));
                return 0;
            }
            cx = rec.cx;
            cz = rec.cz;
            base = rec.base;
            seed = rec.seed;
            how = "기록된 도시";
        } else {
            level = src.getLevel();
            boolean kingdom = level.dimension() == io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM;
            BlockPos feet = BlockPos.containing(src.getPosition());
            cx = kingdom ? io.github.aswq0258.magiccity.world.KingdomTerrain.CX : feet.getX();
            cz = kingdom ? io.github.aswq0258.magiccity.world.KingdomTerrain.CZ : feet.getZ();
            base = autoBase(level, cx, cz);
            seed = level.getSeed() ^ (BlockPos.asLong(cx, base + 1, cz) * 0x9E3779B97F4A7C15L);
            how = kingdom ? "왕국 대륙 왕성 터" : "지금 위치를 도시 중심으로";
        }
        CityLayout layout = new CityLayout(cx, cz, base, seed);
        CityBuildJob job = new CityBuildJob(level, new io.github.aswq0258.magiccity.city.CityExpansion(layout));
        CityBuildManager.start(job);
        final String h = how;
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] 신시가지 건설 시작! (%s) 중심 (%d, %d), 지면 Y=%d, 청크 %d개. 원형 성곽 안은 그대로 둡니다.",
                h, cx, cz, base, job.chunkCount())), true);
        return 1;
    }

    /** 왕국 대륙 차원의 왕성도시 터(260, 260) 로 이동 */
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
    private static int oneHamlet(CommandContext<CommandSourceStack> ctx) {
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
        var spot = io.github.aswq0258.magiccity.site.Hamlets.nearest(src.getPosition().x, src.getPosition().z);
        if (spot == null) return 0;
        CityBuildJob job = new CityBuildJob(k, new io.github.aswq0258.magiccity.site.Hamlets(List.of(spot)));
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] %s 건설 시작! (%d, %d) 청크 %d개.",
                spot.name(), spot.x(), spot.z(), job.chunkCount())), true);
        return 1;
    }

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
        src.sendSuccess(() -> Component.literal(String.format("§e[마법 도시] %s 철거 시작! %d곳, 청크 %d개를 원래 지형으로 되돌립니다. (빈 땅에는 나무·풀을 다시 심습니다)",
                restore.label(), restore.areaCount(), job.chunkCount())), true);
        return 1;
    }

    /**
     * 왕성도시 철거 (all = 왕국 대륙에 지은 것 전부 + 왕성도시). 도시 기록이 있으면 그 위치·차원,
     * 없으면 왕국 대륙의 왕성 터 (260, 260).
     */
    private static int removeCity(CommandContext<CommandSourceStack> ctx, boolean all) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설(철거) 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel kingdom = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        io.github.aswq0258.magiccity.build.CityRecord rec = io.github.aswq0258.magiccity.build.CityRecord.get(src.getServer());
        ServerLevel level;
        int cx, cz, base;
        if (rec.present()) {
            level = src.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(rec.dim)));
            cx = rec.cx;
            cz = rec.cz;
            base = rec.base;
        } else {
            level = kingdom;
            cx = io.github.aswq0258.magiccity.world.KingdomTerrain.CX;
            cz = io.github.aswq0258.magiccity.world.KingdomTerrain.CZ;
            base = level == null ? 63 : autoBase(level, cx, cz);
        }
        if (level == null) {
            src.sendFailure(Component.literal("[마법 도시] 도시가 있는 차원을 찾을 수 없습니다."));
            return 0;
        }
        int jobs = 0, chunks = 0;
        if (all && kingdom != null) {
            for (io.github.aswq0258.magiccity.site.Restore r : io.github.aswq0258.magiccity.site.Restore.allSites()) {
                if (r.areaCount() == 0) continue;
                CityBuildJob j = new CityBuildJob(kingdom, r);
                CityBuildManager.enqueue(j);
                jobs++;
                chunks += j.chunkCount();
            }
        }
        boolean isKingdom = level.dimension() == io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM;
        CityBuildJob cityJob = new CityBuildJob(level, io.github.aswq0258.magiccity.site.Restore.city(cx, cz, base, isKingdom));
        CityBuildManager.enqueue(cityJob);
        jobs++;
        chunks += cityJob.chunkCount();
        final int fj = jobs, fc = chunks;
        src.sendSuccess(() -> Component.literal(String.format("§e[마법 도시] %s 철거 시작! 작업 %d개, 청크 %d개 (차례로 진행). 왕성도시 중심 (%d, %d)%s",
                all ? "왕국 대륙의 모든 건축물" : "왕성도시", fj, fc, cx, cz,
                isKingdom ? "" : " — 왕국 대륙이 아닌 곳은 기본 지형 + 흙·풀로 되돌립니다")), true);
        return 1;
    }

    /**
     * 이미 생성된 왕국 대륙에서 길만 그 자리에서 고침 (대륙을 다시 생성하지 않음):
     * 도시 근처 길·강 띠를 지형째 다시 깔고, 먼 길은 주변 나무만 걷어냄. 이어서 신시가지(성곽 바깥) 를 다시 지음.
     */
    private static int fixLava(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null || src.getLevel() != k) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 안에서 입력하세요."));
            return 0;
        }
        int x = (int) Math.floor(src.getPosition().x), z = (int) Math.floor(src.getPosition().z);
        CityBuildJob job = new CityBuildJob(k, new io.github.aswq0258.magiccity.site.LavaFix(x, z, radius));
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§a[마법 도시] 용암 호수 정리 시작! (%d, %d) 주변 %d블록, 청크 %d개. 지표(Y 50 위)의 용암을 물로 바꿉니다.",
                x, z, radius, job.chunkCount())), true);
        return 1;
    }

    private static int fixForests(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null || src.getLevel() != k) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 안에서 입력하세요."));
            return 0;
        }
        int x = (int) Math.floor(src.getPosition().x), z = (int) Math.floor(src.getPosition().z);
        CityBuildJob job = new CityBuildJob(k, new io.github.aswq0258.magiccity.site.ForestFix(x, z, radius));
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§a[마법 도시] 숲 입히기 시작! (%d, %d) 주변 %d블록, 청크 %d개. 바이옴을 다시 칠하고 빈 땅에 나무를 심습니다.",
                x, z, radius, job.chunkCount())), true);
        return 1;
    }

    private static int fixNature(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        if (CityBuildManager.isRunning()) {
            src.sendFailure(Component.literal("[마법 도시] 이미 건설 중입니다. /magiccity status 로 확인하세요."));
            return 0;
        }
        ServerLevel k = src.getServer().getLevel(io.github.aswq0258.magiccity.world.ModWorldgen.KINGDOM);
        if (k == null || src.getLevel() != k) {
            src.sendFailure(Component.literal("[마법 도시] 왕국 대륙 안에서 입력하세요."));
            return 0;
        }
        int x = (int) Math.floor(src.getPosition().x), z = (int) Math.floor(src.getPosition().z);
        CityBuildJob job = new CityBuildJob(k, new io.github.aswq0258.magiccity.site.NatureFix(x, z, radius));
        CityBuildManager.start(job);
        src.sendSuccess(() -> Component.literal(String.format("§a[마법 도시] 나무 다시 심기 시작! (%d, %d) 주변 %d블록, 청크 %d개. 우거진 숲·길가·건축물 자리는 건드리지 않습니다.",
                x, z, radius, job.chunkCount())), true);
        return 1;
    }

    private static int fixRoads(CommandContext<CommandSourceStack> ctx) {
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
        io.github.aswq0258.magiccity.site.RoadRepair repair = new io.github.aswq0258.magiccity.site.RoadRepair();
        CityBuildJob j1 = new CityBuildJob(k, repair);
        CityBuildManager.enqueue(j1);
        // 왕국 대륙의 왕성도시: 신시가지(성곽 바깥) 다시 짓기
        io.github.aswq0258.magiccity.build.CityRecord rec = io.github.aswq0258.magiccity.build.CityRecord.get(src.getServer());
        int cx = io.github.aswq0258.magiccity.world.KingdomTerrain.CX, cz = io.github.aswq0258.magiccity.world.KingdomTerrain.CZ, base;
        long seed;
        boolean cityHere = !rec.present() || rec.dim.equals(k.dimension().location().toString());
        String extra = "";
        if (cityHere) {
            if (rec.present()) {
                cx = rec.cx;
                cz = rec.cz;
                base = rec.base;
                seed = rec.seed;
            } else {
                base = autoBase(k, cx, cz);
                seed = k.getSeed() ^ (BlockPos.asLong(cx, base + 1, cz) * 0x9E3779B97F4A7C15L);
            }
            CityBuildJob j2 = new CityBuildJob(k, new io.github.aswq0258.magiccity.city.CityExpansion(new CityLayout(cx, cz, base, seed)));
            CityBuildManager.enqueue(j2);
            extra = String.format(" 이어서 왕성도시 신시가지(성곽 바깥, 청크 %d개) 를 다시 짓습니다.", j2.chunkCount());
        }
        final String e = extra;
        src.sendSuccess(() -> Component.literal(String.format("§b[마법 도시] 길 고치기 시작! 길 주변 청크 %d개 (도시 근처는 길·강을 다시 깔고, 먼 길은 주변 나무만 정리).%s",
                j1.chunkCount(), e)), true);
        return 1;
    }
}
