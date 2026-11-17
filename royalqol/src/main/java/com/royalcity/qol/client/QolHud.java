package com.royalcity.qol.client;

import com.royalcity.qol.nav.NavMath;
import com.royalcity.qol.nav.Waypoint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 왼쪽 위 HUD: 좌표·방위, 날짜·시각, 현재 지역, 가장 가까운 지역, 목적지 거리와 화살표 */
public final class QolHud {
    private QolHud() {}

    public static List<String> lines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return out;
        String dim = mc.level.dimension().location().toString();
        double x = p.getX(), z = p.getZ();
        out.add(String.format("XYZ %d %d %d  %s", p.getBlockX(), p.getBlockY(), p.getBlockZ(), NavMath.facing(p.getYRot())));
        long t = mc.level.getDayTime();
        out.add(NavMath.day(t) + "일차 " + NavMath.clock(t));
        Optional<Waypoint> here = QolState.BOOK.regionAt(dim, x, z);
        out.add("지역: " + here.map(Waypoint::name).orElse("-"));
        List<Waypoint> near = QolState.BOOK.nearest(dim, x, z, 2);
        for (Waypoint w : near) {
            if (here.isPresent() && here.get().equals(w)) continue;
            out.add("가까움: " + w.name() + " " + NavMath.distance(w.distanceTo(x, z)) + " " + NavMath.compassTo(x, z, w.x(), w.z()));
            break;
        }
        Waypoint d = QolState.destination;
        if (d != null) {
            if (d.dimension().equals(dim)) {
                out.add("목적지: " + d.name() + " " + NavMath.distance(d.distanceTo(x, z)) + " "
                        + NavMath.arrowTo(p.getYRot(), x, z, d.x(), d.z()));
            } else {
                out.add("목적지: " + d.name() + " (다른 차원)");
            }
        }
        return out;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!QolState.hud || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        List<String> lines = lines(mc);
        if (lines.isEmpty()) return;
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        int x = 4, y = 4, lh = mc.font.lineHeight + 2;
        g.fill(x - 2, y - 2, x + w + 2, y + lines.size() * lh, 0x80000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == lines.size() - 1 && QolState.destination != null ? 0xFFE7C66A : 0xFFFFFFFF;
            g.drawString(mc.font, lines.get(i), x, y + i * lh, color, true);
        }
    }
}
