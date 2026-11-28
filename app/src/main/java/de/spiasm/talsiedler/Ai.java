package de.spiasm.talsiedler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/**
 * Computergegner (Spieler Rot). Benutzt dieselben Befehle wie ein Mensch:
 * Gebäude setzen, Wege bauen, Land erweitern und angreifen.
 */
final class Ai implements Serializable {
    private static final long serialVersionUID = 1L;
    final int pl;

    Ai(int pl) {
        this.pl = pl;
    }

    private float t, attackT;
    private int step;
    private final Random r = new Random(4711);
    private final HashSet<Integer> bad = new HashSet<>();

    /** Wirtschaft: {Gebäude, gewünschte Anzahl}, der Reihe nach */
    private static final int[][] PLAN = {
            {World.B_WOOD, 1}, {World.B_QUARRY, 1}, {World.B_WOOD, 2}, {World.B_SAW, 1}, {World.B_FOREST, 1},
            {World.B_FISH, 1}, {World.B_HUNT, 1}, {World.B_FOREST, 2}, {World.B_WELL, 1}, {World.B_FARM, 1},
            {World.B_FARM, 2}, {World.B_COAL, 1}, {World.B_IRON, 1}, {World.B_SMELTER, 1}, {World.B_TOOLS, 1}, {World.B_MILL, 1},
            {World.B_BAKERY, 1}, {World.B_ARMORY, 1}, {World.B_WOOD, 3}, {World.B_FARM, 2}, {World.B_PIGS, 1},
            {World.B_BUTCHER, 1}, {World.B_QUARRY, 2}, {World.B_WELL, 2}, {World.B_GOLD, 1}, {World.B_COAL, 2},
            {World.B_SAW, 2}, {World.B_FISH, 2}, {World.B_HUNT, 2}, {World.B_FOREST, 3}, {World.B_WOOD, 4},
            {World.B_IRON, 2}, {World.B_SMELTER, 2}, {World.B_ARMORY, 2}, {World.B_FARM, 3}, {World.B_MILL, 2},
            {World.B_BAKERY, 2}, {World.B_STORE, 1}, {World.B_COAL, 3}, {World.B_GRANITE, 1}, {World.B_FARM, 4},
    };

    void tick(World w, float dt) {
        t += dt;
        float iv = w.ai == 1 ? 4f : w.ai == 2 ? 2.5f : 1.6f;
        if (t < iv) return;
        t = 0;
        step++;
        World.Building h = w.hqB(pl);
        if (h == null) return;
        if (step % 4 == 0) connectLoose(w);
        if (step % 15 == 7) cleanup(w);
        if (step % 10 == 3) toolPlan(w);
        if (w.combat()) {
            attackT += iv;
            float first = w.ai == 1 ? 2400 : w.ai == 2 ? 1500 : 900;
            float every = w.ai == 1 ? 150 : w.ai == 2 ? 90 : 55;
            if (w.time > first && attackT >= every) {
                attackT = 0;
                tryAttack(w);
            }
        }
        build(w);
    }

    // ---------------- Bauen ----------------

