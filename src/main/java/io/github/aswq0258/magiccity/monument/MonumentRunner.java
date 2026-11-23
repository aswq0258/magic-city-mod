package io.github.aswq0258.magiccity.monument;

import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 웅장화 묶음의 검사·적용·되돌리기.
 *  survey   : 월드를 바꾸지 않고, 요소마다 쓸 후보와 막힌 이유를 보고
 *  apply    : 검사를 통과한 요소만, 틱당 정해진 시간 안에서 놓음. 바꾸기 전 블록을 백업 파일에 적고 요소가 끝날 때마다 저장
 *  rollback : 백업대로 되돌림. 적용 뒤 누가 바꾼 칸(지금 블록 ≠ 놓은 블록)은 건드리지 않고 수만 셈
 * 백업 파일: <월드>/magiccity/monuments/<id>.nbt
 */
public final class MonumentRunner {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final long BUDGET = 25_000_000L;

    private MonumentRunner() {}

    private static Job active;

    public static boolean running() {
        return active != null;
    }

    public static String status() {
        return active == null ? null : active.status();
    }

    // ───────────────────────── 검사 ─────────────────────────

    public record Pick(List<MonumentPlan> ok, List<String> report) {}

    /** 요소마다 첫 통과 후보를 고름. 월드는 읽기만 함 */
    public static Pick survey(ServerLevel level, Monument m) {
        MonumentGuard guard = new MonumentGuard();
        List<MonumentPlan> ok = new ArrayList<>();
        List<String> report = new ArrayList<>();
        for (List<MonumentPlan> candidates : m.elements(level)) {
            String first = null, name = candidates.isEmpty() ? "?" : candidates.get(0).name;
            MonumentPlan chosen = null;
            for (MonumentPlan plan : candidates) {
                String why = check(level, guard, plan);
                if (why == null) {
                    chosen = plan;
                    break;
                }
                if (first == null) first = why;
            }
            if (chosen != null) {
                ok.add(chosen);
                report.add(String.format("§a○ %s (후보 %d, %d칸) %s", chosen.name, chosen.variant + 1, chosen.size(), chosen.where()));
            } else {
                report.add(String.format("§c× %s — 건너뜀: %s", name, first));
            }
        }
        return new Pick(ok, report);
    }

