package io.github.aswq0258.magiccity.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 보스 전리품 꾸러미 (필드보스·챕터 보스 전장 공통, 2026-11-19). 내용은 {@link DataComponents#CONTAINER}.
 * 오른쪽 클릭하면 안의 아이템을 모두 인벤토리로 옮기고(넘치면 발밑) 꾸러미는 사라진다.
 */
public class LootPouchItem extends Item {
    public LootPouchItem(Properties p) {
        super(p);
    }

    /** 꾸러미 만들기 */
    public static ItemStack make(String title, List<ItemStack> contents) {
        ItemStack s = new ItemStack(ModItems.LOOT_POUCH.get());
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack c : contents) if (!c.isEmpty()) copy.add(c.copy());
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copy));
        s.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(ChatFormatting.GOLD).withStyle(s2 -> s2.withItalic(false)));
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pouch = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(pouch);
        ItemContainerContents c = pouch.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<ItemStack> items = new ArrayList<>();
        c.nonEmptyItemsCopy().forEach(items::add);
        pouch.shrink(1);
        for (ItemStack s : items) {
            if (!sp.getInventory().add(s)) sp.drop(s, false);
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 1.0F);
        sp.displayClientMessage(Component.literal("§6전리품 꾸러미를 열었다 §7(" + items.size() + "가지)"), true);
        return InteractionResultHolder.consume(pouch);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        ItemContainerContents c = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        c.nonEmptyItemsCopy().forEach(s -> tip.add(Component.literal("  " + s.getCount() + " × ").withStyle(ChatFormatting.GRAY).append(s.getHoverName())));
        tip.add(Component.literal("오른쪽 클릭해서 열기").withStyle(ChatFormatting.DARK_GRAY));
    }
}