    private void build(World w) {
        int[] cnt = new int[World.NB];
        int sites = 0, milSites = 0, milDone = 0;
        for (World.Building b : w.buildings.values()) {
            if (b.player != pl) continue;
            cnt[b.type]++;
            if (World.isMilitary(b.type)) {
                if (b.done) milDone++;
                else milSites++;
            } else if (!b.done) sites++;
        }
        World.Building h = w.hqB(pl);
        int boards = h.stock[World.WARE_BOARD];
        // Land erweitern
        int wantMil = 1 + (int) (w.time / (w.ai == 1 ? 420 : w.ai == 2 ? 300 : 210));
        wantMil = Math.min(wantMil, 16);
        int maxMilSites = w.ai == 1 ? 1 : 2;
        boolean soldiersLeft = w.soldiers(pl) > milSites || milDone + milSites < 3;
        if (soldiersLeft && milSites < maxMilSites && milDone + milSites < wantMil) {
            int type = World.B_GUARD;
            boolean nearEnemy = enemyNear(w);
            if (boards >= 14 && (nearEnemy || cnt[World.B_FORT] == 0 && w.time > 1500)) type = World.B_FORT;
            else if (boards >= 8 && (nearEnemy || (milDone + milSites) % 3 == 2)) type = World.B_TOWER;
            if (place(w, type) || type != World.B_GUARD && place(w, World.B_GUARD)) return;
        }
        int maxSites = w.ai == 1 ? 2 : w.ai == 2 ? 3 : 4;
        if (sites >= maxSites) return;
        // hungrige Minen: mehr Essen
        boolean hungry = false;
        for (World.Building b : w.buildings.values())
            if (b.player == pl && b.done && World.isMine(b.type) && b.status != null && b.status.startsWith("Wartet auf Nahrung")) hungry = true;
        if (hungry && w.time > 900) {
            if (cnt[World.B_FARM] < 6 && place(w, World.B_FARM)) return;
            if (cnt[World.B_FARM] >= 3 && cnt[World.B_MILL] < 2 && place(w, World.B_MILL)) return;
            if (cnt[World.B_FARM] >= 3 && cnt[World.B_BAKERY] < 2 && place(w, World.B_BAKERY)) return;
            if (cnt[World.B_FARM] >= 3 && cnt[World.B_WELL] < 3 && place(w, World.B_WELL)) return;
            if (cnt[World.B_FISH] < 4 && toolOk(w, World.B_FISH, cnt) && place(w, World.B_FISH)) return;
            if (cnt[World.B_HUNT] < 3 && toolOk(w, World.B_HUNT, cnt) && place(w, World.B_HUNT)) return;
        }
        for (int[] it : PLAN) {
            if (cnt[it[0]] >= it[1]) continue;
            if (!toolOk(w, it[0], cnt)) continue;
            if (place(w, it[0])) return;
        }
    }

    /** Nur bauen, wenn es für den Beruf ein Werkzeug gibt (eine Spitzhacke bleibt für die Eisenmine übrig). */
    private boolean toolOk(World w, int type, int[] cnt) {
        int job = World.B_JOB[type];
        if (job < 0 || job == World.J_SOLDIER) return true;
        if (w.profs(pl)[job] > 0) return true;
        int tool = World.J_TOOL[job];
        if (tool < 0) return true;
        int have = w.hqB(pl).stock[tool];
        for (World.Building b : w.buildings.values())
            if (b.player == pl && !b.done && World.B_JOB[b.type] == job) have--;
        if (tool == World.T_PICK && type != World.B_IRON && cnt[World.B_IRON] == 0) return have > 1;
        return have > 0;
    }

    /** Werkzeug nur machen, wenn es knapp ist (sonst bleibt Eisen für Waffen) */
    private void toolPlan(World w) {
        World.Building h = w.hqB(pl);
        int[] pr = w.toolPrio(pl);
        for (int i = 0; i < World.NTOOLS; i++) {
            int have = h.stock[World.FIRST_TOOL + i];
            int want = have == 0 ? 8 : have == 1 ? 3 : 0;
            if (pr[i] != want) w.apply(new int[]{World.C_TOOLPRIO, pl, i, want});
        }
    }

    /** Gebäude ohne Arbeit abreißen (der Arbeiter kommt mit Werkzeug zurück). */
    private void cleanup(World w) {
        for (World.Building b : new ArrayList<>(w.buildings.values())) {
            if (b.player != pl || !b.done) continue;
            String st = b.status == null ? "" : b.status;
            boolean useless = st.startsWith("Keine Steine mehr") || st.startsWith("Mine erschöpft") || st.startsWith("Keine Fische");
            if (useless && r.nextInt(3) == 0) {
                w.apply(new int[]{World.C_REMOVE, pl, 0, b.node});
                return;
            }
        }
    }

