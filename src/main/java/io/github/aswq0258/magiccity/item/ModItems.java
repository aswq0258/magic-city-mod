package io.github.aswq0258.magiccity.item;

import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** magiccity 아이템 (2026-11-19: 보스 전리품 꾸러미) */
public final class ModItems {
    private ModItems() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagicCityMod.MODID);

    /** 보스 전리품 꾸러미: 오른쪽 클릭하면 안의 보상이 인벤토리로 (넘치면 발밑) */
    public static final DeferredItem<LootPouchItem> LOOT_POUCH = ITEMS.register("loot_pouch", () -> new LootPouchItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
