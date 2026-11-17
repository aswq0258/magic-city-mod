package com.royalcity.qol;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.royalcity.qol.client.QolCommands;
import com.royalcity.qol.client.QolHud;
import com.royalcity.qol.client.QolState;
import com.royalcity.qol.nav.NavMath;
import com.royalcity.qol.nav.Waypoint;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/** 마법 왕국 대륙 편의 모드. 클라이언트 전용: 전용 서버에서는 이 클래스가 만들어지지 않는다 */
@Mod(value = RoyalQol.MOD_ID, dist = Dist.CLIENT)
public final class RoyalQol {
    public static final String MOD_ID = "royalqol";
    private static final Logger LOG = LogUtils.getLogger();

    private static final String CATEGORY = "key.categories.royalqol";
    public static final KeyMapping KEY_HUD = new KeyMapping("key.royalqol.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping KEY_NEAR = new KeyMapping("key.royalqol.near", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    public RoyalQol(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
            e.register(KEY_HUD);
            e.register(KEY_NEAR);
        });
        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(MOD_ID, "hud"), QolHud::render));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> QolCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
            QolState.lastRegion = null;
            QolState.cycle = -1;
        });
        LOG.info("[royalqol] {}", QolState.load().replace('\n', ' '));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        String dim = mc.level.dimension().location().toString();

        while (KEY_HUD.consumeClick()) {
            QolState.hud = !QolState.hud;
            QolState.save();
            mc.gui.setOverlayMessage(Component.literal("HUD " + (QolState.hud ? "켜짐" : "꺼짐")), false);
        }
        while (KEY_NEAR.consumeClick()) {
            List<Waypoint> near = QolState.BOOK.nearest(dim, p.getX(), p.getZ(), 5);
            if (near.isEmpty()) {
                mc.gui.setOverlayMessage(Component.literal("이 차원에는 웨이포인트가 없습니다"), false);
            } else {
                QolState.cycle = (QolState.cycle + 1) % near.size();
                Waypoint w = near.get(QolState.cycle);
                QolState.destination = w;
                mc.gui.setOverlayMessage(Component.literal("목적지 " + (QolState.cycle + 1) + "/" + near.size() + ": " + w.name()
                        + " " + NavMath.distance(w.distanceTo(p.getX(), p.getZ()))), false);
            }
        }

        // 지역 진입 알림 (화면 아래)
        Optional<Waypoint> here = QolState.BOOK.regionAt(dim, p.getX(), p.getZ());
        String name = here.map(Waypoint::name).orElse(null);
        if (name != null && !name.equals(QolState.lastRegion)) {
            mc.gui.setOverlayMessage(Component.literal("― " + name + " ―"), false);
        }
        QolState.lastRegion = name;

        // 목적지 도착 (채팅)
        Waypoint d = QolState.destination;
        if (d != null && d.dimension().equals(dim) && d.distanceTo(p.getX(), p.getZ()) <= NavMath.arrivalRange(d)) {
            p.displayClientMessage(Component.literal("[royalqol] 목적지에 도착했습니다: " + d.name()), false);
            QolState.destination = null;
            QolState.cycle = -1;
        }
    }
}