    private boolean enemyNear(World w) {
        for (int n = 0; n < World.N; n++) {
            if (w.owner[n] != pl + 1) continue;
            for (int d = 0; d < 6; d++) {
                int m = World.nb(n, d);
                if (m >= 0 && w.owner[m] != 0 && w.owner[m] != pl + 1) return true;
            }
        }
        return false;
    }

    private boolean place(World w, int type) {
        World.Building h = w.hqB(pl);
        int best = -1;
        float bs = -1e9f;
        boolean mine = World.B_SIZE[type] == 4;
        boolean mil = World.isMilitary(type);
        for (int n = 0; n < World.N; n++) {
            if (w.owner[n] != pl + 1 || bad.contains(n * 32 + type)) continue;
            int size;
            if (mine) {
                if (!w.mineOk(n, pl)) continue;
                size = 4;
            } else {
                size = w.buildSize(n, pl);
                if (size < World.B_SIZE[type]) continue;
            }
            float sc = score(w, type, n, h.node);
            if (sc <= -1e8f) continue;
            if (!mine && World.B_SIZE[type] == 1 && size == 2) sc -= 4;
            sc += r.nextFloat() * 0.5f;
            if (sc > bs) {
                bs = sc;
                best = n;
            }
        }
        if (best < 0) return false;
        if (!w.apply(new int[]{World.C_BUILD, pl, type, best})) {
            bad.add(best * 32 + type);
            return false;
        }
        World.Building b = w.buildingAt(best);
        if (b == null) return false;
        if (!connect(w, b.flag)) {
            w.apply(new int[]{World.C_REMOVE, pl, 0, best});
            bad.add(best * 32 + type);
            return false;
        }
        if (mil) b.prio = true;
        return true;
    }

    private float score(World w, int type, int n, int hq) {
        int dh = World.dist(n, hq);
        switch (type) {
            case World.B_WOOD: {
                int trees = 0;
                for (int m : World.around(n, 4)) if (w.obj[m] == World.O_TREE) trees++;
                if (trees < 4) return -1e9f;
                return trees * 2 - dh * 0.5f + front(w, n, 0.6f);
            }
            case World.B_FOREST: {
                int free = 0, nearWood = 0;
                for (int m : World.around(n, 4)) if (w.obj[m] == World.O_NONE && w.terrain[m] == World.T_GRASS) free++;
                for (World.Building b : w.buildings.values())
                    if (b.player == pl && b.type == World.B_WOOD && World.dist(b.node, n) <= 5) nearWood = 12;
                return free + nearWood - dh * 0.5f - front(w, n, 0.6f);
            }
            case World.B_QUARRY: {
                int st = 0;
                for (int m : World.around(n, 5)) if (w.obj[m] == World.O_STONE) st++;
                if (st < 2) return -1e9f;
                return st * 3 - dh * 0.5f;
            }
            case World.B_FISH: {
                int f = 0;
                for (int m : World.around(n, 4)) if (w.terrain[m] == World.T_WATER && w.fish[m] > 0) f++;
                if (f < 3) return -1e9f;
                return f - dh * 0.3f;
            }
            case World.B_HUNT: {
                int trees = 0;
                for (int m : World.around(n, 6)) if (w.obj[m] == World.O_TREE) trees++;
                return trees - dh * 0.4f;
            }
            case World.B_FARM: {
                int free = 0;
                for (int m : World.around(n, 3))
                    if (w.obj[m] == World.O_NONE && w.terrain[m] == World.T_GRASS && w.roadDirs[m] == 0) free++;
                if (free < 8) return -1e9f;
                return free * 2 - dh * 0.4f;
            }
            case World.B_COAL:
            case World.B_IRON:
            case World.B_GOLD:
            case World.B_GRANITE: {
                int res = type - World.B_COAL + 1, amt = 0;
                for (int m : World.around(n, 2)) if (w.res[m] == res) amt += w.resAmt[m];
                if (amt <= 0) return -1e9f;
                return amt - dh * 0.5f;
            }
            case World.B_GUARD:
            case World.B_TOWER:
            case World.B_FORT: {
                int outside = 0, enemy = 0;
                for (int m : World.around(n, 7)) {
                    if (w.owner[m] != pl + 1 && w.terrain[m] != World.T_WATER) outside++;
                    if (w.owner[m] != 0 && w.owner[m] != pl + 1) enemy++;
                }
                if (outside < 6) return -1e9f;
                for (World.Building b : w.buildings.values())
                    if (b.player == pl && World.isMilitary(b.type) && World.dist(b.node, n) < 5) return -1e9f;
                float toward = 0;
                if (w.combat()) {
                    World.Building eh = w.hqB(1 - pl);
                    if (eh != null) toward = -World.dist(n, eh.node) * (w.ai == 1 ? 0.6f : 1.2f);
                }
                return outside + enemy * 2 + (w.ai == 3 ? enemy * 2 : 0) - dh * 0.1f + toward;
            }
            default:
                return -dh;
        }
    }

