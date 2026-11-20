package io.github.aswq0258.magiccity.arena;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.arena.BossRoomRules.Spec;
import io.github.aswq0258.magiccity.boss.ManagedBoss;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 장별 전장 = 실제 보스 방 복사 (2026-11-20, claude/보스방복사전장_초안.md, 사용자 결정: 추천안 전부).
 * 그 장 전장에 처음 들어갈 때 왕국 대륙의 보스 방 범위를 떠서 월드 폴더(magiccity/boss_rooms/chN.nbt)에 저장하고,
 * 입장할 때마다 전장 차원에 사본을 새로 깐다. 보관 블록·스포너·제단 자석석은 빼고, 뚫린 경계는 막는다.
 */
public final class BossRooms {
    private BossRooms() {}

    private static final Gson GSON = new Gson();
    /** 보스 키 → 뜰 범위 (데이터 boss_rooms/<키>.json) */
    private static volatile Map<String, Spec> specs = Map.of();
    /** 장 → 불러온 사본 */
    private static final Map<Integer, Room> CACHE = new HashMap<>();

    /** 사본 하나: 블록 묶음 + 보스 자리·플레이어 자리 (상자 안 상대 좌표) */
    public record Room(int chapter, Spec spec, StructureTemplate template, BlockPos bossRel, BlockPos playerRel) {}

    /** 전장에 깐 사본 (전장 좌표) */
    public record Placed(Room room, BlockPos origin) {
        public Vec3 bossSpot() {
            BlockPos b = origin.offset(room.bossRel());
            return new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        }

