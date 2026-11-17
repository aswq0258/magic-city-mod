package com.royalcity.qol.nav;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 내장 지역 + 사용자 웨이포인트 목록과 조회. 저장은 {@link WaypointJson}. */
public final class WaypointBook {
    private final List<Waypoint> builtin;
    private final List<Waypoint> user = new ArrayList<>();

    public WaypointBook(List<Waypoint> builtin) {
        this.builtin = List.copyOf(builtin);
    }

    public List<Waypoint> user() {
        return List.copyOf(user);
    }

    public List<Waypoint> all() {
        List<Waypoint> l = new ArrayList<>(builtin);
        l.addAll(user);
        return l;
    }

    public List<Waypoint> inDimension(String dim) {
        return all().stream().filter(w -> w.dimension().equals(dim)).toList();
    }

    /** 불러온 사용자 목록으로 바꾼다. 내장 지역과 이름이 겹치거나 중복된 항목은 건너뛰고 건너뛴 수를 돌려준다 */
    public int replaceUser(List<Waypoint> loaded) {
        user.clear();
        int skipped = 0;
        for (Waypoint w : loaded) {
            if (exact(w.name()).isPresent()) {
                skipped++;
                continue;
            }
            user.add(new Waypoint(w.name(), w.dimension(), w.x(), w.y(), w.z(), w.radius(), false, w.note()));
        }
        return skipped;
    }

    public enum AddResult { ADDED, DUPLICATE }

    public AddResult add(Waypoint w) {
        if (exact(w.name()).isPresent()) return AddResult.DUPLICATE;
        user.add(new Waypoint(w.name(), w.dimension(), w.x(), w.y(), w.z(), w.radius(), false, w.note()));
        return AddResult.ADDED;
    }

    public enum RemoveResult { REMOVED, BUILTIN, NOT_FOUND }

    public RemoveResult remove(String name) {
        Optional<Waypoint> w = exact(name);
        if (w.isEmpty()) return RemoveResult.NOT_FOUND;
        if (w.get().builtin()) return RemoveResult.BUILTIN;
        user.remove(w.get());
        return RemoveResult.REMOVED;
    }

    private static String norm(String s) {
        return s.strip().replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public Optional<Waypoint> exact(String name) {
        String n = norm(name);
        return all().stream().filter(w -> norm(w.name()).equals(n)).findFirst();
    }

    /** 이름 찾기: 정확히 같은 이름 → 앞부분 일치 → 포함. 후보가 여러 개면 비어 있는 결과와 후보 목록 */
    public Match find(String query) {
        Optional<Waypoint> e = exact(query);
        if (e.isPresent()) return new Match(e, List.of(e.get()));
        String q = norm(query);
        List<Waypoint> prefix = all().stream().filter(w -> norm(w.name()).startsWith(q)).toList();
        if (prefix.size() == 1) return new Match(Optional.of(prefix.get(0)), prefix);
        if (prefix.size() > 1) return new Match(Optional.empty(), prefix);
        List<Waypoint> part = all().stream().filter(w -> norm(w.name()).contains(q)).toList();
        if (part.size() == 1) return new Match(Optional.of(part.get(0)), part);
        return new Match(Optional.empty(), part);
    }

    public record Match(Optional<Waypoint> chosen, List<Waypoint> candidates) {}

    /** 같은 차원에서 가까운 순 n 개 */
    public List<Waypoint> nearest(String dim, double x, double z, int n) {
        return inDimension(dim).stream()
                .sorted(Comparator.comparingDouble(w -> w.distanceTo(x, z)))
                .limit(n)
                .toList();
    }

    /** 지금 들어가 있는 지역: 반경 안인 것 중 반경이 가장 작은 것 (촌락이 큰 지역보다 우선) */
    public Optional<Waypoint> regionAt(String dim, double x, double z) {
        return inDimension(dim).stream()
                .filter(w -> w.contains(dim, x, z))
                .min(Comparator.comparingInt(Waypoint::radius).thenComparingDouble(w -> w.distanceTo(x, z)));
    }
}