    /** im Kampf: je näher am Gegner, desto höher */
    private float front(World w, int n, float k) {
        if (!w.combat()) return 0;
        World.Building eh = w.hqB(1 - pl);
        World.Building h = w.hqB(pl);
        if (eh == null || h == null) return 0;
        return (World.dist(h.node, eh.node) - World.dist(n, eh.node)) * k;
    }

    // ---------------- Wege ----------------

    private boolean connect(World w, int flagId) {
        World.Flag f = w.flags.get(flagId);
        World.Building h = w.hqB(pl);
        if (f == null || h == null) return false;
        if (w.connected(f.id, h.flag)) return true;
        ArrayList<World.Flag> fl = new ArrayList<>();
        for (World.Flag g : w.flags.values())
            if (g.player == pl && g.id != f.id && World.dist(g.node, f.node) <= 12 && w.connected(g.id, h.flag)) fl.add(g);
        fl.sort((x, y) -> World.dist(x.node, f.node) - World.dist(y.node, f.node));
        int tries = 0;
        for (World.Flag g : fl) {
            if (tries++ > 8) break;
            if (w.planRoad(f.id, g.node) != null && w.apply(new int[]{World.C_ROAD, pl, f.id, g.node})) return true;
        }
        return false;
    }

    private void connectLoose(World w) {
        World.Building h = w.hqB(pl);
        for (World.Flag f : new ArrayList<>(w.flags.values())) {
            if (f.player != pl || !w.flags.containsKey(f.id)) continue;
            boolean has = false;
            for (int x : f.road) if (x >= 0) has = true;
            if (has || f.id == h.flag) continue;
            connect(w, f.id);
        }
    }

    // ---------------- Angreifen ----------------

    private void tryAttack(World w) {
        World.Building target = null;
        int bestMargin = Integer.MIN_VALUE, send = 0;
        int need = w.ai == 3 ? 1 : w.ai == 2 ? 2 : 3;
        for (World.Building t : w.buildings.values()) {
            if (t.player == pl || !w.canAttack(t, pl)) continue;
            int avail = w.attackersFor(t, pl);
            if (avail < 2) continue;
            int[] rk = w.insideRanks(t);
            int def = 0;
            for (int k = 0; k < 5; k++) def += rk[k] * (1 + k);
            int margin = avail - def;
            if (World.isHq(t)) margin += 1;
            if (margin >= need && margin > bestMargin) {
                bestMargin = margin;
                target = t;
                send = Math.min(avail, def * 2 + 2);
            }
        }
        if (target != null) w.apply(new int[]{World.C_ATTACK, pl, target.id, send});
    }
}