        /** 보스 자리 기준으로 범위 밖인지 */
        public boolean outside(Vec3 p) {
            BlockPos b = origin.offset(room.bossRel());
            return BossRoomRules.outOfBox(p.x - (b.getX() + 0.5), p.y - b.getY(), p.z - (b.getZ() + 0.5), room.spec());
        }
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "boss_rooms") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Spec> out = new LinkedHashMap<>();
                int bad = 0;
                for (var e : map.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.startsWith("_")) continue;
                    try {
                        out.put(id, BossRoomRules.parse(e.getValue().getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스 전장] 보스 방 범위 {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                specs = Collections.unmodifiableMap(out);
                synchronized (CACHE) {
                    CACHE.clear();
                }
                MagicCityMod.LOGGER.info("[보스 전장] 보스 방 범위 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
    }

    public static Spec spec(ManagedBoss b) {
        return specs.get(b.key());
    }

    private static Path file(MinecraftServer s, int chapter) {
        return s.getWorldPath(LevelResource.ROOT).resolve("magiccity").resolve("boss_rooms").resolve("ch" + chapter + ".nbt");
    }

    public static boolean saved(MinecraftServer s, int chapter) {
        return Files.exists(file(s, chapter));
    }

    /** 이 장의 사본 (없으면 지금 지역에서 뜸). 쓸 수 없으면 null → 공용 전장 */
    public static Room get(MinecraftServer s, ManagedBoss b) {
        Spec spec = spec(b);
        if (spec == null || !spec.enabled()) return null;
        synchronized (CACHE) {
            Room r = CACHE.get(b.chapter());
            if (r != null && r.spec().equals(spec)) return r;
        }
        Room r = load(s, b, spec);
        if (r == null) r = capture(s, b);
        if (r != null) synchronized (CACHE) {
            CACHE.put(b.chapter(), r);
        }
        return r;
    }

    private static Room load(MinecraftServer s, ManagedBoss b, Spec spec) {
        Path f = file(s, b.chapter());
        if (!Files.exists(f)) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            StructureTemplate t = new StructureTemplate();
            t.load(BuiltInRegistries.BLOCK.asLookup(), root.getCompound("template"));
            Vec3i size = t.getSize();
            if (size.getX() != spec.sizeX() || size.getY() != spec.sizeY() || size.getZ() != spec.sizeZ()) {
                MagicCityMod.LOGGER.info("[보스 전장] {}장 범위가 바뀌어 보스 방을 다시 뜹니다", b.chapter());
                return null;
            }
            BlockPos boss = BlockPos.of(root.getLong("boss"));
            BlockPos player = root.contains("player") ? BlockPos.of(root.getLong("player")) : null;
            return new Room(b.chapter(), spec, t, boss, player);
        } catch (IOException | RuntimeException ex) {
            MagicCityMod.LOGGER.error("[보스 전장] {}장 보스 방 사본 읽기 실패: {}", b.chapter(), ex.getMessage());
            return null;
        }
    }

    /** 지금 왕국 대륙의 보스 방을 떠서 저장. 실패하면 null */
    public static Room capture(MinecraftServer s, ManagedBoss b) {
        Spec spec = spec(b);
        ServerLevel k = s.getLevel(ModWorldgen.KINGDOM);
        if (spec == null || k == null) return null;
        Vec3 c = b.altarCenter();
        BlockPos bc = BlockPos.containing(c);
        int[] o = BossRoomRules.captureOrigin(bc.getX(), bc.getY(), bc.getZ(), spec);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        if (origin.getY() < k.getMinBuildHeight() || origin.getY() + spec.sizeY() > k.getMaxBuildHeight()) return null;
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(k, origin, new Vec3i(spec.sizeX(), spec.sizeY(), spec.sizeZ()), false, null);
        BlockPos bossRel = bc.subtract(origin);
        BlockPos core = SealAltar.core(b.chapter());
        BlockPos playerRel = null;
        if (core != null) {
            BlockPos rel = core.subtract(origin);
            if (BossRoomRules.insideRel(rel.getX(), rel.getY(), rel.getZ(), spec)) playerRel = rel;
        }
        CompoundTag root = new CompoundTag();
        root.put("template", t.save(new CompoundTag()));
        root.putLong("boss", bossRel.asLong());
        if (playerRel != null) root.putLong("player", playerRel.asLong());
        Path f = file(s, b.chapter());
        try {
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(root, f);
        } catch (IOException ex) {
            MagicCityMod.LOGGER.error("[보스 전장] {}장 보스 방 사본 저장 실패: {}", b.chapter(), ex.getMessage());
        }
        MagicCityMod.LOGGER.info("[보스 전장] {}장 보스 방을 떴습니다 ({}×{}×{})", b.chapter(), spec.sizeX(), spec.sizeY(), spec.sizeZ());
        Room r = new Room(b.chapter(), spec, t, bossRel, playerRel);
        synchronized (CACHE) {
            CACHE.put(b.chapter(), r);
        }
        return r;
    }

    /** 사본 지우기 (다음 입장 때 다시 뜸) */
    public static boolean clear(MinecraftServer s, int chapter) {
        synchronized (CACHE) {
            CACHE.remove(chapter);
        }
        try {
            return Files.deleteIfExists(file(s, chapter));
        } catch (IOException ex) {
            return false;
        }
    }

    // ───────────────────────── 전장에 깔기 ─────────────────────────

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** 장 전장 자리를 비움 (빈 구역은 건너뜀): 중심 (cx, cz) 가로 ±r, 높이 y0~y1 */
    public static void clearArea(ServerLevel arena, int cx, int cz, int r, int y0, int y1) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int qx = (cx - r) >> 4; qx <= (cx + r) >> 4; qx++) for (int qz = (cz - r) >> 4; qz <= (cz + r) >> 4; qz++) {
            LevelChunk ch = arena.getChunk(qx, qz);
            for (BlockPos bp : new ArrayList<>(ch.getBlockEntities().keySet())) emptyContainer(arena, bp);
            LevelChunkSection[] secs = ch.getSections();
            for (int i = 0; i < secs.length; i++) {
                if (secs[i].hasOnlyAir()) continue;
                int sy = arena.getSectionYFromSectionIndex(i) << 4;
                if (sy + 15 < y0 || sy > y1) continue;
                for (int x = Math.max(qx << 4, cx - r); x <= Math.min((qx << 4) + 15, cx + r); x++)
                    for (int z = Math.max(qz << 4, cz - r); z <= Math.min((qz << 4) + 15, cz + r); z++)
                        for (int y = Math.max(sy, y0); y <= Math.min(sy + 15, y1); y++) {
                            m.set(x, y, z);
                            if (!ch.getBlockState(m).isAir()) arena.setBlock(m, air, FLAGS);
                        }
            }
        }
    }