    private static String check(ServerLevel level, MonumentGuard guard, MonumentPlan plan) {
        if (plan.isEmpty()) return "설계가 비어 있음";
        if (plan.minY <= level.getMinBuildHeight() || plan.maxY >= level.getMaxBuildHeight() - 1) return "건축 높이 제한 밖";
        List<int[]> npcs = guard.npcBoxes(level, plan);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long k : plan.put.keySet()) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            String why = guard.why(x, y, z, npcs);
            if (why != null) return why + String.format(" (%d, %d, %d)", x, y, z);
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (!MonumentGuard.free(s)) return String.format("기존 블록과 겹침 %s (%d, %d, %d)", BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(), x, y, z);
        }
        for (long k : plan.support) {
            BlockState s = level.getBlockState(m.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)));
            if (!MonumentGuard.solid(s)) return String.format("받침이 없음 (%d, %d, %d)", BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
        }
        return null;
    }

    // ───────────────────────── 적용 ─────────────────────────

    /** 적용 시작. 문제가 있으면 이유, 시작했으면 null */
    public static String apply(ServerLevel level, Monument m, Consumer<String> say) {
        if (active != null) return "다른 웅장화 작업이 진행 중입니다";
        MonumentRecord rec = MonumentRecord.get(level.getServer());
        if (rec.has(m.id()) && !Files.exists(file(level.getServer(), m.id()))) rec.remove(m.id());   // 백업 전에 멈춘 기록 = 바뀐 칸 없음
        if (rec.has(m.id())) return "이미 적용 기록이 있습니다 (" + rec.summary(m.id()) + "). 먼저 rollback 하세요";
        String bad = m.checkWorld(level);
        if (bad != null) return "월드가 설계와 맞지 않습니다: " + bad;
        Pick pick = survey(level, m);
        if (pick.ok().isEmpty()) return "지을 수 있는 요소가 없습니다";
        Path file = file(level.getServer(), m.id());
        if (Files.exists(file)) return "이전 백업 파일이 남아 있습니다: " + file.getFileName() + " (rollback 하거나 직접 옮기세요)";
        rec.mark(m.id(), m.version(), "applying", 0, 0);
        active = new Job(level, m, pick.ok(), file, say, false);
        for (String line : pick.report()) say.accept(line);
        return null;
    }

    /** 되돌리기 시작 */
    public static String rollback(ServerLevel level, String id, Consumer<String> say) {
        if (active != null) return "다른 웅장화 작업이 진행 중입니다";
        Path file = file(level.getServer(), id);
        if (!Files.exists(file)) return "백업 파일이 없습니다: " + file;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            active = Job.restore(level, id, root, file, say);
            return null;
        } catch (IOException e) {
            return "백업을 읽지 못했습니다: " + e.getMessage();
        }
    }

    public static boolean cancel() {
        if (active == null) return false;
        active.finish("중단됨 — 지금까지 바꾼 칸은 백업돼 있어 rollback 으로 되돌릴 수 있습니다");
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post e) {
        if (active != null) active.tick();
    }

    public static void onServerStopping(ServerStoppingEvent e) {
        if (active != null) active.finish("서버 종료로 중단");
    }

    static Path file(MinecraftServer s, String id) {
        return s.getWorldPath(LevelResource.ROOT).resolve("magiccity").resolve("monuments").resolve(id + ".nbt");
    }

    // ───────────────────────── 작업 ─────────────────────────

    private static final class Job {
        final ServerLevel level;
        final String id;
        final Monument m;
        final Path file;
        final Consumer<String> say;
        final boolean restoring;
        /** 적용: 요소 목록과 진행 위치 */
        List<MonumentPlan> plans = List.of();
        int planIndex, entryIndex, placed, elementsDone;
        List<Map.Entry<Long, BlockState>> entries;
        /** 백업: 팔레트 + (칸, 이전, 놓은 것) */
        final List<BlockState> palette = new ArrayList<>();
        final Map<BlockState, Integer> paletteIndex = new HashMap<>();
        final List<long[]> backup = new ArrayList<>();   // {pos, oldIdx, newIdx}
        /** 되돌리기 진행 */
        int restoreIndex, restored, skipped;

        Job(ServerLevel level, Monument m, List<MonumentPlan> plans, Path file, Consumer<String> say, boolean restoring) {
            this.level = level;
            this.m = m;
            this.id = m == null ? "" : m.id();
            this.plans = plans;
            this.file = file;
            this.say = say;
            this.restoring = restoring;
        }

        private Job(ServerLevel level, String id, Path file, Consumer<String> say) {
            this.level = level;
            this.m = null;
            this.id = id;
            this.file = file;
            this.say = say;
            this.restoring = true;
        }

        static Job restore(ServerLevel level, String id, CompoundTag root, Path file, Consumer<String> say) {
            Job j = new Job(level, id, file, say);
            ListTag pal = root.getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < pal.size(); i++) j.palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i)));
            long[] pos = root.getLongArray("pos");
            int[] old = root.getIntArray("old"), now = root.getIntArray("new");
            for (int i = pos.length - 1; i >= 0; i--) j.backup.add(new long[]{pos[i], old[i], now[i]});   // 거꾸로 (위에서 아래로)
            say.accept(String.format("§e[웅장화] %s 되돌리기 시작: %d칸", id, pos.length));
            return j;
        }

        int idx(BlockState s) {
            return paletteIndex.computeIfAbsent(s, k -> {
                palette.add(k);
                return palette.size() - 1;
            });
        }

        String status() {
            if (restoring) return String.format("%s 되돌리는 중 %d/%d", id, restoreIndex, backup.size());
            return String.format("%s 적용 중: 요소 %d/%d, 놓은 칸 %d", id, planIndex, plans.size(), placed);
        }

        void tick() {
            long start = System.nanoTime();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            if (restoring) {
                while (restoreIndex < backup.size()) {
                    long[] e = backup.get(restoreIndex++);
                    p.set(BlockPos.getX(e[0]), BlockPos.getY(e[0]), BlockPos.getZ(e[0]));
                    BlockState now = level.getBlockState(p), mine = palette.get((int) e[2]);
                    if (now == mine) {
                        level.setBlock(p, palette.get((int) e[1]), FLAGS);
                        restored++;
                    } else skipped++;
                    if ((restoreIndex & 63) == 0 && System.nanoTime() - start > BUDGET) return;
                }
                MonumentRecord.get(level.getServer()).remove(id);
                try {
                    Files.move(file, file.resolveSibling(id + "-rolledback-" + System.currentTimeMillis() + ".nbt"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    MagicCityMod.LOGGER.warn("[웅장화] 백업 파일 이름 바꾸기 실패: {}", ex.getMessage());
                }
                active = null;
                say.accept(String.format("§a[웅장화] %s 되돌리기 끝: %d칸 복구, 적용 뒤 바뀐 칸 %d개는 그대로 둠", id, restored, skipped));
                return;
            }
            while (planIndex < plans.size()) {
                if (entries == null) {
                    entries = plans.get(planIndex).ordered();
                    entryIndex = 0;
                }
                while (entryIndex < entries.size()) {
                    Map.Entry<Long, BlockState> e = entries.get(entryIndex++);
                    long k = e.getKey();
                    p.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
                    BlockState old = level.getBlockState(p);
                    if (!MonumentGuard.free(old)) continue;                  // 검사 뒤에 누가 놓았으면 건너뜀
                    BlockState want = Block.updateFromNeighbourShapes(e.getValue(), level, p);
                    level.setBlock(p, want, FLAGS);
                    backup.add(new long[]{k, idx(old), idx(level.getBlockState(p))});
                    placed++;
                    if ((entryIndex & 63) == 0 && System.nanoTime() - start > BUDGET) return;
                }
                elementsDone++;
                planIndex++;
                entries = null;
                save("applying");
                if (System.nanoTime() - start > BUDGET) return;
            }
            save("applied");
            active = null;
            say.accept(String.format("§a[웅장화] %s 적용 끝: 요소 %d개, 놓은 칸 %d개. 되돌리기: /magiccity monument rollback %s", id, elementsDone, placed, id));
        }

        void save(String state) {
            CompoundTag root = new CompoundTag();
            root.putString("id", id);
            root.putInt("version", m.version());
            ListTag pal = new ListTag();
            for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
            root.put("palette", pal);
            long[] pos = new long[backup.size()];
            int[] old = new int[backup.size()], now = new int[backup.size()];
            for (int i = 0; i < backup.size(); i++) {
                pos[i] = backup.get(i)[0];
                old[i] = (int) backup.get(i)[1];
                now[i] = (int) backup.get(i)[2];
            }
            root.putLongArray("pos", pos);
            root.putIntArray("old", old);
            root.putIntArray("new", now);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(id + ".tmp");
                NbtIo.writeCompressed(root, tmp);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                MagicCityMod.LOGGER.error("[웅장화] 백업 저장 실패: {}", ex.getMessage());
            }
            MonumentRecord.get(level.getServer()).mark(id, m.version(), state, elementsDone, backup.size());
        }

        void finish(String why) {
            if (!restoring) save("applying");
            active = null;
            say.accept("§e[웅장화] " + id + " " + why);
        }
    }
}
