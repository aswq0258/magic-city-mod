package io.github.aswq0258.magiccity.arena;

import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.arena.ArenaRules.Vote;
import io.github.aswq0258.magiccity.arena.ArenaRules.VoteResult;
import io.github.aswq0258.magiccity.boss.ManagedBoss;
import io.github.aswq0258.magiccity.boss.Story;
import io.github.aswq0258.magiccity.item.LootPouchItem;
import io.github.aswq0258.magiccity.item.Pouches;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 챕터 보스 인스턴스 전장 (2026-11-19, 프로젝트 문서 claude/인스턴스전장_구현초안.md v3).
 * <ol>
 *   <li>봉인 제단 클릭 → 파티 투표(전원 동의, 30초, 거절·시간 초과면 10초 뒤 다시)</li>
 *   <li>5초 카운트다운 → 전장을 새로 깔고 접속 중인 파티원 전원 이동 → 3초 뒤 보스</li>
 *   <li>죽으면 아이템을 잃지 않고 관전 모드(부활은 아이템으로 죽음을 막는 것만), 전멸·30분이면 패배</li>
 *   <li>보스 처치 → 참가자 모두 장 완료 + 전리품 꾸러미 → 30초 뒤 제단으로</li>
 * </ol>
 * 장마다 한 팀만 (동시 입장 없음). 블록 부수기·놓기·폭발 파괴·양동이·엔더 진주·후렴과·보트·광산수레 금지, 경계 밖이면 되돌림.
 */
public final class ArenaManager {
    private ArenaManager() {}

