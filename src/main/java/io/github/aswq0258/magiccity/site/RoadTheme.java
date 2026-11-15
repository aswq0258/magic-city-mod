package io.github.aswq0258.magiccity.site;

import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 길(지역)마다의 성격: 출몰 몬스터, 둥지 스포너, 필드보스, 둥지 재료.
 * 순서는 KingdomRoads.ROADS 와 같음.
 */
public record RoadTheme(String region, String desc, EntityType<? extends Mob>[] mobs, EntityType<?> spawner, Boss boss,
                        BlockState wall, BlockState accent, BlockState ground, BlockState light) {

    /** 필드보스: 바닐라 몹을 키우고 강화 */
    public record Boss(String name, EntityType<? extends Mob> type, double scale, double hp, double damage, Item weapon, BossEvent.BossBarColor color) {}

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    @SafeVarargs
    private static EntityType<? extends Mob>[] mobs(EntityType<? extends Mob>... t) {
        return t;
    }

    public static final RoadTheme[] THEMES = {
            new RoadTheme("설산 원정길", "눈보라 속 떠도는 망자들", mobs(EntityType.STRAY, EntityType.SKELETON, EntityType.STRAY), EntityType.STRAY,
                    new Boss("설원의 백곰왕", EntityType.POLAR_BEAR, 3.0, 340, 14, null, BossEvent.BossBarColor.WHITE),
                    s(Blocks.SPRUCE_LOG), s(Blocks.PACKED_ICE), s(Blocks.SNOW_BLOCK), s(Blocks.LANTERN)),
            new RoadTheme("거미 숲길", "거미줄로 뒤덮인 숲", mobs(EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.SPIDER), EntityType.SPIDER,
                    new Boss("숲의 여왕거미", EntityType.SPIDER, 3.5, 280, 12, null, BossEvent.BossBarColor.PURPLE),
                    s(Blocks.DARK_OAK_LOG), s(Blocks.COBWEB), s(Blocks.MOSS_BLOCK), s(Blocks.SOUL_LANTERN)),
            new RoadTheme("광산 무역로", "버려진 광부들의 망령", mobs(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE), EntityType.ZOMBIE,
                    new Boss("산악 파괴수", EntityType.RAVAGER, 1.6, 420, 16, null, BossEvent.BossBarColor.YELLOW),
                    s(Blocks.COBBLED_DEEPSLATE), s(Blocks.RAIL), s(Blocks.GRAVEL), s(Blocks.LANTERN)),
            new RoadTheme("비룡 고갯길", "하늘을 가르는 날개", mobs(EntityType.PHANTOM, EntityType.SKELETON, EntityType.SPIDER), EntityType.SKELETON,
                    new Boss("고갯길의 비룡", EntityType.PHANTOM, 4.0, 240, 12, null, BossEvent.BossBarColor.BLUE),
                    s(Blocks.STONE_BRICKS), s(Blocks.BONE_BLOCK), s(Blocks.COBBLESTONE), s(Blocks.LANTERN)),
            new RoadTheme("상단 교역로", "상단을 노리는 약탈단", mobs(EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.PILLAGER), EntityType.PILLAGER,
                    new Boss("약탈단 흑마법사", EntityType.EVOKER, 2.0, 280, 10, null, BossEvent.BossBarColor.RED),
                    s(Blocks.DARK_OAK_PLANKS), s(Blocks.HAY_BLOCK), s(Blocks.COARSE_DIRT), s(Blocks.LANTERN)),
            new RoadTheme("역병 왕도", "버려진 농가의 역병", mobs(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK), EntityType.ZOMBIE,
                    new Boss("역병 거인", EntityType.ZOMBIE, 3.2, 380, 15, Items.IRON_AXE, BossEvent.BossBarColor.GREEN),
                    s(Blocks.OAK_LOG), s(Blocks.HAY_BLOCK), s(Blocks.COARSE_DIRT), s(Blocks.LANTERN)),
            new RoadTheme("물귀신 강변길", "강에서 올라온 익사자", mobs(EntityType.DROWNED, EntityType.DROWNED, EntityType.ZOMBIE), EntityType.DROWNED,
                    new Boss("강의 군주", EntityType.DROWNED, 3.0, 340, 14, Items.TRIDENT, BossEvent.BossBarColor.BLUE),
                    s(Blocks.MUD_BRICKS), s(Blocks.PRISMARINE), s(Blocks.MUD), s(Blocks.SEA_LANTERN)),
            new RoadTheme("늪 교역로", "늪지 마녀의 저주", mobs(EntityType.WITCH, EntityType.SLIME, EntityType.BOGGED), EntityType.SLIME,
                    new Boss("늪의 마녀", EntityType.WITCH, 2.5, 280, 10, null, BossEvent.BossBarColor.PURPLE),
                    s(Blocks.MANGROVE_LOG), s(Blocks.SLIME_BLOCK), s(Blocks.MUD), s(Blocks.SOUL_LANTERN)),
            new RoadTheme("저주받은 국경로", "무덤에서 일어난 국경 수비대", mobs(EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER), EntityType.SKELETON,
                    new Boss("저주받은 궁기사", EntityType.SKELETON, 2.6, 320, 12, Items.BOW, BossEvent.BossBarColor.PINK),
                    s(Blocks.DEEPSLATE_BRICKS), s(Blocks.COBBLESTONE_WALL), s(Blocks.SOUL_SOIL), s(Blocks.SOUL_LANTERN)),
            new RoadTheme("마계 오염 옛길", "열린 마계문에서 흘러나온 것들", mobs(EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.WITHER_SKELETON), EntityType.BLAZE,
                    new Boss("균열의 감시자", EntityType.BLAZE, 3.0, 320, 12, null, BossEvent.BossBarColor.RED),
                    s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.MAGMA_BLOCK), s(Blocks.NETHERRACK), s(Blocks.SOUL_LANTERN)),
    };
}
