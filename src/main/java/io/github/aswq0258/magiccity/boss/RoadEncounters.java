package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.RoadSites;
import io.github.aswq0258.magiccity.site.RoadTheme;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 길 위 몬스터 출몰: 왕국 대륙에서 길(또는 길가 40칸 안)을 걷는 플레이어 주변에
 * 그 길의 몬스터 무리(2~4마리) 가 낮밤 없이 나타남. 쉼터 근처·왕성도시·옛 왕도 안에서는 나오지 않음.
 * 새 길(지역) 에 들어서면 지역 이름을 화면에 알려 줌.
 */
public final class RoadEncounters {
    private RoadEncounters() {}

    private static final String TAG = "magiccity_patrol";
    private static final Map<UUID, Integer> lastRegion = new HashMap<>();
    private static int tick;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 40 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModWorldgen.KINGDOM);
        if (level == null) return;
        double[] edge = new double[1];
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int ri = KingdomRoads.nearest(p.getX(), p.getZ(), 40, edge);
            if (KingdomRoads.excluded(p.getX(), p.getZ())) ri = -1;
            Integer prev = lastRegion.put(p.getUUID(), ri);
            if (ri >= 0 && (prev == null || prev != ri)) {
                RoadTheme t = RoadTheme.THEMES[ri];
                p.displayClientMessage(Component.literal("§e" + KingdomRoads.ROADS[ri].name() + " §7— §f" + t.region() + " §8(" + t.desc() + ")"), true);
            }
            if (ri < 0 || p.isCreative() || level.getDifficulty() == Difficulty.PEACEFUL) continue;
            if (tick % 200 != 0 || level.random.nextFloat() > 0.45F) continue;
            if (nearSafe(p)) continue;
            int count = level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(64), m -> m.getTags().contains(TAG)).size();
            if (count >= 6) continue;
            spawnGroup(level, p, ri);
        }
    }

    private static boolean nearSafe(ServerPlayer p) {
        if (Math.hypot(p.getX() - KingdomTerrain.OLD_X, p.getZ() - KingdomTerrain.OLD_Z) < 260) return true;
        if (Math.max(Math.abs(p.getX() - KingdomTerrain.CX), Math.abs(p.getZ() - KingdomTerrain.CZ)) < 680) return true;   // 왕성도시 + 신시가지
        if (io.github.aswq0258.magiccity.site.DwarfHold.near(p.getX(), p.getZ())) return true;
        if (io.github.aswq0258.magiccity.site.Hamlets.near(p.getX(), p.getZ())) return true;
        if (Math.hypot(p.getX() - io.github.aswq0258.magiccity.site.WorldTree.OX, p.getZ() - io.github.aswq0258.magiccity.site.WorldTree.OZ) < io.github.aswq0258.magiccity.site.WorldTree.R + 20) return true;
        for (RoadSites.Site s : RoadSites.sites()) {
            if (s.kind() == RoadSites.Kind.REST && Math.hypot(p.getX() - s.x(), p.getZ() - s.z()) < 64) return true;
        }
        return false;
    }

    private static void spawnGroup(ServerLevel level, ServerPlayer p, int ri) {
        RoadTheme t = RoadTheme.THEMES[ri];
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = 26 + level.random.nextDouble() * 14;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            if (!level.getFluidState(ground).isEmpty() && t.mobs()[0] != EntityType.DROWNED) continue;
            int n = 2 + level.random.nextInt(3);
            for (int i = 0; i < n; i++) {
                EntityType<? extends Mob> type = t.mobs()[level.random.nextInt(t.mobs().length)];
                BlockPos at = new BlockPos(x + level.random.nextInt(5) - 2, y + (type == EntityType.PHANTOM || type == EntityType.BLAZE ? 6 : 0), z + level.random.nextInt(5) - 2);
                at = new BlockPos(at.getX(), Math.max(at.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ())), at.getZ());
                Mob m = type.spawn(level, at, MobSpawnType.EVENT);
                if (m == null) continue;
                m.addTag(TAG);
                dress(m);
            }
            return;
        }
    }

    /** 햇빛에 타는 몹은 투구 / 화염 저항 */
    static void dress(Mob m) {
        EntityType<?> t = m.getType();
        if (t == EntityType.ZOMBIE || t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.ZOMBIE_VILLAGER
                || t == EntityType.DROWNED || t == EntityType.BOGGED) {
            if (m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                m.setDropChance(EquipmentSlot.HEAD, 0.05F);
            }
        } else if (t == EntityType.PHANTOM) {
            m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        }
    }
}