    public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MagicCityMod.MODID, "boss_arena"));
    /** 플레이어 저장값: 전장에서 돌아갈 자리·원래 게임 모드 (서버가 꺼져도 다시 접속하면 돌려보냄) */
    private static final String RETURN_KEY = "magiccity_arena_return";

    private enum Phase { COUNTDOWN, SPAWN_WAIT, FIGHT, VICTORY }

    private static final class Run {
        final int chapter;
        final ManagedBoss boss;
        final List<UUID> members;
        final Set<UUID> dead = new LinkedHashSet<>();
        final BlockPos floor;
        final BlockPos altar;
        Phase phase = Phase.COUNTDOWN;
        long phaseStart;
        UUID bossId;
        final List<ChunkPos> forced = new ArrayList<>();
        /** 실제 보스 방 사본을 깔았으면 그 자리 (없으면 공용 전장) */
        BossRooms.Placed room;
        Vec3 roomPlayerSpot;

        Run(int chapter, ManagedBoss boss, List<UUID> members, BlockPos altar, long now) {
            this.chapter = chapter;
            this.boss = boss;
            this.members = new ArrayList<>(members);
            int[] c = ArenaRules.center(chapter);
            this.floor = new BlockPos(c[0], c[1], c[2]);
            this.altar = altar;
            this.phaseStart = now;
        }

        Vec3 bossSpot() {
            if (room != null) return room.bossSpot();
            return new Vec3(floor.getX() + 0.5, floor.getY() + 1 + boss.arenaYOffset(), floor.getZ() + 0.5);
        }

        Vec3 playerSpot() {
            if (roomPlayerSpot != null) return roomPlayerSpot;
            return new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 16.5);
        }

        int radius() {
            return ArenaRules.radius(boss.arenaShape());
        }

        /** 전장 밖인지 (보스 방 사본이면 범위 상자, 아니면 공용 원형) */
        boolean outside(Vec3 p) {
            if (room != null) return room.outside(p);
            return ArenaRules.outOfBounds(p.x - (floor.getX() + 0.5), p.y - floor.getY(), p.z - (floor.getZ() + 0.5), radius());
        }
    }

    private static final List<Vote> VOTES = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<Integer, Run> RUNS = new HashMap<>();

    private static long now(MinecraftServer s) {
        return s.overworld().getGameTime();
    }

    private static Run runOf(UUID u) {
        for (Run r : RUNS.values()) if (r.members.contains(u)) return r;
        return null;
    }

    private static Vote voteOf(UUID u) {
        for (Vote v : VOTES) if (v.members.contains(u)) return v;
        return null;
    }

    private static ServerPlayer online(MinecraftServer s, UUID u) {
        return s.getPlayerList().getPlayer(u);
    }

    private static void tell(MinecraftServer s, List<UUID> who, Component msg) {
        for (UUID u : who) {
            ServerPlayer p = online(s, u);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private static Component text(String s) {
        return Component.literal(s);
    }

    // ───────────────────────── 제안·투표 ─────────────────────────

    /** 제단 클릭 (파티장이든 파티원이든) */
    public static void propose(ServerPlayer p, int chapter) {
        MinecraftServer s = p.server;
        long now = now(s);
        ManagedBoss b = ManagedBoss.forChapter(chapter);
        if (b == null) return;
        if (s.getLevel(ARENA) == null) {
            p.sendSystemMessage(text("§c[보스전] 전장 차원을 찾을 수 없습니다 (magiccity 데이터 확인)."));
            return;
        }
        if (RUNS.containsKey(chapter)) {
            p.sendSystemMessage(text("§c[보스전] 지금 다른 일행이 " + b.name() + "(와)과 싸우는 중이다. 끝난 뒤에 다시 오라."));
            return;
        }
        if (runOf(p.getUUID()) != null || voteOf(p.getUUID()) != null) {
            p.sendSystemMessage(text("§c[보스전] 이미 다른 보스전 투표나 전투에 참여 중입니다."));
            return;
        }
        Long cd = COOLDOWN.get(p.getUUID());
        if (cd != null && !ArenaRules.mayPropose(now, cd)) {
            p.sendSystemMessage(text("§c[보스전] " + ((cd - now + 19) / 20) + "초 뒤에 다시 제안할 수 있습니다."));
            return;
        }
        List<ServerPlayer> party = Party.onlineMembers(p);
        for (ServerPlayer m : party) {
            if (m != p && (runOf(m.getUUID()) != null || voteOf(m.getUUID()) != null)) {
                p.sendSystemMessage(text("§c[보스전] 파티원 " + m.getName().getString() + "(이)가 다른 보스전 투표나 전투에 참여 중입니다."));
                return;
            }
        }
        String blocked = b.entryBlocked(p.serverLevel(), party);
        if (blocked != null) {
            p.sendSystemMessage(text("§c[보스전] " + blocked));
            return;
        }
        BlockPos altar = SealAltar.core(chapter);
        if (altar == null) altar = p.blockPosition();
        List<UUID> ids = party.stream().map(Entity::getUUID).toList();
        Vote v = new Vote(chapter, p.getUUID(), ids, now);
        if (v.result(now) == VoteResult.PASSED) {                                              // 파티가 없으면 바로
            start(s, v, altar);
            return;
        }
        VOTES.add(v);
        pendingAltar.put(v, altar);
        tell(s, ids, text("§6[보스전] " + p.getName().getString() + "님이 " + chapter + "장 보스 " + b.name() + "(와)과의 결전을 제안했습니다. §7(30초, 전원 동의해야 입장)"));
        MutableComponent row = text("  ").copy()
                .append(button("동의", "/arena vote accept", ChatFormatting.GREEN)).append(text("   "))
                .append(button("거절", "/arena vote decline", ChatFormatting.RED));
        for (UUID u : ids) {
            ServerPlayer m = online(s, u);
            if (m != null && !u.equals(p.getUUID())) m.sendSystemMessage(row);
        }
        p.sendSystemMessage(text("§7(투표를 시작했습니다. 파티원 " + (ids.size() - 1) + "명의 응답을 기다립니다)"));
    }

    private static final Map<Vote, BlockPos> pendingAltar = new HashMap<>();

    /** /arena vote accept|decline */
    public static String vote(ServerPlayer p, boolean accept) {
        Vote v = voteOf(p.getUUID());
        if (v == null) return "진행 중인 보스전 투표가 없습니다.";
        if (v.voted(p.getUUID())) return "이미 투표했습니다.";
        if (accept) v.accept(p.getUUID());
        else v.decline(p.getUUID());
        tell(p.server, v.members, text("§7[보스전] " + p.getName().getString() + ": " + (accept ? "§a동의" : "§c거절")
                + " §7(" + v.yesCount() + "/" + v.members.size() + ")"));
        resolve(p.server, v, now(p.server));
        return null;
    }

    private static void resolve(MinecraftServer s, Vote v, long now) {
        VoteResult r = v.result(now);
        if (r == VoteResult.PENDING) return;
        VOTES.remove(v);
        BlockPos altar = pendingAltar.remove(v);
        if (r == VoteResult.PASSED) {
            start(s, v, altar);
            return;
        }
        ServerPlayer d = v.declinedBy() == null ? null : online(s, v.declinedBy());
        String why = r == VoteResult.EXPIRED ? "30초 안에 모두 응답하지 않았습니다"
                : (d != null ? d.getName().getString() : "파티원") + "(이)가 거절했습니다(또는 접속을 끊었습니다)";
        tell(s, v.members, text("§c[보스전] 입장이 취소되었습니다 — " + why + ". §710초 뒤에 다시 제안할 수 있습니다."));
        long at = ArenaRules.retryAt(now);
        for (UUID u : v.members) COOLDOWN.put(u, at);
    }

    private static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("클릭해서 " + label))));
    }

    // ───────────────────────── 진행 ─────────────────────────

    private static void start(MinecraftServer s, Vote v, BlockPos altar) {
        ManagedBoss b = ManagedBoss.forChapter(v.chapter);
        if (RUNS.containsKey(v.chapter)) {
            tell(s, v.members, text("§c[보스전] 그 사이 다른 일행이 먼저 들어갔습니다."));
            return;
        }
        Run r = new Run(v.chapter, b, v.members, altar, now(s));
        RUNS.put(v.chapter, r);
        tell(s, r.members, text("§6[보스전] 모두 동의했다. 5초 뒤 " + b.name() + "(이)가 기다리는 전장으로 이동한다."));
    }

    /** /magiccity arena start: 시험용, 혼자 바로 (입장 조건 무시) */
    public static String forceStart(ServerPlayer p, int chapter) {
        ManagedBoss b = ManagedBoss.forChapter(chapter);
        if (b == null) return "장은 1~12";
        if (RUNS.containsKey(chapter)) return chapter + "장 전장이 이미 쓰이는 중";
        if (runOf(p.getUUID()) != null) return "이미 전장에 있음";
        BlockPos altar = SealAltar.core(chapter);
        start(p.server, new Vote(chapter, p.getUUID(), List.of(p.getUUID()), now(p.server)), altar != null ? altar : p.blockPosition());
        return null;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        long now = now(s);
        int t = s.getTickCount();
        if (t % 40 == 0) SealAltar.tick(s);
        if (t % 20 == 0) {
            for (Vote v : new ArrayList<>(VOTES)) {
                for (UUID u : v.members) if (online(s, u) == null) v.decline(u);           // 접속 끊김 = 거절
                resolve(s, v, now);
            }
        }
        if (t % 5 != 0 || RUNS.isEmpty()) return;
        ServerLevel arena = s.getLevel(ARENA);
        if (arena == null) return;
        for (Run r : new ArrayList<>(RUNS.values())) step(s, arena, r, now, t);
    }

    private static void step(MinecraftServer s, ServerLevel arena, Run r, long now, int t) {
        long el = now - r.phaseStart;
        switch (r.phase) {
            case COUNTDOWN -> {
                for (long k = el - 4; k <= el; k++) {
                    int sec = ArenaRules.countdownSecond(k);
                    if (sec > 0) tell(s, r.members, text("§e[보스전] " + sec + "…"));
                }
                if (el >= ArenaRules.COUNTDOWN_TICKS) enter(s, arena, r, now);
            }
            case SPAWN_WAIT -> {
                keepInside(arena, r);
                if (el >= ArenaRules.SPAWN_DELAY_TICKS) {
                    Mob m = r.boss.arenaSpawn(arena, r.bossSpot(), alive(s, r));
                    if (m == null) {
                        finish(s, arena, r, "§c[보스전] 보스를 부르지 못했습니다. 제단으로 돌아갑니다.");
                        return;
                    }
                    r.bossId = m.getUUID();
                    r.phase = Phase.FIGHT;
                    r.phaseStart = now;
                }
            }
            case FIGHT -> {
                keepInside(arena, r);
                Mob boss = arena.getEntity(r.bossId) instanceof Mob m && m.isAlive() ? m : null;
                List<ServerPlayer> alive = alive(s, r);
                ArenaRules.Outcome o = ArenaRules.outcome(false, alive.size(), el);
                if (o == ArenaRules.Outcome.WIPE) {
                    finish(s, arena, r, "§c[보스전] 일행이 모두 쓰러졌다… 제단으로 돌아갑니다. (다시 도전할 수 있음)");
                    return;
                }
                if (o == ArenaRules.Outcome.TIMEOUT) {
                    finish(s, arena, r, "§c[보스전] 30분이 지났다. " + r.boss.name() + "(이)가 어둠 속으로 물러났다… 제단으로 돌아갑니다.");
                    return;
                }
                if (boss == null) {
                    finish(s, arena, r, "§c[보스전] 보스가 전장에서 사라졌다. 제단으로 돌아갑니다.");
                    return;
                }
                Vec3 c = r.bossSpot();
                if (r.outside(boss.position())) boss.teleportTo(c.x, c.y, c.z);
                r.boss.arenaTick(arena, boss, alive, c, t);
                if (el % 1200 == 0 && el > 0) {
                    long left = (ArenaRules.TIME_LIMIT_TICKS - el) / 1200;
                    if (left == 10 || left == 5 || left == 1) tell(s, r.members, text("§e[보스전] 남은 시간 " + left + "분"));
                }
            }
            case VICTORY -> {
                keepInside(arena, r);
                if (el >= ArenaRules.VICTORY_TICKS) finish(s, arena, r, "§a[보스전] 제단으로 돌아갑니다.");
            }
        }
    }

    /** 살아 있는(관전 아님) 참가자 중 전장에 있는 사람 */
    private static List<ServerPlayer> alive(MinecraftServer s, Run r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : r.members) {
            ServerPlayer p = online(s, u);
            if (p != null && !r.dead.contains(u) && p.level().dimension() == ARENA) out.add(p);
        }
        return out;
    }

    /** 전장을 깔고 접속 중인 참가자 이동 */
    private static void enter(MinecraftServer s, ServerLevel arena, Run r, long now) {
        int cx = r.floor.getX() >> 4, cz = r.floor.getZ() >> 4;
        for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) {
            arena.setChunkForced(x, z, true);
            r.forced.add(new ChunkPos(x, z));
        }
        clearEntities(arena, r);
        // 실제 보스 방 사본 (처음이면 지금 지역에서 뜸). 없으면 공용 전장 (2026-11-20)
        BossRooms.clearArea(arena, r.floor.getX(), r.floor.getZ(), 52, 20, 160);
        BossRooms.Room room = BossRooms.get(s, r.boss);
        if (room != null) {
            r.room = BossRooms.place(arena, room, r.floor.getX(), ArenaRules.FLOOR_Y, r.floor.getZ());
            r.roomPlayerSpot = BossRooms.playerSpot(arena, r.room);
            clearEntities(arena, r);                                                    // 깔면서 생긴 떨어진 물건 등
        } else {
            ArenaShapes.build(arena, r.floor, r.boss.arenaShape());
        }
        Vec3 spot = r.playerSpot();
        for (UUID u : new ArrayList<>(r.members)) {
            ServerPlayer p = online(s, u);
            if (p == null || !p.isAlive()) {
                r.members.remove(u);
                continue;
            }
            CompoundTag ret = new CompoundTag();
            BlockPos back = r.altar.above();
            ret.putDouble("x", back.getX() + 0.5);
            ret.putDouble("y", back.getY());
            ret.putDouble("z", back.getZ() + 0.5);
            ret.putInt("mode", p.gameMode.getGameModeForPlayer().getId());
            p.getPersistentData().put(RETURN_KEY, ret);
            p.stopRiding();
            p.teleportTo(arena, spot.x, spot.y, spot.z, 180F, 0F);
            p.sendSystemMessage(text("§6[보스전] " + r.chapter + "장 전장 — 블록을 부수거나 놓을 수 없다. 쓰러지면 아이템은 그대로, 관전으로 남는다. 제한 시간 30분."));
        }
        if (r.members.isEmpty()) {
            finish(s, arena, r, null);
            return;
        }
        arena.playSound(null, BlockPos.containing(r.bossSpot()), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1F, 0.6F);
        r.phase = Phase.SPAWN_WAIT;
        r.phaseStart = now;
    }

    /** 경계 밖·바닥 아래로 나간 참가자(관전 포함)를 시작 자리로 */
    private static void keepInside(ServerLevel arena, Run r) {
        Vec3 spot = r.playerSpot();
        for (UUID u : r.members) {
            ServerPlayer p = arena.getServer().getPlayerList().getPlayer(u);
            if (p == null || p.level() != arena) continue;
            if (r.outside(p.position())) {
                p.teleportTo(arena, spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
            }
        }
    }

    /** 승리: 참가자 모두 장 완료 + 꾸러미 → 30초 뒤 귀환 */
    private static void victory(MinecraftServer s, ServerLevel arena, Run r, Mob boss) {
        r.phase = Phase.VICTORY;
        r.phaseStart = now(s);
        r.boss.defeated(arena, boss);
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID u : r.members) {
            ServerPlayer p = online(s, u);
            if (p != null) players.add(p);
        }
        Story.creditPlayers(players, r.chapter);
        ItemStack pouch = LootPouchItem.make(r.boss.name() + "의 전리품 꾸러미", List.of(r.boss.lootStacks()));
        for (ServerPlayer p : players) Pouches.give(p, pouch, ARENA, Vec3.atCenterOf(r.floor), 200);
        if (r.boss.hasPreFight()) r.boss.resetPreFight();
        tell(s, r.members, text("§a[보스전] 승리! 30초 뒤 제단으로 돌아갑니다."));
    }

    /** 끝: 참가자를 제단으로, 전장 정리 */
    private static void finish(MinecraftServer s, ServerLevel arena, Run r, String msg) {
        // 먼저 목록에서 뺀다: 아래 귀환 이동이 차원 이동 이벤트(onChangeDimension)를 부르는데,
        // 그때 이 전투가 아직 목록에 있으면 참가자 목록을 고치거나 finish 를 다시 불러 서버가 멈춘다 (v0.19 게임 확인에서 발견)
        if (RUNS.get(r.chapter) != r) return;
        RUNS.remove(r.chapter);
        List<UUID> members = new ArrayList<>(r.members);
        if (msg != null) tell(s, members, text(msg));
        for (UUID u : members) {
            ServerPlayer p = online(s, u);
            if (p != null) sendBack(p);
        }
        r.boss.arenaEnd();
        clearEntities(arena, r);
        for (ChunkPos c : r.forced) arena.setChunkForced(c.x, c.z, false);
    }

    private static void clearEntities(ServerLevel arena, Run r) {
        AABB box = new AABB(r.floor).inflate(48, 60, 48);
        for (Entity e : arena.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
    }

    /** 저장해 둔 자리·게임 모드로 돌려보냄 */
    private static void sendBack(ServerPlayer p) {
        CompoundTag ret = p.getPersistentData().getCompound(RETURN_KEY);
        p.getPersistentData().remove(RETURN_KEY);
        ServerLevel k = p.server.getLevel(ModWorldgen.KINGDOM);
        if (k == null) k = p.server.overworld();
        GameType mode = ret.contains("mode") ? GameType.byId(ret.getInt("mode")) : GameType.SURVIVAL;
        if (mode == GameType.SPECTATOR) mode = GameType.SURVIVAL;
        p.setGameMode(mode);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.fallDistance = 0;
        if (ret.contains("x")) p.teleportTo(k, ret.getDouble("x"), ret.getDouble("y"), ret.getDouble("z"), p.getYRot(), 0F);
        else {
            BlockPos sp = k.getSharedSpawnPos();
            p.teleportTo(k, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), 0F);
        }
    }

    // ───────────────────────── 이벤트 ─────────────────────────

    /** 죽음: 보스면 승리, 참가자면 아이템을 잃지 않고 관전 (부활의 기도·불사의 토템처럼 죽음을 막은 경우는 이미 취소돼 여기 오지 않음) */
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level) || level.dimension() != ARENA) return;
        if (event.getEntity() instanceof ServerPlayer p) {
            Run r = runOf(p.getUUID());
            if (r == null) return;
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.clearFire();
            p.setGameMode(GameType.SPECTATOR);
            r.dead.add(p.getUUID());
            tell(p.server, r.members, text("§c[보스전] " + p.getName().getString() + "(이)가 쓰러졌다 — 관전으로 남습니다. §7(살아 있는 일행 " + alive(p.server, r).size() + "명)"));
            return;
        }
        for (Run r : RUNS.values()) {
            if (r.phase == Phase.FIGHT && event.getEntity().getUUID().equals(r.bossId) && event.getEntity() instanceof Mob m) {
                victory(level.getServer(), level, r, m);
                return;
            }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Vote v = voteOf(p.getUUID());
        if (v != null) v.decline(p.getUUID());
        Run r = runOf(p.getUUID());
        if (r == null) return;
        r.members.remove(p.getUUID());
        r.dead.remove(p.getUUID());
        if (r.members.isEmpty()) {
            ServerLevel arena = p.server.getLevel(ARENA);
            if (arena != null) finish(p.server, arena, r, null);
        }
    }

    /** 다시 접속: 전투에 속하지 않은 채 전장에 있으면 저장해 둔 자리로 */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.level().dimension() == ARENA && runOf(p.getUUID()) == null) {
            sendBack(p);
            p.sendSystemMessage(text("§7[보스전] 전투가 끝나 있어 제단으로 돌아왔습니다."));
        } else if (p.level().dimension() != ARENA && p.getPersistentData().contains(RETURN_KEY)) {
            CompoundTag ret = p.getPersistentData().getCompound(RETURN_KEY);
            p.getPersistentData().remove(RETURN_KEY);
            GameType mode = ret.contains("mode") ? GameType.byId(ret.getInt("mode")) : GameType.SURVIVAL;
            if (p.isSpectator()) p.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        }
    }

    /** 전장을 벗어나면(명령 이동 등) 참가자에서 빠짐 */
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getFrom() != ARENA) return;
        Run r = runOf(p.getUUID());
        if (r == null) return;
        r.members.remove(p.getUUID());
        r.dead.remove(p.getUUID());
        p.getPersistentData().remove(RETURN_KEY);
        if (p.isSpectator()) p.setGameMode(GameType.SURVIVAL);
        if (r.members.isEmpty()) {
            ServerLevel arena = p.server.getLevel(ARENA);
            if (arena != null) finish(p.server, arena, r, null);
        }
    }

    private static boolean inArena(Level level) {
        return level != null && level.dimension() == ARENA;
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && inArena(l)) event.setCanceled(true);          // 크리에이티브도 막음 (2026-11-20)
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level l && inArena(l)) event.setCanceled(true);
    }

    public static void onExplode(ExplosionEvent.Detonate event) {
        if (inArena(event.getLevel())) event.getAffectedBlocks().clear();
    }

    private static boolean forbidden(ItemStack s) {
        return s.getItem() instanceof BucketItem || s.getItem() instanceof EnderpearlItem || s.getItem() instanceof ChorusFruitItem
                || s.getItem() instanceof BoatItem || s.getItem() instanceof MinecartItem;
    }

    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (inArena(event.getLevel()) && forbidden(event.getItemStack())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer p) p.displayClientMessage(text("§c전장에서는 쓸 수 없습니다."), true);
        }
    }

    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (inArena(event.getLevel()) && forbidden(event.getItemStack())) event.setCanceled(true);
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (inArena(event.getLevel()) && (event.getEntity() instanceof Boat || event.getEntity() instanceof AbstractMinecart || event.getEntity() instanceof ThrownEnderpearl))
            event.setCanceled(true);
    }

    /** 전장에서는 몹이 블록을 바꾸지 못함 (좀벌레가 돌벽돌 바닥에 숨어 보스가 사라지던 문제, 약탈수 잎 부수기 등) */
    public static void onGrief(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) {
        if (inArena(event.getEntity().level())) event.setCanGrief(false);
    }

    public static void onPearl(EntityTeleportEvent.EnderPearl event) {
        if (inArena(event.getEntity().level())) event.setCanceled(true);
    }

    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (inArena(event.getEntity().level())) event.setCanceled(true);
    }

    // ───────────────────────── 표시·관리 ─────────────────────────

    public static String status() {
        StringBuilder b = new StringBuilder("§6[보스전] 전장 " + RUNS.size() + "곳, 투표 " + VOTES.size() + "건");
        for (Run r : RUNS.values())
            b.append("\n§7").append(r.chapter).append("장 ").append(r.boss.name()).append(" — ").append(r.phase)
                    .append(", 참가 ").append(r.members.size()).append("명 (쓰러짐 ").append(r.dead.size()).append(")");
        return b.toString();
    }

    /** /magiccity arena stop <장> */
    public static boolean stop(MinecraftServer s, int chapter) {
        Run r = RUNS.get(chapter);
        ServerLevel arena = s.getLevel(ARENA);
        if (r == null || arena == null) return false;
        Mob boss = r.bossId == null ? null : arena.getEntity(r.bossId) instanceof Mob m ? m : null;
        if (boss != null) boss.discard();
        finish(s, arena, r, "§c[보스전] 관리자가 전장을 닫았습니다.");
        return true;
    }
}
