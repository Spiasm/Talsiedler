package de.spiasm.talsiedler;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Die ganze Spielwelt: Karte, Fahnen, Wege, Gebäude, Waren und Siedler.
 * Reines Java ohne Android, damit es auch am Computer getestet werden kann.
 *
 * Karte: Knotengitter, ungerade Zeilen sind um einen halben Knoten nach rechts versetzt.
 * Jeder Knoten hat 6 Nachbarn: 0=O, 1=SO, 2=SW, 3=W, 4=NW, 5=NO.
 */
public class World implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int W = 72, H = 72, N = W * H;

    public static final byte T_WATER = 0, T_GRASS = 1, T_MOUNTAIN = 2, T_SAND = 3;
    public static final byte O_NONE = 0, O_TREE = 1, O_STONE = 2, O_BUILDING = 3, O_FLAG = 4, O_BLOCK = 5;

    public static final int WARE_LOG = 0, WARE_BOARD = 1, WARE_STONE = 2, NWARES = 3;
    public static final String[] WARE_NAMES = {"Baumstamm", "Brett", "Stein"};

    public static final int B_HQ = 0, B_WOOD = 1, B_FOREST = 2, B_QUARRY = 3, B_SAW = 4, B_GUARD = 5, B_TOWER = 6, NB = 7;
    public static final String[] B_NAMES = {"Hauptquartier", "Holzfäller", "Förster", "Steinbruch", "Sägewerk", "Wachstube", "Wachturm"};
    /** 1 klein, 2 mittel, 3 groß */
    public static final int[] B_SIZE = {3, 1, 1, 1, 2, 1, 2};
    public static final int[][] B_COST = {{0, 0, 0}, {0, 2, 0}, {0, 2, 0}, {0, 2, 0}, {0, 2, 2}, {0, 2, 1}, {0, 4, 3}};
    public static final int[] B_RADIUS = {9, 0, 0, 0, 0, 6, 9};
    public static final int[] B_WORK_RADIUS = {0, 6, 5, 7, 0, 0, 0};

    public static final int J_CARRIER = 0, J_BUILDER = 1, J_WOODCUTTER = 2, J_FORESTER = 3, J_MASON = 4, J_SAWYER = 5, J_SOLDIER = 6;
    public static final String[] J_NAMES = {"Träger", "Bauarbeiter", "Holzfäller", "Förster", "Steinmetz", "Schreiner", "Soldat"};

    // Siedler-Zustände
    static final int S_TO_SITE = 1, S_INSIDE = 2, S_OUT_GO = 3, S_WORK = 4, S_OUT_BACK = 5, S_DELIVER = 6,
            S_BUILD = 7, S_RETURN = 8, S_SAW = 9;
    static final int C_GO = 20, C_IDLE = 21, C_FETCH = 22, C_CARRY = 23, C_DROPWAIT = 24, C_TOMID = 25;

    public static final int FLAG_CAP = 8;

    // Ereignisse für Töne
    public static final int E_CHOP = 1, E_CLINK = 2, E_DIG = 3, E_HAMMER = 4, E_SAW = 5, E_TREEFALL = 6, E_DONE = 7,
            E_LAND = 8, E_DROP = 9, E_PAVE = 10;
    static final float BUILD_PER_MAT = 3f;
    static final float TREE_GROW = 35f;

    // ---------------- Klassen ----------------

    public static class Ware implements Serializable {
        private static final long serialVersionUID = 1L;
        public int type;
        int dest = -1, flag = -1, next = -1, res = -1;
    }

    public static class Flag implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, node;
        public final int[] road = {-1, -1, -1, -1, -1, -1};
        public final ArrayList<Ware> wares = new ArrayList<>();
    }

    public static class Road implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, a, b;
        public int[] path;
        public int carrier = -1;
        /** 0 = Trampelpfad, 1 = gepflastert, 2 = gepflastert mit zweitem Träger */
        public int level;
        public float traffic;
        /** Id des zweiten Trägers, 0 = keiner */
        public int helper;
    }

    public static class Msg implements Serializable {
        private static final long serialVersionUID = 1L;
        public String text;
        public int node;
        public float time;
    }

    public static class Building implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, type, node, flag;
        public boolean done, occupied;
        public final int[] got = new int[NWARES], incoming = new int[NWARES], stock = new int[NWARES];
        public int used;
        public float buildT;
        public int builder = -1, worker = -1;
        public String status = "";
        public int produced;
        public boolean stopped, problem;
        /** Auslastung 0..1 (gleitender Mittelwert) */
        public float util;
        int noticed;

        public int totalCost() {
            int s = 0;
            for (int c : B_COST[type]) s += c;
            return s;
        }

        public float progress() {
            int t = totalCost();
            if (done || t == 0) return 1f;
            return Math.min(1f, (used + buildT / BUILD_PER_MAT) / t);
        }
    }

    public static class Settler implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, job, node, next = -1;
        public float prog;
        int[] path;
        int pi;
        public int state;
        public float timer;
        int home = -1;
        int ridx, target = -1, endIdx;
        public Ware carry;
        public int carryType = -1;
        public boolean visible = true;
        int[] tripBack;
        float stepTime = 1f;
        public float pause, idle, workPhase;
        float tempo, speedF;
        public boolean helper;

        public float tempo() {
            return tempo <= 0 ? 1f : tempo;
        }

        public boolean walking() {
            return next >= 0;
        }

        public boolean working() {
            return state == S_WORK || state == S_BUILD;
        }

        public int carried() {
            if (carry != null) return carry.type;
            return carryType;
        }
    }

    // ---------------- Daten ----------------

    public final byte[] terrain = new byte[N];
    public final byte[] height = new byte[N];
    public final byte[] obj = new byte[N];
    public final byte[] objVal = new byte[N];
    public final int[] objId = new int[N];
    public final byte[] roadDirs = new byte[N];
    public final boolean[] owned = new boolean[N];
    final float[] growT = new float[N];
    final boolean[] reserved = new boolean[N];
    public boolean[] explored = new boolean[N];
    public int exploredVer;
    public ArrayList<Msg> msgs = new ArrayList<>();
    public int msgCount;
    /** fallende Bäume: {Knoten, Startzeit, Richtung} */
    public ArrayList<float[]> falls = new ArrayList<>();
    /** Statistik alle 30 s: {Zeit, Stämme, Bretter, Steine, Land, Leute} */
    public ArrayList<int[]> stats = new ArrayList<>();
    float statT, noPoolT;
    public transient ArrayList<int[]> events;

    public final LinkedHashMap<Integer, Flag> flags = new LinkedHashMap<>();
    public final LinkedHashMap<Integer, Road> roads = new LinkedHashMap<>();
    public final LinkedHashMap<Integer, Building> buildings = new LinkedHashMap<>();
    public final LinkedHashMap<Integer, Settler> settlers = new LinkedHashMap<>();

    int nextId = 1;
    public int hq;
    public int pool = 30;
    public int soldiers = 8;
    public float time;
    float poolT, demandT, requestT, rerouteT, growTick;
    int roadVer;
    final Random rng;
    public final long seed;
    public int producedTotal[] = new int[NWARES];

    // ---------------- Hilfsfunktionen Gitter ----------------

    public static int nb(int n, int d) {
        if (n < 0) return -1;
        int x = n % W, y = n / W;
        boolean odd = (y & 1) == 1;
        int nx, ny;
        switch (d) {
            case 0: nx = x + 1; ny = y; break;
            case 3: nx = x - 1; ny = y; break;
            case 1: ny = y + 1; nx = odd ? x + 1 : x; break;
            case 2: ny = y + 1; nx = odd ? x : x - 1; break;
            case 4: ny = y - 1; nx = odd ? x : x - 1; break;
            default: ny = y - 1; nx = odd ? x + 1 : x; break;
        }
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) return -1;
        return ny * W + nx;
    }

    public static int dirTo(int a, int b) {
        for (int d = 0; d < 6; d++) if (nb(a, d) == b) return d;
        return -1;
    }

    public static int dist(int a, int b) {
        int ax = a % W, ay = a / W, bx = b % W, by = b / W;
        int aq = ax - (ay - (ay & 1)) / 2, bq = bx - (by - (by & 1)) / 2;
        int dq = aq - bq, dr = ay - by;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    /** Alle Knoten im Umkreis r (inklusive Mitte). */
    public static ArrayList<Integer> around(int n, int r) {
        ArrayList<Integer> out = new ArrayList<>();
        int cx = n % W, cy = n / W;
        for (int y = Math.max(0, cy - r); y <= Math.min(H - 1, cy + r); y++)
            for (int x = Math.max(0, cx - r - 1); x <= Math.min(W - 1, cx + r + 1); x++) {
                int m = y * W + x;
                if (dist(n, m) <= r) out.add(m);
            }
        return out;
    }

    int newId() {
        return nextId++;
    }

    // ---------------- Kartenerzeugung ----------------

    public World(long seed) {
        this.seed = seed;
        rng = new Random(seed);
        generate();
    }

    private float[] noiseGrid(int cell, Random r) {
        int gw = W / cell + 3, gh = H / cell + 3;
        float[] g = new float[gw * gh];
        for (int i = 0; i < g.length; i++) g[i] = r.nextFloat();
        float[] out = new float[N];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float fx = (x + ((y & 1) * 0.5f)) / cell, fy = y / (float) cell;
                int ix = (int) fx, iy = (int) fy;
                float tx = fx - ix, ty = fy - iy;
                tx = tx * tx * (3 - 2 * tx);
                ty = ty * ty * (3 - 2 * ty);
                float a = g[iy * gw + ix], b = g[iy * gw + ix + 1], c = g[(iy + 1) * gw + ix], d = g[(iy + 1) * gw + ix + 1];
                out[y * W + x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
            }
        return out;
    }

    private float[] fbm(Random r) {
        float[] a = noiseGrid(14, r), b = noiseGrid(7, r), c = noiseGrid(3, r);
        float[] o = new float[N];
        for (int i = 0; i < N; i++) o[i] = a[i] * 0.6f + b[i] * 0.28f + c[i] * 0.12f;
        return o;
    }

    private void generate() {
        float[] land = fbm(rng), forest = fbm(rng), rocks = fbm(rng), bumps = noiseGrid(4, rng);
        int cx = W / 2, cy = H / 2;
        if ((cy & 1) == 1) cy++;
        int center = cy * W + cx;
        for (int n = 0; n < N; n++) {
            int x = n % W, y = n / W;
            float v = land[n];
            // Rand der Karte wird Wasser
            int edge = Math.min(Math.min(x, W - 1 - x), Math.min(y, H - 1 - y));
            if (edge < 4) v -= (4 - edge) * 0.12f;
            int dc = dist(n, center);
            if (dc < 12) v = v * (dc / 12f) + 0.48f * (1 - dc / 12f);
            if (v < 0.3f) {
                terrain[n] = T_WATER;
                height[n] = 0;
            } else if (v > 0.64f) {
                terrain[n] = T_MOUNTAIN;
                height[n] = (byte) Math.min(9, 3 + (int) ((v - 0.64f) * 40));
            } else {
                terrain[n] = T_GRASS;
                height[n] = (byte) (1 + (int) (bumps[n] * 2.6f));
            }
        }
        // Sand am Ufer
        for (int n = 0; n < N; n++) {
            if (terrain[n] != T_GRASS) continue;
            for (int d = 0; d < 6; d++) {
                int m = nb(n, d);
                if (m >= 0 && terrain[m] == T_WATER) {
                    terrain[n] = T_SAND;
                    height[n] = 1;
                    break;
                }
            }
        }
        // Startgebiet ebnen
        for (int m : around(center, 4)) {
            terrain[m] = T_GRASS;
            height[m] = 2;
        }
        for (int n = 0; n < N; n++) {
            if (terrain[n] == T_GRASS && dist(n, center) > 3) {
                float dens = dist(n, center) < 10 ? 0.35f : 0.75f;
                if (forest[n] > 0.58f && rng.nextFloat() < dens) {
                    obj[n] = O_TREE;
                    objVal[n] = (byte) (rng.nextFloat() < 0.85f ? 3 : rng.nextInt(3));
                } else if (rocks[n] > 0.66f && rng.nextFloat() < 0.5f) {
                    obj[n] = O_STONE;
                    objVal[n] = (byte) (3 + rng.nextInt(4));
                }
            }
        }
        // Rund ums Hauptquartier sicher Bäume und Steine
        int trees = 0, stones = 0;
        for (int m : around(center, 8)) {
            if (obj[m] == O_TREE) trees++;
            if (obj[m] == O_STONE) stones++;
        }
        int tries = 0;
        while ((trees < 14 || stones < 8) && tries++ < 6000) {
            int m = (cy + rng.nextInt(15) - 7) * W + cx + rng.nextInt(15) - 7;
            int d = dist(m, center);
            if (d < 4 || d > 8 || terrain[m] != T_GRASS || obj[m] != O_NONE) continue;
            if (stones < 8 && (d >= 5 && rng.nextBoolean())) {
                obj[m] = O_STONE;
                objVal[m] = (byte) (5 + rng.nextInt(3));
                stones++;
            } else if (trees < 14) {
                obj[m] = O_TREE;
                objVal[m] = 3;
                trees++;
            }
        }
        // Hauptquartier
        Building b = new Building();
        b.id = newId();
        b.type = B_HQ;
        b.node = center;
        b.done = true;
        b.occupied = true;
        buildings.put(b.id, b);
        hq = b.id;
        obj[center] = O_BUILDING;
        objId[center] = b.id;
        for (int d : new int[]{3, 4, 5}) {
            int m = nb(center, d);
            obj[m] = O_BLOCK;
            objId[m] = b.id;
        }
        Flag f = makeFlag(nb(center, 1));
        b.flag = f.id;
        b.stock[WARE_LOG] = 8;
        b.stock[WARE_BOARD] = 30;
        b.stock[WARE_STONE] = 20;
        recomputeTerritory();
    }

    // ---------------- Abfragen für die Bedienung ----------------

    public Building hqB() {
        return buildings.get(hq);
    }

    public Building buildingAt(int n) {
        if (n < 0) return null;
        if (obj[n] == O_BUILDING || obj[n] == O_BLOCK) return buildings.get(objId[n]);
        return null;
    }

    public Flag flagAt(int n) {
        if (n < 0 || obj[n] != O_FLAG) return null;
        return flags.get(objId[n]);
    }

    public Road roadAt(int n) {
        if (n < 0 || roadDirs[n] == 0 || obj[n] == O_FLAG) return null;
        for (Road r : roads.values())
            for (int i = 1; i < r.path.length - 1; i++) if (r.path[i] == n) return r;
        return null;
    }

    boolean passableTerrain(int n) {
        return terrain[n] != T_WATER;
    }

    private boolean neighbourFlag(int n) {
        for (int d = 0; d < 6; d++) {
            int m = nb(n, d);
            if (m >= 0 && obj[m] == O_FLAG) return true;
        }
        return false;
    }

    /** Darf hier eine Fahne hin? (auch mitten auf einem Weg) */
    public boolean canFlag(int n) {
        if (n < 0 || !owned[n] || !passableTerrain(n)) return false;
        if (obj[n] != O_NONE) return false;
        if (neighbourFlag(n)) return false;
        int x = n % W, y = n / W;
        return x > 0 && y > 0 && x < W - 1 && y < H - 1;
    }

    /** Größte Gebäudegröße, die hier gebaut werden kann (0 = nichts). */
    public int buildSize(int n) {
        if (n < 0 || !owned[n] || terrain[n] != T_GRASS || obj[n] != O_NONE || roadDirs[n] != 0) return 0;
        int f = nb(n, 1);
        if (f < 0 || !owned[f]) return 0;
        if (obj[f] == O_FLAG) {
            Flag fl = flagAt(f);
            if (fl.road[4] != -1) return 0;
        } else if (!canFlag(f)) return 0;
        if (Math.abs(height[n] - height[f]) > 1) return 0;
        for (int d = 0; d < 6; d++) {
            int m = nb(n, d);
            if (m < 0) return 0;
            if (d != 1 && obj[m] == O_FLAG) return 0;
            if (obj[m] == O_BUILDING) return 0;
        }
        int size = 1;
        boolean medium = true;
        for (int d : new int[]{3, 4, 5}) {
            int m = nb(n, d);
            if (m < 0 || terrain[m] != T_GRASS || obj[m] != O_NONE || roadDirs[m] != 0 || !owned[m]
                    || Math.abs(height[m] - height[n]) > 1) {
                medium = false;
                break;
            }
            for (int e = 0; e < 6; e++) {
                int k = nb(m, e);
                if (k >= 0 && (obj[k] == O_BUILDING || obj[k] == O_BLOCK)) medium = false;
            }
        }
        if (medium) size = 2;
        return size;
    }

    // ---------------- Bauen ----------------

    Flag makeFlag(int n) {
        Flag f = new Flag();
        f.id = newId();
        f.node = n;
        flags.put(f.id, f);
        obj[n] = O_FLAG;
        objId[n] = f.id;
        return f;
    }

    /** Fahne setzen, teilt einen Weg falls nötig. */
    public Flag placeFlag(int n) {
        if (!canFlag(n)) return null;
        Road r = roadAt(n);
        Flag f = makeFlag(n);
        if (r != null) splitRoad(r, f);
        return f;
    }

    public int placeBuilding(int type, int n) {
        int size = buildSize(n);
        if (size < B_SIZE[type] || type == B_HQ) return -1;
        int fn = nb(n, 1);
        Flag f = flagAt(fn);
        if (f == null) f = placeFlag(fn);
        if (f == null) return -1;
        Building b = new Building();
        b.id = newId();
        b.type = type;
        b.node = n;
        b.flag = f.id;
        buildings.put(b.id, b);
        obj[n] = O_BUILDING;
        objId[n] = b.id;
        if (B_SIZE[type] >= 2) {
            for (int d : new int[]{3, 4, 5}) {
                int m = nb(n, d);
                obj[m] = O_BLOCK;
                objId[m] = b.id;
            }
        }
        b.status = "Wartet auf Bauarbeiter";
        return b.id;
    }

    /** Plant einen Weg von einer Fahne zu einem Ziel-Knoten. Liefert die Knotenliste oder null. */
    public int[] planRoad(int fromFlag, int target) {
        Flag f = flags.get(fromFlag);
        if (f == null || target < 0) return null;
        Building tb = buildingAt(target);
        if (tb != null) target = flags.get(tb.flag).node;
        if (target == f.node) return null;
        boolean ok = obj[target] == O_FLAG || canFlag(target);
        if (!ok) return null;
        int[] prev = new int[N];
        java.util.Arrays.fill(prev, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(f.node);
        prev[f.node] = -1;
        boolean found = false;
        while (!q.isEmpty() && !found) {
            int c = q.poll();
            for (int d = 0; d < 6; d++) {
                int m = nb(c, d);
                if (m < 0 || prev[m] != -2) continue;
                if (c == f.node && f.road[d] != -1) continue;
                if (m == target) {
                    if (obj[m] == O_FLAG) {
                        Flag tf = flagAt(m);
                        if (tf.road[(d + 3) % 6] != -1) continue;
                    }
                    prev[m] = c;
                    found = true;
                    break;
                }
                if (!owned[m] || !passableTerrain(m) || obj[m] != O_NONE || roadDirs[m] != 0) continue;
                if (dist(m, f.node) > 30) continue;
                prev[m] = c;
                q.add(m);
            }
        }
        if (!found) return null;
        ArrayList<Integer> p = new ArrayList<>();
        for (int c = target; c != -1; c = prev[c]) p.add(0, c);
        int[] out = new int[p.size()];
        for (int i = 0; i < out.length; i++) out[i] = p.get(i);
        return out;
    }

    /** Baut einen geplanten Weg, setzt dabei automatisch Fahnen alle 2 Felder. */
    public boolean buildRoad(int[] path) {
        if (path == null || path.length < 2) return false;
        if (obj[path[0]] != O_FLAG) return false;
        int end = path[path.length - 1];
        // Weg darf noch immer frei sein
        for (int i = 1; i < path.length - 1; i++)
            if (obj[path[i]] != O_NONE || roadDirs[path[i]] != 0 || !owned[path[i]]) return false;
        if (obj[end] != O_FLAG && placeFlag(end) == null) return false;
        ArrayList<Integer> cut = new ArrayList<>();
        cut.add(0);
        int last = 0;
        for (int i = 2; i <= path.length - 3; i++) {
            if (i - last >= 2 && canFlag(path[i])) {
                makeFlag(path[i]);
                cut.add(i);
                last = i;
            }
        }
        cut.add(path.length - 1);
        for (int k = 0; k + 1 < cut.size(); k++) {
            int[] sub = java.util.Arrays.copyOfRange(path, cut.get(k), cut.get(k + 1) + 1);
            createRoad(sub);
        }
        return true;
    }

    Road createRoad(int[] path) {
        Road r = new Road();
        r.id = newId();
        r.path = path;
        Flag fa = flagAt(path[0]), fb = flagAt(path[path.length - 1]);
        r.a = fa.id;
        r.b = fb.id;
        fa.road[dirTo(path[0], path[1])] = r.id;
        fb.road[dirTo(path[path.length - 1], path[path.length - 2])] = r.id;
        for (int i = 0; i + 1 < path.length; i++) {
            int d = dirTo(path[i], path[i + 1]);
            roadDirs[path[i]] |= (byte) (1 << d);
            roadDirs[path[i + 1]] |= (byte) (1 << ((d + 3) % 6));
        }
        roads.put(r.id, r);
        roadVer++;
        return r;
    }

    private void detachRoad(Road r) {
        Flag fa = flags.get(r.a), fb = flags.get(r.b);
        if (fa != null) for (int d = 0; d < 6; d++) if (fa.road[d] == r.id) fa.road[d] = -1;
        if (fb != null) for (int d = 0; d < 6; d++) if (fb.road[d] == r.id) fb.road[d] = -1;
        roads.remove(r.id);
        roadVer++;
        lostCarried.clear();
        for (int cid : new int[]{r.carrier, r.helper}) {
            Settler c = settlers.get(cid);
            if (c == null || c.job != J_CARRIER) continue;
            if (c.carry != null) {
                Ware carried = c.carry;
                carried.res = -1;
                carried.next = -1;
                carried.flag = -1;
                lostCarried.add(carried);
            }
            settlers.remove(c.id);
            pool++;
            for (Flag f : new Flag[]{fa, fb}) {
                if (f == null) continue;
                for (Ware w : f.wares) if (w.res == c.id) w.res = -1;
            }
        }
        for (Flag f : new Flag[]{fa, fb}) {
            if (f == null) continue;
            for (Ware w : f.wares) if (w.next == r.id) w.next = -1;
        }
    }

    private transient ArrayList<Ware> lostCarried = new ArrayList<>();

    private void splitRoad(Road r, Flag f) {
        int k = -1;
        for (int i = 1; i < r.path.length - 1; i++) if (r.path[i] == f.node) k = i;
        if (k < 0) return;
        if (lostCarried == null) lostCarried = new ArrayList<>();
        detachRoad(r);
        Road r1 = createRoad(java.util.Arrays.copyOfRange(r.path, 0, k + 1));
        Road r2 = createRoad(java.util.Arrays.copyOfRange(r.path, k, r.path.length));
        for (Road nr : new Road[]{r1, r2}) {
            nr.level = r.level;
            nr.traffic = r.traffic / 2;
        }
        for (Ware w : new ArrayList<>(lostCarried)) {
            w.flag = f.id;
            f.wares.add(w);
            route(w, f);
        }
        lostCarried.clear();
    }

    public void removeRoad(Road r) {
        if (lostCarried == null) lostCarried = new ArrayList<>();
        detachRoad(r);
        for (Ware w : lostCarried) loseWare(w);
        lostCarried.clear();
        for (int i = 0; i + 1 < r.path.length; i++) {
            int d = dirTo(r.path[i], r.path[i + 1]);
            roadDirs[r.path[i]] &= (byte) ~(1 << d);
            roadDirs[r.path[i + 1]] &= (byte) ~(1 << ((d + 3) % 6));
        }
    }

    private void loseWare(Ware w) {
        Building d = buildings.get(w.dest);
        if (d != null && d.id != hq && d.incoming[w.type] > 0) d.incoming[w.type]--;
    }

    public void removeFlag(Flag f) {
        if (f.id == hqB().flag) return;
        for (int d = 0; d < 6; d++) {
            Road r = roads.get(f.road[d]);
            if (r != null) removeRoad(r);
        }
        Building b = buildingAt(nb(f.node, 4));
        if (b != null && b.flag == f.id) removeBuilding(b);
        for (Ware w : f.wares) loseWare(w);
        f.wares.clear();
        flags.remove(f.id);
        obj[f.node] = O_NONE;
        roadVer++;
    }

    public void removeBuilding(Building b) {
        if (b.id == hq) return;
        buildings.remove(b.id);
        obj[b.node] = O_NONE;
        for (int d = 0; d < 6; d++) {
            int m = nb(b.node, d);
            if (m >= 0 && obj[m] == O_BLOCK && objId[m] == b.id) obj[m] = O_NONE;
        }
        for (int id : new int[]{b.builder, b.worker}) {
            Settler s = settlers.get(id);
            if (s != null) dismiss(s);
        }
        if (B_RADIUS[b.type] > 0) recomputeTerritory();
    }

    private void dismiss(Settler s) {
        if (s.target >= 0 && s.job != J_CARRIER) reserved[s.target] = false;
        settlers.remove(s.id);
        if (s.job == J_SOLDIER) soldiers++;
        else pool++;
    }

    /** Abreißen, was auf dem Knoten steht. */
    public boolean removeAt(int n) {
        Building b = buildingAt(n);
        if (b != null) {
            if (b.id == hq) return false;
            removeBuilding(b);
            return true;
        }
        Flag f = flagAt(n);
        if (f != null) {
            if (f.id == hqB().flag) return false;
            removeFlag(f);
            return true;
        }
        Road r = roadAt(n);
        if (r != null) {
            removeRoad(r);
            return true;
        }
        return false;
    }

    public void recomputeTerritory() {
        java.util.Arrays.fill(owned, false);
        if (explored == null) explored = new boolean[N];
        for (Building b : buildings.values()) {
            int r = B_RADIUS[b.type];
            if (r <= 0 || !b.occupied) continue;
            for (int m : around(b.node, r)) owned[m] = true;
            for (int m : around(b.node, r + 3)) explored[m] = true;
        }
        exploredVer++;
    }

    /** Nach dem Laden eines älteren Spielstands fehlende Teile ergänzen. */
    public void fixup() {
        if (msgs == null) msgs = new ArrayList<>();
        if (falls == null) falls = new ArrayList<>();
        if (stats == null) stats = new ArrayList<>();
        if (explored == null) {
            explored = new boolean[N];
            recomputeTerritory();
        }
        if (producedTotal == null) producedTotal = new int[NWARES];
    }

    void ev(int type, int node) {
        if (events == null) events = new ArrayList<>();
        if (events.size() < 64) events.add(new int[]{type, node});
    }

    public void msg(String text, int node) {
        Msg m = new Msg();
        m.text = text;
        m.node = node;
        m.time = time;
        msgs.add(m);
        msgCount++;
        while (msgs.size() > 40) msgs.remove(0);
    }

    /** Wie viele Leute gibt es insgesamt (frei + unterwegs + in Gebäuden). */
    public int people() {
        return pool + settlers.size();
    }

    // ---------------- Wegenetz ----------------

    static class Route {
        final HashMap<Integer, Integer> dist = new HashMap<>();
        final HashMap<Integer, Integer> prev = new HashMap<>();
    }

    private transient HashMap<Integer, Route> cache;
    private transient int cacheVer = -1;

    Route dijkstra(int src) {
        if (cache == null || cacheVer != roadVer) {
            cache = new HashMap<>();
            cacheVer = roadVer;
        }
        Route rt = cache.get(src);
        if (rt != null) return rt;
        rt = new Route();
        PriorityQueue<int[]> pq = new PriorityQueue<>((p, q) -> Integer.compare(p[0], q[0]));
        rt.dist.put(src, 0);
        pq.add(new int[]{0, src});
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            Integer known = rt.dist.get(cur[1]);
            if (known != null && known < cur[0]) continue;
            Flag f = flags.get(cur[1]);
            if (f == null) continue;
            for (int d = 0; d < 6; d++) {
                Road r = roads.get(f.road[d]);
                if (r == null) continue;
                int other = r.a == f.id ? r.b : r.a;
                int nd = cur[0] + (r.path.length - 1) * (r.level >= 1 ? 7 : 10) + (r.carrier < 0 ? 40 : 0);
                Integer od = rt.dist.get(other);
                if (od == null || nd < od) {
                    rt.dist.put(other, nd);
                    rt.prev.put(other, r.id);
                    pq.add(new int[]{nd, other});
                }
            }
        }
        cache.put(src, rt);
        return rt;
    }

    int firstRoad(int src, int dst) {
        Route rt = dijkstra(src);
        if (!rt.dist.containsKey(dst) || src == dst) return -1;
        int cur = dst, road = -1;
        while (cur != src) {
            road = rt.prev.get(cur);
            Road r = roads.get(road);
            cur = r.a == cur ? r.b : r.a;
        }
        return road;
    }

    public boolean connected(int srcFlag, int dstFlag) {
        return dijkstra(srcFlag).dist.containsKey(dstFlag);
    }

    /** Knotenpfad über Wege von Fahne zu Fahne (beide Fahnen-Knoten enthalten). */
    int[] netPath(int src, int dst) {
        Route rt = dijkstra(src);
        if (!rt.dist.containsKey(dst)) return null;
        ArrayList<Integer> roadsRev = new ArrayList<>();
        int cur = dst;
        while (cur != src) {
            int rid = rt.prev.get(cur);
            roadsRev.add(rid);
            Road r = roads.get(rid);
            cur = r.a == cur ? r.b : r.a;
        }
        ArrayList<Integer> nodes = new ArrayList<>();
        nodes.add(flags.get(src).node);
        cur = src;
        for (int i = roadsRev.size() - 1; i >= 0; i--) {
            Road r = roads.get(roadsRev.get(i));
            if (r.a == cur) {
                for (int k = 1; k < r.path.length; k++) nodes.add(r.path[k]);
                cur = r.b;
            } else {
                for (int k = r.path.length - 2; k >= 0; k--) nodes.add(r.path[k]);
                cur = r.a;
            }
        }
        return toArr(nodes);
    }

    static int[] toArr(ArrayList<Integer> l) {
        int[] o = new int[l.size()];
        for (int i = 0; i < o.length; i++) o[i] = l.get(i);
        return o;
    }

    static int[] concat(int[] a, int... b) {
        int[] o = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    /** Freies Laufen (z.B. Holzfäller zum Baum). Pfad ohne Startknoten. */
    int[] walkPath(int from, int to, int maxR) {
        if (from == to) return new int[0];
        int[] prev = new int[N];
        java.util.Arrays.fill(prev, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        prev[from] = -1;
        while (!q.isEmpty()) {
            int c = q.poll();
            for (int d = 0; d < 6; d++) {
                int m = nb(c, d);
                if (m < 0 || prev[m] != -2) continue;
                if (m != to) {
                    if (!passableTerrain(m) || obj[m] == O_BUILDING || obj[m] == O_BLOCK) continue;
                    if (dist(m, from) > maxR) continue;
                }
                prev[m] = c;
                if (m == to) {
                    ArrayList<Integer> p = new ArrayList<>();
                    for (int k = to; k != from; k = prev[k]) p.add(0, k);
                    return toArr(p);
                }
                q.add(m);
            }
        }
        return null;
    }

    // ---------------- Waren ----------------

    int deficit(Building b, int t) {
        if (b.id == hq) return 0;
        if (!b.done) return B_COST[b.type][t] - b.got[t] - b.incoming[t];
        if (b.type == B_SAW && t == WARE_LOG) return b.stopped ? 0 : 4 - b.stock[t] - b.incoming[t];
        return 0;
    }

    int chooseDest(int type, Flag f) {
        Route rt = dijkstra(f.id);
        int best = -1, bd = Integer.MAX_VALUE;
        for (Building b : buildings.values()) {
            if (deficit(b, type) <= 0) continue;
            Integer d = rt.dist.get(b.flag);
            if (d == null) continue;
            if (d < bd) {
                bd = d;
                best = b.id;
            }
        }
        return best >= 0 ? best : hq;
    }

    void addProduct(Flag f, int type) {
        Ware w = new Ware();
        w.type = type;
        w.dest = chooseDest(type, f);
        Building d = buildings.get(w.dest);
        if (d != null && d.id != hq) d.incoming[type]++;
        w.flag = f.id;
        f.wares.add(w);
        producedTotal[type]++;
        route(w, f);
    }

    /** Ware liegt an Fahne f: abliefern oder nächsten Weg wählen. */
    void route(Ware w, Flag f) {
        Building d = buildings.get(w.dest);
        if (d == null) {
            w.dest = hq;
            d = hqB();
        }
        if (d.flag == f.id) {
            f.wares.remove(w);
            deliver(w, d);
            return;
        }
        w.next = firstRoad(f.id, d.flag);
    }

    void deliver(Ware w, Building d) {
        w.flag = -1;
        if (d.id == hq) {
            d.stock[w.type]++;
            return;
        }
        if (d.incoming[w.type] > 0) d.incoming[w.type]--;
        if (!d.done) d.got[w.type]++;
        else d.stock[w.type]++;
    }

    // ---------------- Siedler losschicken ----------------

    Settler spawn(int job, int[] path) {
        Settler s = new Settler();
        s.id = newId();
        s.job = job;
        s.node = hqB().node;
        s.tempo = 0.85f + rng.nextFloat() * 0.3f;
        settlers.put(s.id, s);
        setPath(s, path);
        return s;
    }

    void setPath(Settler s, int[] path) {
        s.path = path;
        s.pi = 0;
        s.prog = 0;
        s.next = (path != null && path.length > 0) ? path[0] : -1;
        if (s.next >= 0) s.stepTime = stepTime(s, s.node, s.next);
    }

    float stepTime(Settler s, int a, int b) {
        float t = stepTime(a, b);
        if (s.speedF > 0) t *= s.speedF;
        if (s.carry != null || s.carryType >= 0) t *= 1.12f;
        return t;
    }

    float stepTime(int a, int b) {
        float t = 0.8f;
        int dh = height[b] - height[a];
        if (dh > 0) t += dh * 0.3f;
        return t;
    }

    int[] pathFromHq(Building b) {
        int[] p = netPath(hqB().flag, b.flag);
        if (p == null) return null;
        return concat(p, b.node);
    }

    int jobFor(int type) {
        switch (type) {
            case B_WOOD: return J_WOODCUTTER;
            case B_FOREST: return J_FORESTER;
            case B_QUARRY: return J_MASON;
            case B_SAW: return J_SAWYER;
            default: return J_SOLDIER;
        }
    }

    private void requests() {
        Building h = hqB();
        for (Building b : buildings.values()) {
            if (b.id == hq) continue;
            if (!b.done && b.builder < 0) {
                if (pool <= 0) { b.status = "Keine Siedler frei"; noPool(); continue; }
                int[] p = pathFromHq(b);
                if (p == null) { b.status = "Kein Weg zum Hauptquartier"; continue; }
                Settler s = spawn(J_BUILDER, p);
                s.state = S_TO_SITE;
                s.home = b.id;
                b.builder = s.id;
                pool--;
                b.status = "Bauarbeiter kommt";
            } else if (b.done && b.worker < 0) {
                int job = jobFor(b.type);
                if (job == J_SOLDIER ? soldiers <= 0 : pool <= 0) {
                    b.status = job == J_SOLDIER ? "Keine Soldaten frei" : "Keine Siedler frei";
                    if (job != J_SOLDIER) noPool();
                    continue;
                }
                int[] p = pathFromHq(b);
                if (p == null) { b.status = "Kein Weg zum Hauptquartier"; continue; }
                Settler s = spawn(job, p);
                s.state = S_TO_SITE;
                s.home = b.id;
                b.worker = s.id;
                if (job == J_SOLDIER) soldiers--; else pool--;
                b.status = J_NAMES[job] + " ist unterwegs";
            }
        }
        for (Road r : roads.values()) {
            boolean needHelper = r.level >= 2 && (r.helper == 0 || !settlers.containsKey(r.helper)) && r.carrier >= 0;
            if ((r.carrier >= 0 && !needHelper) || pool <= 0) continue;
            int mid = r.path.length / 2;
            if (needHelper && mid > 1) mid--;
            int[] p = null;
            int[] pa = netPath(h.flag, r.a);
            if (pa != null) {
                p = pa;
                for (int k = 1; k <= mid; k++) p = concat(p, r.path[k]);
            } else {
                int[] pb = netPath(h.flag, r.b);
                if (pb != null) {
                    p = pb;
                    for (int k = r.path.length - 2; k >= mid; k--) p = concat(p, r.path[k]);
                }
            }
            if (p == null) continue;
            Settler s = spawn(J_CARRIER, p);
            s.state = C_GO;
            s.home = r.id;
            s.ridx = mid;
            if (needHelper) {
                r.helper = s.id;
                s.helper = true;
            } else r.carrier = s.id;
            pool--;
            roadVer++; // Wege mit Träger werden bevorzugt
        }
    }

    private void noPool() {
        if (time - noPoolT > 120f || noPoolT == 0) {
            noPoolT = time;
            msg("Keine freien Siedler mehr – es kommen langsam neue dazu", hqB().node);
        }
    }

    private void demand() {
        Building h = hqB();
        Flag hf = flags.get(h.flag);
        if (hf.wares.size() >= FLAG_CAP) return;
        Route rt = dijkstra(hf.id);
        for (Building b : buildings.values()) {
            if (b.id == hq || !rt.dist.containsKey(b.flag)) continue;
            for (int t = 0; t < NWARES; t++) {
                if (deficit(b, t) > 0 && h.stock[t] > 0) {
                    h.stock[t]--;
                    Ware w = new Ware();
                    w.type = t;
                    w.dest = b.id;
                    b.incoming[t]++;
                    w.flag = hf.id;
                    hf.wares.add(w);
                    route(w, hf);
                    return;
                }
            }
        }
    }

    // ---------------- Simulation ----------------

    public void update(float dt) {
        time += dt;
        poolT += dt;
        if (poolT >= 25f) {
            poolT = 0;
            if (pool < 99) pool++;
        }
        requestT += dt;
        if (requestT >= 1f) {
            requestT = 0;
            requests();
        }
        demandT += dt;
        if (demandT >= 0.5f) {
            demandT = 0;
            demand();
        }
        rerouteT += dt;
        if (rerouteT >= 2f) {
            rerouteT = 0;
            for (Flag f : flags.values())
                for (Ware w : new ArrayList<>(f.wares))
                    if (w.next < 0 || !roads.containsKey(w.next)) {
                        if (w.res < 0) route(w, f);
                    }
        }
        growTick += dt;
        if (growTick >= 1f) {
            for (int n = 0; n < N; n++) {
                if (obj[n] == O_TREE && objVal[n] < 3) {
                    growT[n] += growTick;
                    if (growT[n] >= TREE_GROW) {
                        growT[n] = 0;
                        objVal[n]++;
                    }
                }
            }
            growTick = 0;
        }
        statT += dt;
        if (statT >= 30f || stats.isEmpty()) {
            statT = 0;
            int land = 0;
            for (boolean o : owned) if (o) land++;
            Building h = hqB();
            stats.add(new int[]{(int) time, h.stock[0], h.stock[1], h.stock[2], land, people()});
            if (stats.size() > 480) {
                // ältere Hälfte ausdünnen
                ArrayList<int[]> n = new ArrayList<>();
                for (int i = 0; i < stats.size(); i++) if (i % 2 == 0 || i >= stats.size() / 2) n.add(stats.get(i));
                stats = n;
            }
        }
        for (Building b : buildings.values()) {
            if (!b.done || b.id == hq || b.type >= B_GUARD) continue;
            Settler wk = settlers.get(b.worker);
            if (wk == null || wk.state == S_TO_SITE || b.stopped) continue;
            float target = b.problem ? 0f : 1f;
            b.util += (target - b.util) * dt / 90f;
        }
        for (Road r : roads.values()) {
            r.traffic *= (1f - dt / 300f);
        }
        for (int i = falls.size() - 1; i >= 0; i--) if (time - falls.get(i)[1] > 3f) falls.remove(i);
        for (Settler s : new ArrayList<>(settlers.values())) {
            if (!settlers.containsKey(s.id)) continue;
            if (s.pause > 0) {
                s.pause -= dt;
                continue;
            }
            if (s.walking()) {
                s.prog += dt / s.stepTime;
                if (s.prog >= 1f) {
                    s.node = s.next;
                    s.pi++;
                    s.prog = 0;
                    if (s.pi < s.path.length) {
                        s.next = s.path[s.pi];
                        s.stepTime = stepTime(s, s.node, s.next);
                    } else s.next = -1;
                }
                if (s.walking()) continue;
            }
            think(s, dt);
        }
    }

    private void think(Settler s, float dt) {
        if (s.job == J_CARRIER) {
            Road r = roads.get(s.home);
            if (r == null) {
                settlers.remove(s.id);
                pool++;
                return;
            }
            carrier(s, r);
            return;
        }
        if (s.state == S_RETURN) {
            settlers.remove(s.id);
            if (s.job == J_SOLDIER) soldiers++;
            else pool++;
            return;
        }
        Building b = buildings.get(s.home);
        if (b == null) {
            dismiss(s);
            return;
        }
        Flag bf = flags.get(b.flag);
        switch (s.state) {
            case S_TO_SITE:
                if (s.job == J_BUILDER) {
                    s.state = S_BUILD;
                    b.status = "Wird gebaut";
                } else {
                    s.visible = false;
                    s.state = S_INSIDE;
                    s.timer = 2f;
                    if (s.job == J_SOLDIER) {
                        b.occupied = true;
                        b.status = "Besetzt – das Land wächst";
                        recomputeTerritory();
                        msg(B_NAMES[b.type] + " besetzt – dein Land ist gewachsen", b.node);
                        ev(E_LAND, b.node);
                    } else b.status = "Arbeitet";
                }
                break;
            case S_BUILD: {
                int total = b.totalCost();
                int have = 0;
                for (int g : b.got) have += g;
                if (b.used < have) {
                    b.buildT += dt;
                    b.status = "Wird gebaut";
                    float ph = s.workPhase;
                    s.workPhase += dt * s.tempo();
                    if ((int) (ph / 0.5f) != (int) (s.workPhase / 0.5f)) ev(E_HAMMER, b.node);
                    if (b.buildT >= BUILD_PER_MAT) {
                        b.buildT = 0;
                        b.used++;
                    }
                } else b.status = "Wartet auf Baumaterial";
                if (b.used >= total) {
                    b.done = true;
                    b.builder = -1;
                    b.status = "Fertig";
                    msg(B_NAMES[b.type] + " ist fertig gebaut", b.node);
                    ev(E_DONE, b.node);
                    sendHome(s, b);
                }
                break;
            }
            case S_INSIDE:
                if (s.job == J_SOLDIER) break;
                s.timer -= dt;
                if (s.timer > 0) break;
                if (b.stopped) {
                    b.status = "Angehalten";
                    s.timer = 1f;
                    break;
                }
                if (s.job == J_SAWYER) {
                    if (b.stock[WARE_LOG] > 0) {
                        s.state = S_SAW;
                        s.timer = 6f / s.tempo();
                        s.workPhase = 0;
                        b.status = "Sägt Bretter";
                        b.problem = false;
                        ev(E_SAW, b.node);
                    } else {
                        b.status = "Wartet auf Baumstämme";
                        b.problem = true;
                        s.timer = 1f;
                    }
                    break;
                }
                int tgt = findTarget(s.job, b.node);
                if (tgt < 0) {
                    b.status = s.job == J_WOODCUTTER ? "Keine großen Bäume in der Nähe"
                            : s.job == J_MASON ? "Keine Steine mehr in der Nähe" : "Kein Platz zum Pflanzen";
                    b.problem = true;
                    if ((b.noticed & 1) == 0) {
                        b.noticed |= 1;
                        msg(B_NAMES[b.type] + ": " + b.status, b.node);
                    }
                    s.timer = 4f;
                    break;
                }
                b.problem = false;
                b.noticed &= ~1;
                int[] p = walkPath(bf.node, tgt, B_WORK_RADIUS[b.type] + 3);
                if (p == null) {
                    reserved[tgt] = true; // unerreichbar, nicht wieder probieren
                    s.timer = 1f;
                    break;
                }
                reserved[tgt] = true;
                s.target = tgt;
                s.visible = true;
                s.node = b.node;
                s.tripBack = null;
                setPath(s, concat(new int[]{bf.node}, p));
                s.state = S_OUT_GO;
                b.status = s.job == J_WOODCUTTER ? "Geht zum Baum" : s.job == J_MASON ? "Geht zum Stein" : "Geht pflanzen";
                break;
            case S_OUT_GO:
                s.state = S_WORK;
                s.timer = (s.job == J_FORESTER ? 3.5f : 5f) / s.tempo();
                s.workPhase = 0;
                b.status = s.job == J_WOODCUTTER ? "Fällt einen Baum" : s.job == J_MASON ? "Haut Steine" : "Pflanzt einen Baum";
                break;
            case S_WORK: {
                s.timer -= dt;
                float ph = s.workPhase;
                s.workPhase += dt * s.tempo();
                float iv = s.job == J_WOODCUTTER ? 0.75f : s.job == J_MASON ? 0.85f : 1.1f;
                if (s.timer > 0.3f && (int) (ph / iv) != (int) (s.workPhase / iv))
                    ev(s.job == J_WOODCUTTER ? E_CHOP : s.job == J_MASON ? E_CLINK : E_DIG, s.target);
                if (s.timer > 0) break;
                int t = s.target;
                if (s.job == J_WOODCUTTER) {
                    if (obj[t] == O_TREE && objVal[t] >= 3) {
                        obj[t] = O_NONE;
                        s.carryType = WARE_LOG;
                        falls.add(new float[]{t, time, treeKindOf(t)});
                        ev(E_TREEFALL, t);
                    }
                } else if (s.job == J_MASON) {
                    if (obj[t] == O_STONE) {
                        objVal[t]--;
                        if (objVal[t] <= 0) obj[t] = O_NONE;
                        s.carryType = WARE_STONE;
                    }
                } else {
                    if (obj[t] == O_NONE && roadDirs[t] == 0) {
                        obj[t] = O_TREE;
                        objVal[t] = 0;
                        growT[t] = 0;
                        b.produced++;
                    }
                }
                reserved[t] = false;
                s.target = -1;
                int[] back = walkPath(s.node, bf.node, 20);
                if (back == null) back = new int[]{bf.node};
                setPath(s, concat(back, b.node));
                s.state = S_OUT_BACK;
                b.status = "Kommt zurück";
                break;
            }
            case S_OUT_BACK:
                s.visible = false;
                if (s.carryType >= 0) s.state = S_DELIVER;
                else {
                    s.state = S_INSIDE;
                    s.timer = 5f / s.tempo();
                }
                break;
            case S_SAW: {
                s.timer -= dt;
                float ph = s.workPhase;
                s.workPhase += dt;
                if (s.timer > 0.5f && (int) (ph / 2f) != (int) (s.workPhase / 2f)) ev(E_SAW, b.node);
                if (s.timer > 0) break;
                if (b.stock[WARE_LOG] > 0) {
                    b.stock[WARE_LOG]--;
                    s.carryType = WARE_BOARD;
                    s.state = S_DELIVER;
                } else s.state = S_INSIDE;
                break;
            }
            case S_DELIVER:
                if (bf.wares.size() < FLAG_CAP) {
                    addProduct(bf, s.carryType);
                    b.produced++;
                    s.carryType = -1;
                    s.state = S_INSIDE;
                    s.timer = (s.job == J_SAWYER ? 1f : 5f) / s.tempo();
                    b.status = "Ruht sich aus";
                    b.problem = false;
                    ev(E_DROP, bf.node);
                } else {
                    b.status = "Fahne ist voll – es fehlen Träger oder Wege";
                    b.problem = true;
                }
                break;
        }
    }

    private void sendHome(Settler s, Building from) {
        s.state = S_RETURN;
        s.visible = true;
        s.home = -1;
        int[] p = netPath(from.flag, hqB().flag);
        if (p == null) {
            settlers.remove(s.id);
            if (s.job == J_SOLDIER) soldiers++;
            else pool++;
            return;
        }
        setPath(s, concat(p, hqB().node));
    }

    public int treeKindOf(int n) {
        int h = n * 73856093 ^ (int) seed * 19349663;
        int k = Math.abs(h) % 7;
        return k < 3 ? 0 : k < 6 ? 1 : 2;
    }

    int findTarget(int job, int center) {
        int r = job == J_WOODCUTTER ? B_WORK_RADIUS[B_WOOD] : job == J_MASON ? B_WORK_RADIUS[B_QUARRY] : B_WORK_RADIUS[B_FOREST];
        int best = -1, bd = 999;
        ArrayList<Integer> cand = new ArrayList<>();
        for (int m : around(center, r)) {
            if (reserved[m]) continue;
            if (job == J_WOODCUTTER) {
                if (obj[m] == O_TREE && objVal[m] >= 3) {
                    int d = dist(m, center);
                    if (d < bd) { bd = d; best = m; }
                }
            } else if (job == J_MASON) {
                if (obj[m] == O_STONE) {
                    int d = dist(m, center);
                    if (d < bd) { bd = d; best = m; }
                }
            } else {
                if (terrain[m] != T_GRASS || obj[m] != O_NONE || roadDirs[m] != 0 || dist(m, center) < 2) continue;
                boolean free = true;
                for (int d = 0; d < 6; d++) {
                    int k = nb(m, d);
                    if (k < 0 || obj[k] == O_BUILDING || obj[k] == O_FLAG || obj[k] == O_BLOCK) free = false;
                }
                if (free) cand.add(m);
            }
        }
        if (job == J_FORESTER) return cand.isEmpty() ? -1 : cand.get(rng.nextInt(cand.size()));
        return best;
    }

    // ---------------- Träger ----------------

    private int endIndex(Road r, int e) {
        return e == 0 ? 0 : r.path.length - 1;
    }

    private Flag endFlag(Road r, int e) {
        return flags.get(e == 0 ? r.a : r.b);
    }

    private void walkRoad(Settler s, Road r, int to) {
        int from = s.ridx;
        ArrayList<Integer> p = new ArrayList<>();
        if (to > from) for (int i = from + 1; i <= to; i++) p.add(r.path[i]);
        else for (int i = from - 1; i >= to; i--) p.add(r.path[i]);
        s.node = r.path[from];
        s.speedF = r.level >= 1 ? 0.7f : 1f;
        s.idle = 0;
        setPath(s, toArr(p));
        s.ridx = to;
    }

    private Ware waiting(Flag f, Road r, Settler s) {
        if (f == null) return null;
        for (Ware w : f.wares) if (w.next == r.id && (w.res < 0 || w.res == s.id)) return w;
        return null;
    }

    private void carrier(Settler s, Road r) {
        switch (s.state) {
            case C_GO:
            case C_TOMID:
                s.state = C_IDLE;
                // fall through
            case C_IDLE: {
                s.idle += 0.05f;
                for (int e = 0; e < 2; e++) {
                    Ware w = waiting(endFlag(r, e), r, s);
                    if (w != null) {
                        w.res = s.id;
                        s.target = e;
                        walkRoad(s, r, endIndex(r, e));
                        s.state = C_FETCH;
                        return;
                    }
                }
                break;
            }
            case C_FETCH: {
                Flag f = endFlag(r, s.target);
                Ware w = null;
                for (Ware x : f.wares) if (x.res == s.id) w = x;
                if (w == null || w.next != r.id) {
                    if (w != null) w.res = -1;
                    afterTrip(s, r, s.target);
                    return;
                }
                pickUp(s, r, f, w, s.target);
                break;
            }
            case C_CARRY:
            case C_DROPWAIT: {
                Flag f = endFlag(r, s.target);
                Ware w = s.carry;
                Building d = buildings.get(w.dest);
                boolean deliverHere = d == null ? hqB().flag == f.id : d.flag == f.id;
                if (deliverHere || f.wares.size() < FLAG_CAP) {
                    s.carry = null;
                    w.res = -1;
                    w.flag = f.id;
                    f.wares.add(w);
                    route(w, f);
                    traffic(r);
                    afterTrip(s, r, s.target);
                    s.pause = 0.35f;
                } else {
                    // Fahne voll: tauschen, wenn dort etwas für unseren Weg wartet
                    Ware other = waiting(f, r, s);
                    if (other != null) {
                        s.carry = null;
                        w.res = -1;
                        w.flag = f.id;
                        f.wares.add(w);
                        route(w, f);
                        pickUp(s, r, f, other, s.target);
                    } else s.state = C_DROPWAIT;
                }
                break;
            }
        }
    }

    private void pickUp(Settler s, Road r, Flag f, Ware w, int atEnd) {
        f.wares.remove(w);
        w.flag = -1;
        w.res = s.id;
        s.carry = w;
        int other = 1 - atEnd;
        s.target = other;
        walkRoad(s, r, endIndex(r, other));
        s.state = C_CARRY;
        s.pause = 0.4f;
    }

    private void traffic(Road r) {
        r.traffic += 1f;
        if (r.level == 0 && r.traffic >= 20f) {
            r.level = 1;
            roadVer++;
            msg("Ein viel benutzter Weg wurde gepflastert – dort geht es schneller", r.path[r.path.length / 2]);
            ev(E_PAVE, r.path[r.path.length / 2]);
        } else if (r.level == 1 && r.traffic >= 38f) {
            r.level = 2;
            roadVer++;
            msg("Viel Verkehr – ein zweiter Träger hilft auf diesem Weg", r.path[r.path.length / 2]);
            ev(E_PAVE, r.path[r.path.length / 2]);
        }
    }

    /** Arbeit in einem Gebäude anhalten oder fortsetzen. */
    public void setStopped(Building b, boolean stop) {
        b.stopped = stop;
        b.status = stop ? "Angehalten" : "Arbeitet wieder";
    }

    private void afterTrip(Settler s, Road r, int atEnd) {
        Flag here = endFlag(r, atEnd);
        Ware w = waiting(here, r, s);
        if (w != null) {
            w.res = s.id;
            pickUp(s, r, here, w, atEnd);
            return;
        }
        int other = 1 - atEnd;
        w = waiting(endFlag(r, other), r, s);
        if (w != null) {
            w.res = s.id;
            s.target = other;
            walkRoad(s, r, endIndex(r, other));
            s.state = C_FETCH;
            return;
        }
        walkRoad(s, r, r.path.length / 2);
        s.state = C_TOMID;
    }

    // ---------------- für die Anzeige ----------------

    public int wareCount(int t) {
        return hqB().stock[t];
    }

    public int countBuildings(int type, boolean doneOnly) {
        int c = 0;
        for (Building b : buildings.values()) if (b.type == type && (!doneOnly || b.done)) c++;
        return c;
    }
}