    /** 보관 블록이면 안을 비우고 전리품 표를 떼어 냄 (지울 때 물건이 쏟아지거나 새로 생기지 않게) */
    private static boolean emptyContainer(ServerLevel level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof RandomizableContainerBlockEntity rc) rc.setLootTable(null);
        if (be instanceof Container c) {
            c.clearContent();
            return true;
        }
        return false;
    }

    /** 사본을 전장에 깔고 정리·경계 막기. 보스 자리가 (cx, floorY, cz) 에 옴 */
    public static Placed place(ServerLevel arena, Room room, int cx, int floorY, int cz) {
        Spec s = room.spec();
        BlockPos bossAt = new BlockPos(cx, floorY, cz);
        BlockPos origin = bossAt.subtract(room.bossRel());
        room.template().placeInWorld(arena, origin, origin, new StructurePlaceSettings(), RandomSource.create(0), Block.UPDATE_CLIENTS);
        strip(arena, origin, room);
        seal(arena, origin, s);
        return new Placed(room, origin);
    }

    /** 보관 블록·스포너·제단 자석석 빼기 */
    private static void strip(ServerLevel arena, BlockPos origin, Room room) {
        Spec s = room.spec();
        BlockState air = Blocks.AIR.defaultBlockState();
        int x0 = origin.getX(), z0 = origin.getZ(), x1 = x0 + s.sizeX() - 1, z1 = z0 + s.sizeZ() - 1;
        int y0 = origin.getY(), y1 = y0 + s.sizeY() - 1;
        for (int qx = x0 >> 4; qx <= x1 >> 4; qx++) for (int qz = z0 >> 4; qz <= z1 >> 4; qz++) {
            LevelChunk ch = arena.getChunk(qx, qz);
            List<BlockPos> list = new ArrayList<>(ch.getBlockEntities().keySet());
            for (BlockPos p : list) {
                if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1 || p.getY() < y0 || p.getY() > y1) continue;
                BlockEntity be = arena.getBlockEntity(p);
                boolean remove = emptyContainer(arena, p) || be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity;
                if (remove) arena.setBlock(p, air, FLAGS);
            }
        }
        if (room.playerRel() != null) {
            BlockPos core = origin.offset(room.playerRel());
            if (arena.getBlockState(core).is(Blocks.LODESTONE)) arena.setBlock(core, air, FLAGS);
        }
    }

    /** 뚫린 경계 막기: 옆면은 보이지 않는 벽, 바닥은 지하 방만 보이지 않는 벽, 윗면은 지하 방이면 심층암(햇빛 막기) 아니면 보이지 않는 벽 */
    private static void seal(ServerLevel arena, BlockPos origin, Spec s) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState(), roof = Blocks.DEEPSLATE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < s.sizeX(); x++) for (int y = 0; y < s.sizeY(); y++) for (int z = 0; z < s.sizeZ(); z++) {
            int f = BossRoomRules.face(x, y, z, s);
            if (f == 0 || (f == 2 && !s.underground())) continue;
            m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            BlockState st = arena.getBlockState(m);
            if (st.blocksMotion() && st.getFluidState().isEmpty()) continue;
            arena.setBlock(m, f == 3 && s.underground() ? roof : barrier, FLAGS);
        }
    }

    /** 플레이어가 설 자리: 제단 자리(있으면), 없으면 보스 자리 둘레에서 발 디딜 곳 */
    public static Vec3 playerSpot(ServerLevel arena, Placed pl) {
        Room room = pl.room();
        if (room.playerRel() != null) {
            BlockPos p = pl.origin().offset(room.playerRel());
            if (standable(arena, p)) return Vec3.atBottomCenterOf(p);
        }
        BlockPos b = pl.origin().offset(room.bossRel());
        int lim = Math.max(4, room.spec().r() - 3);
        for (int d = 8; d <= lim; d += 2) {
            for (int a = 0; a < 16; a++) {
                double ang = Math.PI * 2 * a / 16;
                int x = b.getX() + (int) Math.round(Math.cos(ang) * d), z = b.getZ() + (int) Math.round(Math.sin(ang) * d);
                for (int dy = -room.spec().down(); dy <= 8; dy++) {
                    BlockPos p = new BlockPos(x, b.getY() + dy, z);
                    if (standable(arena, p)) return Vec3.atBottomCenterOf(p);
                }
            }
        }
        return Vec3.atBottomCenterOf(b.above());
    }

    private static boolean standable(ServerLevel l, BlockPos p) {
        BlockState below = l.getBlockState(p.below());
        return below.blocksMotion() && !below.is(Blocks.BARRIER) && below.getFluidState().isEmpty()
                && l.getBlockState(p).getCollisionShape(l, p).isEmpty() && l.getBlockState(p).getFluidState().isEmpty()
                && l.getBlockState(p.above()).getCollisionShape(l, p.above()).isEmpty();
    }
}
