package de.spiasm.talsiedler;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Random;

/** Zeichnet die Karte mit allem, was darauf steht und läuft. */
public class Renderer {
    public static final float TW = 32f, RH = 16f, HS = 4f, MX = 32f, MY = 56f;

    public float camX, camY, zoom = 1.6f;
    public int viewW, viewH;
    public boolean showSpots, showUtil;
    /** Spieler, der hier zuschaut (0 = blau, 1 = rot) */
    public int me;
    private int fogMe = -1;
    /** Farbe für Fahnen/Wimpel beim nächsten Zeichnen */
    private int pc = 0xFF1E5BC6;
    public int selected = -1;
    public int roadFrom = -1;

    private Bitmap terrainBmp, fogBmp, miniBmp;
    private World terrainOf, fogOf, miniOf;
    private int fogVer = -1;
    private float miniT = -99;
    private int[] miniPx;
    private final int[] chopping = new int[64];
    private int nChop;
    static final int FOG = 8;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    @SuppressWarnings("unchecked")
    private ArrayList<World.Settler>[] rows = new ArrayList[World.H + 2];

    public Renderer() {
        for (int i = 0; i < rows.length; i++) rows[i] = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private void ensureRows() {
        if (rows.length >= World.H + 2) return;
        rows = new ArrayList[World.H + 2];
        for (int i = 0; i < rows.length; i++) rows[i] = new ArrayList<>();
    }

    // ---------- Koordinaten ----------

    public static float nx(int n) {
        int x = n % World.W, y = n / World.W;
        return MX + x * TW + ((y & 1) != 0 ? TW / 2 : 0);
    }

    public static float ny(World w, int n) {
        return MY + (n / World.W) * RH - w.height[n] * HS;
    }

    public static float worldW() {
        return MX * 2 + World.W * TW + TW / 2;
    }

    public static float worldH() {
        return MY + World.H * RH + 24;
    }

    public void centerOn(World w, int n) {
        camX = nx(n) - viewW / 2f / zoom;
        camY = ny(w, n) - viewH / 2f / zoom;
        clampCam();
    }

    public void clampCam() {
        float vw = viewW / zoom, vh = viewH / zoom;
        float maxX = worldW() - vw, maxY = worldH() - vh;
        camX = Math.max(-vw * 0.3f, Math.min(maxX + vw * 0.3f, camX));
        camY = Math.max(-vh * 0.3f, Math.min(maxY + vh * 0.3f, camY));
    }

    public int nodeAt(World w, float sx, float sy) {
        float wx = sx / zoom + camX, wy = sy / zoom + camY;
        int row = (int) ((wy - MY) / RH);
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int y = row - 2; y <= row + 6; y++) {
            if (y < 0 || y >= World.H) continue;
            int x0 = (int) ((wx - MX) / TW);
            for (int x = x0 - 1; x <= x0 + 1; x++) {
                if (x < 0 || x >= World.W) continue;
                int n = y * World.W + x;
                float dx = nx(n) - wx, dy = ny(w, n) - wy;
                float d = dx * dx + dy * dy * 1.6f;
                if (d < bd) {
                    bd = d;
                    best = n;
                }
            }
        }
        return bd < TW * TW ? best : -1;
    }

    // ---------- Gelände vorberechnen ----------

    private static int shade(int c, float f) {
        int r = Math.min(255, Math.max(0, (int) (((c >> 16) & 255) * f)));
        int g = Math.min(255, Math.max(0, (int) (((c >> 8) & 255) * f)));
        int b = Math.min(255, Math.max(0, (int) ((c & 255) * f)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** feste Zufallszahl 0..1 je Knoten (gleich in jeder Kachel) */
    private static float h01(int n, int k) {
        int h = n * 0x27D4EB2D + k * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private int baseColor(World w, int t, int h, int key) {
        float v = 0.93f + h01(key, 1) * 0.12f;
        switch (t) {
            case World.T_WATER: return shade(0xFF2F6DA8, 0.95f + h01(key, 2) * 0.08f);
            case World.T_MOUNTAIN:
                if (h >= 8) return shade(0xFFE8ECEF, v);
                if (w.mapType == World.MT_ETNA) return shade(mix(0xFF4A3E38, 0xFF6A5A50, h01(key, 3)), v);
                if (w.mapType == World.MT_DOLOMITES) return shade(mix(0xFFB0A89A, 0xFFC8BFB0, h01(key, 3)), v);
                return shade(mix(0xFF8C7A62, 0xFF9E9488, h01(key, 3)), v);
            case World.T_SAND:
                if (w.mapType == World.MT_SAHARA) return shade(mix(0xFFE3C27A, 0xFFEFD49A, h01(key, 3)), v);
                return shade(0xFFD9C48A, v);
            case World.T_LAVA: return shade(mix(0xFFC2361A, 0xFFE8741E, h01(key, 3)), 0.9f + h01(key, 4) * 0.2f);
            default:
                if (w.mapType == World.MT_SAHARA) return shade(mix(0xFF7DA845, 0xFF96B850, h01(key, 3)), v);
                if (w.mapType == World.MT_ETNA) return shade(mix(0xFF6A9A40, 0xFF7E9A48, h01(key, 3)), v);
                return shade(mix(0xFF6FA845, 0xFF83B34E, h01(key, 3)), v);
        }
    }

    private int triTerrain(World w, int a, int b, int c) {
        int ta = w.terrain[a], tb = w.terrain[b], tc = w.terrain[c];
        if (ta == tb || ta == tc) return ta;
        if (tb == tc) return tb;
        return ta;
    }

    // ---------- Gelände in Kacheln ----------

    static final int TILE = 512;
    private final java.util.LinkedHashMap<Long, Bitmap> tiles = new java.util.LinkedHashMap<>(64, 0.75f, true);
    private int tileCap = 48, tilesBuilt;

    /** Gelände der neuen Welt: alte Kacheln wegwerfen */
    public void buildTerrain(World w) {
        for (Bitmap b : tiles.values()) b.recycle();
        tiles.clear();
        terrainOf = w;
    }

    private Bitmap tile(World w, int tx, int ty) {
        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
        Bitmap b = tiles.get(key);
        if (b != null) return b;
        if (tilesBuilt >= 6) return null;
        tilesBuilt++;
        b = Bitmap.createBitmap(TILE + 2, TILE + 2, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        c.translate(-tx * TILE + 1, -ty * TILE + 1);
        float x0 = tx * TILE - 1, y0 = ty * TILE - 1, x1 = x0 + TILE + 2, y1 = y0 + TILE + 2;
        int ry0 = Math.max(0, (int) ((y0 - MY) / RH) - 2), ry1 = Math.min(World.H - 2, (int) ((y1 - MY + 10 * HS) / RH) + 2);
        int cx0 = Math.max(0, (int) ((x0 - MX) / TW) - 2), cx1 = Math.min(World.W - 1, (int) ((x1 - MX) / TW) + 2);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setStrokeWidth(1.2f);
        for (int y = ry0; y <= ry1; y++) {
            for (int x = cx0; x <= cx1; x++) {
                int n = y * World.W + x;
                int e = World.nb(n, 0), se = World.nb(n, 1), sw = World.nb(n, 2);
                if (se >= 0 && sw >= 0) tri(c, tp, w, n, se, sw, n * 2);
                if (e >= 0 && se >= 0) tri(c, tp, w, n, e, se, n * 2 + 1);
            }
        }
        // Gras-Büschel, Steinchen, Wellen, Dünen, Lava-Krusten
        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = Math.max(0, ry0 - 1); y <= Math.min(World.H - 1, ry1 + 1); y++) {
            for (int x = cx0; x <= cx1; x++) {
                int n = y * World.W + x;
                int t = w.terrain[n];
                for (int k = 0; k < 3; k++) {
                    float px = nx(n) + (h01(n, 10 + k) - 0.5f) * TW, py = ny(w, n) + (h01(n, 20 + k) - 0.5f) * RH;
                    boolean flip = h01(n, 30 + k) < 0.5f;
                    float sz = h01(n, 40 + k);
                    if (t == World.T_GRASS) {
                        dp.setColor(flip ? 0x553B6E24 : 0x44A9D46A);
                        c.drawCircle(px, py, 1.2f + sz * 1.3f, dp);
                    } else if (t == World.T_MOUNTAIN) {
                        dp.setColor(flip ? 0x55584A3A : 0x40FFFFFF);
                        c.drawCircle(px, py, 0.8f + sz * 1.6f, dp);
                    } else if (t == World.T_WATER) {
                        dp.setColor(0x305FA3DE);
                        c.drawRect(px - 4, py, px + 4, py + 1, dp);
                    } else if (t == World.T_LAVA) {
                        dp.setColor(flip ? 0x80301008 : 0x90FFC040);
                        if (flip) c.drawRect(px - 3 - sz * 2, py - 0.6f, px + 3 + sz * 2, py + 0.6f, dp);
                        else c.drawCircle(px, py, 0.8f + sz, dp);
                    } else {
                        if (w.mapType == World.MT_SAHARA && k == 0) {
                            // Düne
                            dp.setColor(0x30A07838);
                            rf.set(px - 9, py - 1.6f, px + 9, py + 1.6f);
                            c.drawOval(rf, dp);
                            dp.setColor(0x40FFF0C8);
                            rf.set(px - 7, py - 2.4f, px + 5, py - 0.6f);
                            c.drawOval(rf, dp);
                        } else {
                            dp.setColor(0x40A08850);
                            c.drawCircle(px, py, 1f, dp);
                        }
                    }
                }
            }
        }
        tiles.put(key, b);
        while (tiles.size() > tileCap) {
            java.util.Iterator<java.util.Map.Entry<Long, Bitmap>> it = tiles.entrySet().iterator();
            Bitmap old = it.next().getValue();
            it.remove();
            old.recycle();
        }
        return b;
    }

    private void drawTerrain(Canvas c, World w) {
        int ntx = (int) Math.ceil(worldW() / TILE), nty = (int) Math.ceil(worldH() / TILE);
        int tx0 = Math.max(0, (int) Math.floor(camX / TILE)), tx1 = Math.min(ntx - 1, (int) Math.floor((camX + viewW / zoom) / TILE));
        int ty0 = Math.max(0, (int) Math.floor(camY / TILE)), ty1 = Math.min(nty - 1, (int) Math.floor((camY + viewH / zoom) / TILE));
        tileCap = Math.max(24, (tx1 - tx0 + 2) * (ty1 - ty0 + 2) + 10);
        tilesBuilt = 0;
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++) {
                Bitmap tb = tile(w, tx, ty);
                if (tb == null) continue;
                c.save();
                c.translate(tx * TILE - 1, ty * TILE - 1);
                c.drawBitmap(tb, 0, 0, bmpPaint);
                c.restore();
            }
    }

    private void tri(Canvas c, Paint tp, World w, int a, int b, int d, int key) {
        int t = triTerrain(w, a, b, d);
        int ha = w.height[a], hb = w.height[b], hd = w.height[d];
        int col = baseColor(w, t, Math.max(ha, Math.max(hb, hd)), key);
        // Licht kommt von links oben
        float ax = nx(a), ay = ny(w, a), bx = nx(b), by = ny(w, b), dx = nx(d), dy = ny(w, d);
        float slopeX = ((hb + hd) / 2f - ha) * (bx > ax ? 1 : -1);
        float slopeY = (a / World.W == b / World.W) ? (hd - (ha + hb) / 2f) : ((hb + hd) / 2f - ha);
        float f = 1f + slopeX * 0.07f - slopeY * 0.09f;
        if (t == World.T_WATER || t == World.T_LAVA) f = 1f;
        f = Math.max(0.55f, Math.min(1.45f, f));
        col = shade(col, f);
        path.reset();
        path.moveTo(ax, ay);
        path.lineTo(bx, by);
        path.lineTo(dx, dy);
        path.close();
        tp.setColor(col);
        tp.setStyle(Paint.Style.FILL);
        c.drawPath(path, tp);
        tp.setStyle(Paint.Style.STROKE);
        c.drawPath(path, tp);
        tp.setStyle(Paint.Style.FILL);
    }

    // ---------- Hauptzeichnen ----------

    public void draw(Canvas c, World w, float t) {
        if (terrainOf != w) buildTerrain(w);
        ensureRows();
        if (fogOf != w || fogVer != w.exploredVer || fogMe != me) buildFog(w);
        boolean[] ex = w.exploredOf(me);
        c.drawColor(0xFF000000);
        c.save();
        c.scale(zoom, zoom);
        c.translate(-camX, -camY);
        drawTerrain(c, w);

        int y0 = Math.max(0, (int) ((camY - MY) / RH) - 2);
        int y1 = Math.min(World.H - 1, (int) ((camY + viewH / zoom - MY) / RH) + 4);
        int x0 = Math.max(0, (int) ((camX - MX) / TW) - 2);
        int x1 = Math.min(World.W - 1, (int) ((camX + viewW / zoom - MX) / TW) + 2);

        // Grenze
        p.setStyle(Paint.Style.FILL);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                int n = y * World.W + x;
                int ow = w.owner[n];
                if (ow == 0 || !ex[n]) continue;
                boolean edge = false;
                for (int d = 0; d < 6 && !edge; d++) {
                    int m = World.nb(n, d);
                    if (m < 0 || w.owner[m] != ow) edge = true;
                }
                if (edge) borderStone(c, nx(n), ny(w, n), ow - 1);
            }

        // Wege
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (World.Road r : w.roads.values()) if (ex[r.path[0]] || ex[r.path[r.path.length - 1]]) drawRoad(c, w, r.path, r.carrier < 0, r.level);
        for (World.Building b : w.buildings.values()) {
            World.Flag f = w.flags.get(b.flag);
            if (f != null) drawRoad(c, w, new int[]{b.node, f.node}, false, 0);
        }
        p.setStyle(Paint.Style.FILL);

        // Auswahl
        if (selected >= 0) ring(c, nx(selected), ny(w, selected), 0xFFFFF176, t);
        if (roadFrom >= 0) {
            World.Flag f = w.flags.get(roadFrom);
            if (f != null) ring(c, nx(f.node), ny(w, f.node), 0xFF4FC3F7, t);
        }

        // Siedler nach Zeilen sortieren
        for (ArrayList<World.Settler> l : rows) l.clear();
        nChop = 0;
        for (World.Settler s : w.settlers.values()) {
            if (s.job == World.J_WOODCUTTER && s.state == World.S_WORK && nChop < chopping.length) chopping[nChop++] = s.target;
            if (!s.visible) continue;
            int row = s.node / World.W;
            if (s.next >= 0) row = Math.max(row, s.next / World.W);
            if (row >= y0 - 1 && row <= y1 + 1) rows[row].add(s);
        }

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int n = y * World.W + x;
                if (!ex[n]) continue;
                float px = nx(n), py = ny(w, n);
                if (w.terrain[n] == World.T_LAVA) lavaGlow(c, px, py, t, n);
                switch (w.obj[n]) {
                    case World.O_TREE: {
                        float shake = 0;
                        for (int i = 0; i < nChop; i++) if (chopping[i] == n) shake = (float) Math.sin(t * 40) * 0.8f;
                        tree(c, px + shake, py, treeKind(w, n), w.objVal[n], t, n);
                        break;
                    }
                    case World.O_STONE: stones(c, px, py, w.objVal[n], n); break;
                    case World.O_FIELD: field(c, px, py, w.objVal[n], n, t); break;
                    case World.O_FLAG: flag(c, w, w.flagAt(n), px, py, t); break;
                    case World.O_BUILDING: building(c, w, w.buildings.get(w.objId[n]), px, py, t); break;
                }
                if (showSpots && w.own(n, me) && w.obj[n] == World.O_NONE) spot(c, w, n, px, py);
                if (w.sign != null && w.sign[n] != 0 && (w.obj[n] == World.O_NONE || w.obj[n] == World.O_FIELD)) geoSign(c, px, py, w.sign[n]);
            }
            for (float[] fl : w.falls) if ((int) fl[0] / World.W == y) falling(c, w, fl);
            if (w.ruins != null)
                for (float[] ru : w.ruins) {
                    int rn = (int) ru[0];
                    if (rn / World.W == y && ex[rn] && w.obj[rn] != World.O_BUILDING) ruin(c, w, ru, t);
                }
            if (w.animals != null)
                for (World.Animal a : w.animals) {
                    int ar = Math.max(a.node / World.W, a.next >= 0 ? a.next / World.W : 0);
                    if (ar == y) animal(c, w, a, t);
                }
            if (w.boats != null)
                for (World.Boat bt : w.boats)
                    if (bt.user == 0 && bt.node / World.W == y && ex[bt.node]) boat(c, nx(bt.node), ny(w, bt.node), 1, t, bt.id, false, false, 0);
            for (World.Settler s : rows[y]) settler(c, w, s, t);
        }
        if (showUtil) {
            for (World.Building b : w.buildings.values()) {
                int by = b.node / World.W;
                if (by < y0 || by > y1 || !b.done || !World.hasUtil(b.type) || b.player != me) continue;
                utilBadge(c, nx(b.node), ny(w, b.node), b);
            }
        }
        // Nebel
        c.save();
        c.scale(FOG, FOG);
        c.drawBitmap(fogBmp, 0, 0, bmpPaint);
        c.restore();
        c.restore();
    }

    // ---------- Nebel ----------

    private void buildFog(World w) {
        int fw = (int) (worldW() / FOG) + 2, fh = (int) (worldH() / FOG) + 2;
        fogBmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(fogBmp);
        Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        fp.setColor(0xFF000000);
        float r = TW * 0.85f / FOG;
        boolean[] ex = w.exploredOf(me);
        for (int n = 0; n < World.N; n++) {
            if (ex[n]) continue;
            float x = nx(n) / FOG, y = ny(w, n) / FOG;
            c.drawCircle(x, y, r, fp);
            // Ränder der Karte ganz abdecken
            int gx = n % World.W, gy = n / World.W;
            if (gx == 0) c.drawRect(0, y - r, x, y + r, fp);
            if (gx == World.W - 1) c.drawRect(x, y - r, fw, y + r, fp);
            if (gy == 0) c.drawRect(x - r, 0, x + r, y, fp);
            if (gy == World.H - 1) c.drawRect(x - r, y, x + r, fh, fp);
        }
        // halbdunkler Saum außerhalb des eigenen Landes
        fp.setColor(0x38000000);
        for (int n = 0; n < World.N; n++) {
            if (!ex[n] || w.owner[n] != 0) continue;
            c.drawCircle(nx(n) / FOG, ny(w, n) / FOG, r, fp);
        }
        fogOf = w;
        fogVer = w.exploredVer;
        fogMe = me;
    }

    // ---------- Übersichtskarte ----------

    /** Kleine Karte: 2 Pixel pro Knoten in der Breite. Wird höchstens alle 2 s neu gemalt. */
    public Bitmap minimap(World w, float t) {
        int mw = World.W * 2 + 1, mh = World.H;
        if (miniBmp == null) {
            miniBmp = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
            miniPx = new int[mw * mh];
        }
        if (miniOf == w && t - miniT < 2f && t >= miniT) return miniBmp;
        miniOf = w;
        miniT = t;
        java.util.Arrays.fill(miniPx, 0xFF000000);
        boolean[] ex = w.exploredOf(me);
        for (int n = 0; n < World.N; n++) {
            if (!ex[n]) continue;
            int x = n % World.W, y = n / World.W;
            int col;
            switch (w.terrain[n]) {
                case World.T_WATER: col = 0xFF2F6DA8; break;
                case World.T_MOUNTAIN: col = w.height[n] >= 8 ? 0xFFE0E4E8 : 0xFF8C7A62; break;
                case World.T_SAND: col = 0xFFD9C48A; break;
                case World.T_LAVA: col = 0xFFD8501E; break;
                default: col = 0xFF6FA845;
            }
            if (w.obj[n] == World.O_TREE) col = 0xFF2F5E25;
            else if (w.obj[n] == World.O_FIELD) col = w.objVal[n] >= 3 ? 0xFFE0C050 : 0xFF9CBF4A;
            else if (w.obj[n] == World.O_STONE) col = 0xFFA5A19A;
            if (w.owner[n] == 1) col = mix(col, 0xFF3D7BFF, 0.25f);
            else if (w.owner[n] == 2) col = mix(col, 0xFFFF4040, 0.25f);
            if (w.roadDirs[n] != 0) col = 0xFFE8CC90;
            if (w.obj[n] == World.O_BUILDING || w.obj[n] == World.O_BLOCK) col = 0xFFFFFFFF;
            int px = x * 2 + (y & 1);
            miniPx[y * mw + px] = col;
            miniPx[y * mw + px + 1] = col;
        }
        miniBmp.setPixels(miniPx, 0, mw, 0, 0, mw, mh);
        return miniBmp;
    }

    private float[] miniB;
    private int miniBVer = -1;
    private World miniBOf;

    /** Ausschnitt der kleinen Karte (in Pixeln der Minikarte): nur das erkundete Gebiet, Seitenverhältnis aspect. */
    public float[] miniBounds(World w, float aspect) {
        if (miniB != null && miniBOf == w && miniBVer == w.exploredVer + me * 100000) return miniB;
        int x0 = World.W, x1 = 0, y0 = World.H, y1 = 0;
        boolean[] ex = w.exploredOf(me);
        for (int n = 0; n < World.N; n++) {
            if (!ex[n]) continue;
            int x = n % World.W, y = n / World.W;
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            y0 = Math.min(y0, y);
            y1 = Math.max(y1, y);
        }
        float bx0 = (x0 - 3) * 2f, bx1 = (x1 + 4) * 2f, by0 = y0 - 3, by1 = y1 + 4;
        float bw = bx1 - bx0, bh = by1 - by0;
        if (bw / bh < aspect) {
            float nw = bh * aspect;
            bx0 -= (nw - bw) / 2;
            bx1 = bx0 + nw;
        } else {
            float nh = bw / aspect;
            by0 -= (nh - bh) / 2;
            by1 = by0 + nh;
        }
        miniB = new float[]{bx0, by0, bx1, by1};
        miniBOf = w;
        miniBVer = w.exploredVer + me * 100000;
        return miniB;
    }

    /** Ansicht in Minikarten-Pixeln: Mitte x, Mitte y, halbe Breite, halbe Höhe */
    public float[] viewMini() {
        float cx = camX + viewW / 2f / zoom, cy = camY + viewH / 2f / zoom;
        return new float[]{(cx - MX) / TW * 2f, (cy - MY) / RH, viewW / zoom / TW, viewH / zoom / RH / 2f};
    }

    public void centerOnMini(float px, float py) {
        float wx = MX + px / 2f * TW, wy = MY + py * RH;
        camX = wx - viewW / 2f / zoom;
        camY = wy - viewH / 2f / zoom;
        clampCam();
    }

    /** Welt-Mittelpunkt der Ansicht in Kartenbruchteilen 0..1 */
    public float[] viewFrac() {
        float cx = camX + viewW / 2f / zoom, cy = camY + viewH / 2f / zoom;
        float vw = viewW / zoom, vh = viewH / zoom;
        return new float[]{(cx - MX) / (World.W * TW), (cy - MY) / (World.H * RH), vw / (World.W * TW), vh / (World.H * RH)};
    }

    public void centerOnFrac(World w, float fx, float fy) {
        float wx = MX + fx * World.W * TW, wy = MY + fy * World.H * RH;
        camX = wx - viewW / 2f / zoom;
        camY = wy - viewH / 2f / zoom;
        clampCam();
    }

    // ---------- Auslastung ----------

    private void utilBadge(Canvas c, float x, float y, World.Building b) {
        int pct = Math.round(b.util * 100);
        int col = b.stopped ? 0xFF757575 : pct >= 70 ? 0xFF2E7D32 : pct >= 35 ? 0xFFF9A825 : 0xFFC62828;
        float bx = x, by = y - (World.B_SIZE[b.type] >= 2 ? 40 : 32);
        rf.set(bx - 11, by - 6, bx + 11, by + 5);
        p.setColor(0x88000000);
        c.drawRoundRect(rf, 3, 3, p);
        rf.set(bx - 10, by - 5, bx + 10, by + 4);
        p.setColor(col);
        c.drawRoundRect(rf, 3, 3, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(7.5f);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(b.stopped ? "Stop" : pct + "%", bx, by + 2.3f, p);
        p.setFakeBoldText(false);
    }

    private static int treeKind(World w, int n) {
        if (w.mapType == World.MT_SAHARA) return 3;
        int h = n * 73856093 ^ (int) w.seed * 19349663;
        int k = Math.abs(h) % 7;
        return k < 3 ? 0 : k < 6 ? 1 : 2;
    }

    private void drawRoad(Canvas c, World w, int[] pth, boolean noCarrier, int level) {
        path.reset();
        path.moveTo(nx(pth[0]), ny(w, pth[0]));
        for (int i = 1; i < pth.length; i++) path.lineTo(nx(pth[i]), ny(w, pth[i]));
        p.setStyle(Paint.Style.STROKE);
        if (level >= 1) {
            p.setColor(0xFF55524C);
            p.setStrokeWidth(6.5f);
            c.drawPath(path, p);
            p.setColor(0xFF9D978C);
            p.setStrokeWidth(4.6f);
            c.drawPath(path, p);
            // Pflastersteine
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i + 1 < pth.length; i++) {
                float ax = nx(pth[i]), ay = ny(w, pth[i]), bx = nx(pth[i + 1]), by = ny(w, pth[i + 1]);
                for (int k = 0; k < 5; k++) {
                    float f = (k + 0.5f) / 5f;
                    float sx = ax + (bx - ax) * f, sy = ay + (by - ay) * f;
                    float o = ((k + i) % 2 == 0) ? -1f : 1f;
                    p.setColor((k + i) % 3 == 0 ? 0xFFB8B2A6 : 0xFF86807A);
                    c.drawCircle(sx + o * 0.6f, sy - o * 0.5f, 1.1f, p);
                }
            }
            return;
        }
        p.setColor(0xFF6E5230);
        p.setStrokeWidth(5f);
        c.drawPath(path, p);
        p.setColor(noCarrier ? 0xFFB9A47E : 0xFFD2B47C);
        p.setStrokeWidth(3f);
        c.drawPath(path, p);
    }

    private void ring(Canvas c, float x, float y, int col, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setColor(col);
        float s = 1f + 0.12f * (float) Math.sin(t * 5);
        rf.set(x - 13 * s, y - 6 * s, x + 13 * s, y + 6 * s);
        c.drawOval(rf, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void borderStone(Canvas c, float x, float y, int pl) {
        p.setColor(0x66000000);
        c.drawCircle(x + 1, y + 1, 2.2f, p);
        p.setColor(pl == 0 ? 0xFF2F64D0 : 0xFFD03030);
        c.drawCircle(x, y - 1, 2.2f, p);
        p.setColor(0xFFBFD4FF);
        c.drawCircle(x - 0.6f, y - 1.8f, 0.8f, p);
    }

    private void spot(Canvas c, World w, int n, float x, float y) {
        if (w.terrain[n] == World.T_MOUNTAIN) {
            if (w.mineOk(n, me)) {
                p.setColor(0x88000000);
                rf.set(x - 4, y - 1.2f, x + 4, y + 1.2f);
                c.drawOval(rf, p);
                p.setColor(0xFF424242);
                rf.set(x - 3.5f, y - 7, x + 3.5f, y);
                c.drawRoundRect(rf, 3, 3, p);
                p.setColor(0xFFFFEB3B);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                c.drawLine(x - 2.5f, y - 2, x + 2.5f, y - 6, p);
                c.drawLine(x - 3.5f, y - 5, x - 1, y - 7, p);
                p.setStyle(Paint.Style.FILL);
                return;
            }
        }
        int s = w.buildSize(n, me);
        if (s >= 2) {
            miniHouse(c, x, y, 1.15f, 0xFFFF9800);
        } else if (s == 1) {
            miniHouse(c, x, y, 0.85f, 0xFFFFEB3B);
        } else if (w.roadDirs[n] == 0 && w.canFlag(n, me)) {
            p.setColor(0xFF5D4037);
            c.drawRect(x - 0.6f, y - 8, x + 0.6f, y, p);
            p.setColor(0xFFFFEB3B);
            c.drawRect(x + 0.6f, y - 8, x + 5, y - 5, p);
        }
    }

    private void miniHouse(Canvas c, float x, float y, float s, int col) {
        p.setColor(0x88000000);
        rf.set(x - 5 * s, y - 1.5f * s, x + 5 * s, y + 1.5f * s);
        c.drawOval(rf, p);
        p.setColor(col);
        c.drawRect(x - 4 * s, y - 6 * s, x + 4 * s, y, p);
        path.reset();
        path.moveTo(x - 5.5f * s, y - 6 * s);
        path.lineTo(x, y - 11 * s);
        path.lineTo(x + 5.5f * s, y - 6 * s);
        path.close();
        p.setColor(shade(col, 0.7f));
        c.drawPath(path, p);
    }

    // ---------- Bäume und Steine ----------

    void tree(Canvas c, float x, float y, int kind, int stage, float t, int n) {
        float s = stage >= 3 ? 1f : 0.35f + stage * 0.2f;
        float sway = (float) Math.sin(t * 1.3f + n * 0.7f) * 0.8f * s;
        p.setColor(0x38000000);
        rf.set(x - 7 * s, y - 2 * s, x + 9 * s, y + 2.5f * s);
        c.drawOval(rf, p);
        if (kind == 3) {
            // Palme
            float lean = 2.5f * s;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.2f * s);
            p.setColor(0xFF8A6A42);
            path.reset();
            path.moveTo(x, y);
            path.quadTo(x + lean * 0.2f, y - 10 * s, x + lean + sway, y - 19 * s);
            c.drawPath(path, p);
            p.setStrokeWidth(0.5f * s);
            p.setColor(0xFF6A4E2E);
            for (int i = 1; i < 6; i++) {
                float f = i / 6f, bx = x + lean * f * f + sway * f, by = y - 19 * s * f;
                c.drawLine(bx - 1.1f * s, by, bx + 1.1f * s, by - 0.4f * s, p);
            }
            float tx = x + lean + sway, ty = y - 19 * s;
            p.setStrokeWidth(1.6f * s);
            for (int k = 0; k < 7; k++) {
                double a = Math.PI * (k / 6.0) + Math.sin(t + n) * 0.05;
                float ex = tx + (float) Math.cos(a) * -9 * s, ey = ty - (float) Math.sin(a) * 3 * s + 4 * s;
                p.setColor(k % 2 == 0 ? 0xFF3E8A2E : 0xFF56A23A);
                path.reset();
                path.moveTo(tx, ty);
                path.quadTo((tx + ex) / 2, ty - 4 * s, ex, ey);
                c.drawPath(path, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setColor(0xFF6A4A26);
            c.drawCircle(tx - 1, ty + 1, 1.1f * s, p);
            c.drawCircle(tx + 1, ty + 1.4f, 1.1f * s, p);
            return;
        }
        if (kind == 1) {
            p.setColor(0xFF5A3B22);
            c.drawRect(x - 1.3f * s, y - 7 * s, x + 1.3f * s, y, p);
            for (int i = 0; i < 3; i++) {
                float by = y - 5 * s - i * 6.5f * s, wd = (8.5f - i * 2.2f) * s;
                path.reset();
                path.moveTo(x - wd + sway * i * 0.4f, by);
                path.lineTo(x + sway * (i + 1) * 0.5f, by - 10 * s);
                path.lineTo(x + wd + sway * i * 0.4f, by);
                path.close();
                p.setColor(i % 2 == 0 ? 0xFF2C5A2C : 0xFF356B33);
                c.drawPath(path, p);
            }
            p.setColor(0x30FFFFFF);
            c.drawCircle(x - 2 * s + sway, y - 18 * s, 2f * s, p);
        } else if (kind == 2) {
            p.setColor(0xFFE8E4DA);
            c.drawRect(x - 1.1f * s, y - 12 * s, x + 1.1f * s, y, p);
            p.setColor(0xFF444444);
            c.drawRect(x - 1.1f * s, y - 7 * s, x + 0.4f * s, y - 6.3f * s, p);
            p.setColor(0xFF8DBB45);
            rf.set(x - 6 * s + sway, y - 25 * s, x + 6 * s + sway, y - 10 * s);
            c.drawOval(rf, p);
            p.setColor(0xFFA9D25A);
            c.drawCircle(x - 2 * s + sway, y - 20 * s, 3.2f * s, p);
        } else {
            p.setColor(0xFF6B4628);
            c.drawRect(x - 1.6f * s, y - 10 * s, x + 1.6f * s, y, p);
            p.setColor(0xFF2F6E25);
            c.drawCircle(x + sway, y - 16 * s, 8.5f * s, p);
            p.setColor(0xFF3F8A2F);
            c.drawCircle(x - 3 * s + sway, y - 17 * s, 5.5f * s, p);
            c.drawCircle(x + 3.5f * s + sway, y - 14 * s, 5f * s, p);
            p.setColor(0xFF62A841);
            c.drawCircle(x - 3.5f * s + sway, y - 20 * s, 3f * s, p);
        }
    }

    private static final float[][] STONE_POS = {{0, 0}, {-5, -1}, {5, 1}, {-2, -4}, {3, -4}, {0, 3}};

    void stones(Canvas c, float x, float y, int amount, int n) {
        int k = Math.min(6, Math.max(1, amount));
        p.setColor(0x38000000);
        rf.set(x - 10, y - 3, x + 11, y + 5);
        c.drawOval(rf, p);
        for (int i = 0; i < k; i++) {
            float sx = x + STONE_POS[i][0], sy = y + STONE_POS[i][1];
            float r = 4.2f - (i % 3) * 0.6f;
            path.reset();
            path.moveTo(sx - r, sy);
            path.lineTo(sx - r * 0.6f, sy - r * 1.1f);
            path.lineTo(sx + r * 0.4f, sy - r * 1.3f);
            path.lineTo(sx + r, sy - r * 0.3f);
            path.lineTo(sx + r * 0.7f, sy + r * 0.3f);
            path.close();
            p.setColor((i + n) % 2 == 0 ? 0xFFA19D96 : 0xFF8F8A82);
            c.drawPath(path, p);
            p.setColor(0xFFC9C5BE);
            c.drawCircle(sx - r * 0.3f, sy - r * 0.8f, r * 0.3f, p);
        }
    }

    // ---------- Fahnen ----------

    public static int wareColor(int t) {
        switch (t) {
            case World.WARE_LOG: return 0xFF8D5A2B;
            case World.WARE_BOARD: return 0xFFE2BC7A;
            default: return 0xFF9E9E9E;
        }
    }

    public void ware(Canvas c, int type, float x, float y, float s) {
        p.setStyle(Paint.Style.FILL);
        switch (type) {
            case World.WARE_LOG:
                p.setColor(0xFF7A4A22);
                rf.set(x - 4 * s, y - 1.6f * s, x + 4 * s, y + 1.6f * s);
                c.drawRoundRect(rf, 1.6f * s, 1.6f * s, p);
                p.setColor(0xFFD9A86A);
                c.drawCircle(x + 3.6f * s, y, 1.4f * s, p);
                return;
            case World.WARE_BOARD:
                p.setColor(0xFFB98B4E);
                c.drawRect(x - 4.5f * s, y - 1.2f * s, x + 4.5f * s, y + 1.4f * s, p);
                p.setColor(0xFFEBC98A);
                c.drawRect(x - 4.5f * s, y - 1.2f * s, x + 4.5f * s, y - 0.2f * s, p);
                return;
            case World.WARE_STONE:
                blob(c, x, y, 2.6f * s, 0xFF8A8680, 0xFFC4C0BA);
                return;
            case World.WARE_FISH:
                p.setColor(0xFF7FA7C9);
                rf.set(x - 3.6f * s, y - 1.3f * s, x + 2.4f * s, y + 1.3f * s);
                c.drawOval(rf, p);
                path.reset();
                path.moveTo(x + 2 * s, y);
                path.lineTo(x + 4.2f * s, y - 1.6f * s);
                path.lineTo(x + 4.2f * s, y + 1.6f * s);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFF22313F);
                c.drawCircle(x - 2.4f * s, y - 0.3f * s, 0.4f * s, p);
                return;
            case World.WARE_MEAT:
                p.setColor(0xFFC0504D);
                rf.set(x - 3 * s, y - 2 * s, x + 2.4f * s, y + 2 * s);
                c.drawOval(rf, p);
                p.setColor(0xFFF3E5D8);
                c.drawRect(x + 2 * s, y - 0.5f * s, x + 4.2f * s, y + 0.5f * s, p);
                c.drawCircle(x + 4.3f * s, y, 0.8f * s, p);
                return;
            case World.WARE_BREAD:
                p.setColor(0xFFC98A3A);
                rf.set(x - 3.8f * s, y - 2 * s, x + 3.8f * s, y + 1.6f * s);
                c.drawOval(rf, p);
                p.setColor(0xFFE8B66A);
                for (int i = -1; i <= 1; i++) c.drawRect(x + i * 1.8f * s - 0.3f * s, y - 1.6f * s, x + i * 1.8f * s + 0.3f * s, y - 0.4f * s, p);
                return;
            case World.WARE_GRAIN:
                p.setColor(0xFFD9B44A);
                for (int i = -1; i <= 1; i++) c.drawRect(x + i * 1.2f * s - 0.3f * s, y - 2.5f * s, x + i * 1.2f * s + 0.3f * s, y + 2.5f * s, p);
                p.setColor(0xFFE8C65A);
                for (int i = -1; i <= 1; i++) {
                    rf.set(x + i * 1.2f * s - 0.8f * s, y - 4 * s, x + i * 1.2f * s + 0.8f * s, y - 1.6f * s);
                    c.drawOval(rf, p);
                }
                p.setColor(0xFF8D6E2A);
                c.drawRect(x - 1.8f * s, y + 0.4f * s, x + 1.8f * s, y + 1.1f * s, p);
                return;
            case World.WARE_FLOUR:
                p.setColor(0xFFEDE7DA);
                rf.set(x - 2.8f * s, y - 2.6f * s, x + 2.8f * s, y + 2.4f * s);
                c.drawRoundRect(rf, 1.2f * s, 1.2f * s, p);
                p.setColor(0xFFB0A48E);
                c.drawRect(x - 1.4f * s, y - 2.9f * s, x + 1.4f * s, y - 2.3f * s, p);
                return;
            case World.WARE_WATER:
                p.setColor(0xFF8D6E4A);
                rf.set(x - 2.6f * s, y - 2.4f * s, x + 2.6f * s, y + 2.4f * s);
                c.drawRoundRect(rf, 0.8f * s, 0.8f * s, p);
                p.setColor(0xFF4FA3E0);
                c.drawRect(x - 2.1f * s, y - 2.3f * s, x + 2.1f * s, y - 1.3f * s, p);
                return;
            case World.WARE_PIG:
                p.setColor(0xFFF2A6B4);
                rf.set(x - 3.4f * s, y - 2 * s, x + 2.6f * s, y + 1.8f * s);
                c.drawOval(rf, p);
                c.drawCircle(x + 3 * s, y - 0.4f * s, 1.3f * s, p);
                p.setColor(0xFFD9849A);
                c.drawRect(x - 2.4f * s, y + 1.2f * s, x - 1.6f * s, y + 2.6f * s, p);
                c.drawRect(x + 1 * s, y + 1.2f * s, x + 1.8f * s, y + 2.6f * s, p);
                return;
            case World.WARE_COAL:
                blob(c, x, y, 2.6f * s, 0xFF262626, 0xFF5A5A5A);
                return;
            case World.WARE_IRONORE:
                blob(c, x, y, 2.6f * s, 0xFF8A5A3C, 0xFFB5651D);
                return;
            case World.WARE_GOLD:
                p.setColor(0xFFC99A1E);
                path.reset();
                path.moveTo(x - 3.6f * s, y + 1.6f * s);
                path.lineTo(x - 2.4f * s, y - 1.4f * s);
                path.lineTo(x + 2.4f * s, y - 1.4f * s);
                path.lineTo(x + 3.6f * s, y + 1.6f * s);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFFFFE082);
                c.drawRect(x - 2 * s, y - 1.2f * s, x + 1.6f * s, y - 0.4f * s, p);
                return;
            case World.WARE_IRON:
                p.setColor(0xFF6E7B86);
                path.reset();
                path.moveTo(x - 3.8f * s, y + 1.4f * s);
                path.lineTo(x - 2.6f * s, y - 1.4f * s);
                path.lineTo(x + 2.6f * s, y - 1.4f * s);
                path.lineTo(x + 3.8f * s, y + 1.4f * s);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFFB0BEC5);
                c.drawRect(x - 2.2f * s, y - 1.2f * s, x + 1.8f * s, y - 0.5f * s, p);
                return;
        }
        if (type == World.WARE_BOAT) {
            c.save();
            c.translate(x, y);
            c.scale(s * 0.45f, s * 0.45f);
            boat(c, 0, -2, 1, 0, 0, false, false, 0);
            c.restore();
            return;
        }
        if (type == World.WARE_SWORD) {
            c.save();
            c.translate(x, y);
            c.scale(s, s);
            c.rotate(-40);
            p.setColor(0xFFDDE3E8);
            path.reset();
            path.moveTo(-0.7f, -1.5f);
            path.lineTo(-0.6f, -7.2f);
            path.lineTo(0, -8.4f);
            path.lineTo(0.6f, -7.2f);
            path.lineTo(0.7f, -1.5f);
            path.close();
            c.drawPath(path, p);
            p.setColor(0xFF9AA3AA);
            c.drawRect(-0.15f, -7.2f, 0.15f, -1.5f, p);
            p.setColor(0xFFC9A227);
            c.drawRect(-2.6f, -1.6f, 2.6f, -0.7f, p);
            p.setColor(0xFF6D4C41);
            c.drawRect(-0.55f, -0.7f, 0.55f, 2.2f, p);
            p.setColor(0xFFC9A227);
            c.drawCircle(0, 2.6f, 0.8f, p);
            c.restore();
            return;
        }
        if (type == World.WARE_SHIELD) {
            c.save();
            c.translate(x, y);
            c.scale(s, s);
            path.reset();
            path.moveTo(-3.6f, -3.6f);
            path.lineTo(3.6f, -3.6f);
            path.lineTo(3.6f, -0.5f);
            path.quadTo(3.4f, 3f, 0, 4.4f);
            path.quadTo(-3.4f, 3f, -3.6f, -0.5f);
            path.close();
            p.setColor(0xFF6B7178);
            c.drawPath(path, p);
            c.scale(0.8f, 0.8f);
            p.setColor(0xFF8D5A2B);
            c.drawPath(path, p);
            p.setColor(0xFF6B7178);
            c.drawRect(-0.6f, -3.6f, 0.6f, 4f, p);
            c.drawRect(-3.6f, -1.2f, 3.6f, 0f, p);
            p.setColor(0xFFC9A227);
            c.drawCircle(0, -0.6f, 1f, p);
            c.restore();
            return;
        }
        if (World.isTool(type)) {
            // Werkzeug auf einem kleinen Brett
            p.setColor(0xFFCFB88E);
            rf.set(x - 4 * s, y - 3.4f * s, x + 4 * s, y + 3.4f * s);
            c.drawRoundRect(rf, 1 * s, 1 * s, p);
            c.save();
            c.translate(x, y);
            c.scale(s, s);
            int ti = TOOL_ICON[type - World.FIRST_TOOL];
            tool(c, new float[]{-2.2f, 2.6f}, ti == 5 ? 165 : 145, ti);
            c.restore();
        }
    }

    private static final int[] TOOL_ICON = {0, 8, 1, 3, 2, 5, 6, 7, 9, 10};

    private void blob(Canvas c, float x, float y, float r, int col, int hi) {
        path.reset();
        path.moveTo(x - r, y + r * 0.3f);
        path.lineTo(x - r * 0.6f, y - r * 0.8f);
        path.lineTo(x + r * 0.5f, y - r);
        path.lineTo(x + r, y);
        path.lineTo(x + r * 0.5f, y + r * 0.8f);
        path.lineTo(x - r * 0.4f, y + r * 0.8f);
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
        p.setColor(hi);
        c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.35f, p);
    }

    private static final float[][] WARE_POS = {{-6, 2}, {6, 2}, {-6, -2}, {6, -2}, {-3, 4}, {3, 4}, {-9, 0}, {9, 0}};

    void flag(Canvas c, World w, World.Flag f, float x, float y, float t) {
        if (f == null) return;
        for (int i = 0; i < f.wares.size() && i < WARE_POS.length; i++)
            ware(c, f.wares.get(i).type, x + WARE_POS[i][0], y + WARE_POS[i][1], 0.8f);
        p.setColor(0x40000000);
        rf.set(x - 3, y - 1, x + 3, y + 1.5f);
        c.drawOval(rf, p);
        p.setColor(0xFF4E3524);
        c.drawRect(x - 0.7f, y - 18, x + 0.7f, y, p);
        float wv = (float) Math.sin(t * 6 + x * 0.1f);
        path.reset();
        path.moveTo(x + 0.7f, y - 18);
        path.quadTo(x + 4.5f, y - 19.5f + wv, x + 9, y - 18 + wv * 0.6f);
        path.lineTo(x + 9, y - 12.5f + wv * 0.6f);
        path.quadTo(x + 4.5f, y - 14 + wv, x + 0.7f, y - 12.5f);
        path.close();
        p.setColor(World.P_COLORS[f.player]);
        c.drawPath(path, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(x + 0.7f, y - 15.7f, x + 8.5f, y - 14.7f + wv * 0.4f, p);
    }

    /** Wimpelfarbe für Gebäude-Bilder in Fenstern */
    public void bannerFor(int pl) {
        pc = World.P_COLORS[pl];
    }

    // ---------- Gebäude ----------

    private void house(Canvas c, float x, float y, float w, float h, float roofH, int wall, int roof) {
        float l = x - w / 2, r = x + w / 2, d = w * 0.32f, dy = d * 0.55f;
        p.setColor(0x40000000);
        rf.set(l - 2, y - 3, r + d + 4, y + 4);
        c.drawOval(rf, p);
        // Seitenwand
        path.reset();
        path.moveTo(r, y);
        path.lineTo(r + d, y - dy);
        path.lineTo(r + d, y - h - dy);
        path.lineTo(r, y - h);
        path.close();
        p.setColor(shade(wall, 0.72f));
        c.drawPath(path, p);
        // Vorderwand
        p.setColor(wall);
        c.drawRect(l, y - h, r, y, p);
        // Giebel
        path.reset();
        path.moveTo(l, y - h);
        path.lineTo(x, y - h - roofH);
        path.lineTo(r, y - h);
        path.close();
        p.setColor(shade(wall, 0.9f));
        c.drawPath(path, p);
        // Dach
        path.reset();
        path.moveTo(x, y - h - roofH);
        path.lineTo(r + 1.5f, y - h + 1);
        path.lineTo(r + d + 1.5f, y - h - dy + 1);
        path.lineTo(x + d, y - h - roofH - dy);
        path.close();
        p.setColor(roof);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(shade(roof, 0.6f));
        path.reset();
        path.moveTo(l - 1.5f, y - h + 1);
        path.lineTo(x, y - h - roofH);
        path.lineTo(r + 1.5f, y - h + 1);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        // Tür
        p.setColor(0xFF4A2E1A);
        c.drawRect(x - w * 0.12f, y - h * 0.5f, x + w * 0.12f, y, p);
    }

    private void tower(Canvas c, float x, float y, float w, float h, int wall) {
        float l = x - w / 2, r = x + w / 2, d = w * 0.3f, dy = d * 0.55f;
        p.setColor(0x40000000);
        rf.set(l - 2, y - 3, r + d + 4, y + 4);
        c.drawOval(rf, p);
        path.reset();
        path.moveTo(r, y);
        path.lineTo(r + d, y - dy);
        path.lineTo(r + d, y - h - dy);
        path.lineTo(r, y - h);
        path.close();
        p.setColor(shade(wall, 0.7f));
        c.drawPath(path, p);
        p.setColor(wall);
        c.drawRect(l, y - h, r, y, p);
        // Zinnen
        float zw = w / 5f;
        for (int i = 0; i < 5; i += 2) c.drawRect(l + i * zw, y - h - 3.5f, l + (i + 1) * zw, y - h, p);
        p.setColor(shade(wall, 0.7f));
        c.drawRect(r, y - h - dy - 3.5f, r + d * 0.5f, y - h - dy, p);
        // Fugen
        p.setColor(shade(wall, 0.85f));
        for (float yy = y - 4; yy > y - h; yy -= 4) c.drawRect(l, yy, r, yy + 0.6f, p);
        p.setColor(0xFF2E2E2E);
        c.drawRect(x - 1.2f, y - h * 0.7f, x + 1.2f, y - h * 0.55f, p);
        p.setColor(0xFF4A2E1A);
        c.drawRect(x - w * 0.15f, y - h * 0.3f, x + w * 0.15f, y, p);
    }

    // ---------- Lava und Boote ----------

    private void lavaGlow(Canvas c, float x, float y, float t, int n) {
        float g = 0.5f + 0.5f * (float) Math.sin(t * 1.7f + n * 1.3f);
        p.setColor(((int) (70 + 90 * g) << 24) | 0xFFB030);
        c.drawCircle(x + (h01(n, 50) - 0.5f) * 12, y + (h01(n, 51) - 0.5f) * 6, 2.5f + g * 2.5f, p);
        if (h01(n, 52) < 0.12f) {
            // Rauchfähnchen
            float f = (t * 0.25f + h01(n, 53)) % 1f;
            p.setColor(((int) (0x60 * (1 - f)) << 24) | 0x5A5048);
            c.drawCircle(x + f * 5, y - 4 - f * 18, 2 + f * 4, p);
        }
    }

    /** Ruderboot; mit Fischer (rudern oder Netz) oder leer am Ufer */
    void boat(Canvas c, float x, float y, int face, float t, int id, boolean withFisher, boolean net, float ph) {
        float bob = (float) Math.sin(t * 2.2f + id) * 0.6f;
        y += bob + 2;
        // Wellen
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.6f);
        p.setColor(0x70FFFFFF);
        rf.set(x - 11, y - 1.5f, x + 11, y + 2.5f);
        c.drawOval(rf, p);
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.translate(x, y);
        if (face < 0) c.scale(-1, 1);
        // Rumpf
        path.reset();
        path.moveTo(-9, -3);
        path.lineTo(9.5f, -3.6f);
        path.quadTo(8, 1.6f, 3, 1.8f);
        path.lineTo(-6, 1.8f);
        path.quadTo(-9, 0.6f, -9, -3);
        path.close();
        p.setColor(0xFF7A4E2C);
        c.drawPath(path, p);
        p.setColor(0xFF5A3820);
        c.drawRect(-8, -3.2f, 8.5f, -2.2f, p);
        p.setColor(0xFF9A6A40);
        c.drawRect(-7.5f, -1.4f, 7, -0.8f, p);
        p.setColor(0x40000000);
        c.drawRect(-6, 0.6f, 4, 1.8f, p);
        if (withFisher) {
            beginFigure(c, -1, -2, 1, SHIRT[World.J_FISHER], 0xFF5D4037, 0, 0, net ? 18 : 0, 40, true);
            float a;
            if (net) {
                float f = frac(ph / 2.5f);
                a = f < 0.5f ? 60 + 80 * f * 2 : 140 - 100 * (f - 0.5f) * 2;
            } else a = 60 + 50 * (float) Math.sin(ph * Math.PI * 2);
            float[] h = arm(c, a, SHIRT[World.J_FISHER]);
            endFigure(c);
            if (net) {
                // Netz im Wasser
                float f = frac(ph / 2.5f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.3f);
                p.setColor(0xC0D8C8A0);
                float nx0 = 10, ny0 = -2, nr = 4 + 3 * (float) Math.sin(f * Math.PI);
                c.drawLine(-1 + h[0], -7 + h[1], nx0, ny0, p);
                rf.set(nx0 - nr, ny0 - nr * 0.3f, nx0 + nr, ny0 + nr * 0.5f);
                c.drawOval(rf, p);
                for (int k = -2; k <= 2; k++) c.drawLine(nx0 + k * nr / 2.5f, ny0 - nr * 0.25f, nx0 + k * nr / 3.5f, ny0 + nr * 0.45f, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                // Ruder
                float r = (float) Math.toRadians(a);
                float ox = (float) Math.sin(r) * 4.8f - 1, oy = (float) Math.cos(r) * 4.8f - 7;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.7f);
                p.setColor(0xFFB08860);
                c.drawLine(ox - 4, oy - 2, ox + 6, oy + 7, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFB08860);
                rf.set(ox + 4.5f, oy + 5.5f, ox + 8, oy + 8.5f);
                c.drawOval(rf, p);
            }
        } else {
            // liegendes Ruder
            p.setColor(0xFFB08860);
            c.drawRect(-6, -2.8f, 6, -2.2f, p);
        }
        c.restore();
    }

    // ---------- Soldaten ----------

    public static final int[] RANK_COL = {0xFF8E8E8E, 0xFFB0703A, 0xFFD5DAE0, 0xFF5B8DEF, 0xFFFFC107};

    private void soldier(Canvas c, World w, World.Settler s, float x, float y, int face, float legs, boolean walk) {
        int shirt = World.P_COLORS[s.player];
        int helm = RANK_COL[Math.max(0, Math.min(4, s.rank))];
        float wt = w.time;
        if (s.state == World.A_DEAD) {
            // gefallen: liegt am Boden
            float f = Math.min(1f, (3f - s.timer) / 0.6f);
            c.save();
            c.translate(x, y);
            c.rotate(-face * 88 * f);
            beginFigure(c, 0, 0, face, shade(shirt, 0.8f), helm, 0, 0, 0, 20, false);
            arm(c, 40, shade(shirt, 0.8f));
            endFigure(c);
            c.restore();
            if (s.timer < 1.2f) {
                // kleines Grabkreuz erscheint
                p.setColor(0xFF6D4C41);
                c.drawRect(x + face * 5 - 0.4f, y - 5, x + face * 5 + 0.4f, y, p);
                c.drawRect(x + face * 5 - 1.6f, y - 3.8f, x + face * 5 + 1.6f, y - 3.1f, p);
            }
            return;
        }
        boolean fighting = s.state == World.A_FIGHT && !walk;
        boolean waiting = (s.state == World.A_WAIT || s.state == World.A_DEFEND) && !walk;
        if (fighting) {
            boolean attacker = s.atk != 0;
            face = attacker ? -1 : 1;
            x += attacker ? 7f : -7f;
            y += attacker ? 2f : -1f;
        } else if (waiting && s.atk != 0) {
            int k = s.id % 7;
            x += (k % 4 - 1.5f) * 5f + 6;
            y += (k / 4) * 4f + 3;
            face = -1;
        }
        float sw = fighting ? Math.max(0, 1 - (wt - s.swingT) / 0.45f) : 0;
        float lunge = fighting ? sw * 1.6f * face : 0;
        float lean = fighting ? 8 * sw : 0;
        beginFigure(c, x + lunge, y, face, shirt, helm, walk ? legs : 0, fighting ? 0.3f : 0, lean, walk ? -28 * legs : 10, false);
        // Schild vorn am Körper
        p.setColor(0xFF6B7178);
        rf.set(0.6f, -1.6f, 4.0f, 3.6f);
        c.drawOval(rf, p);
        p.setColor(shade(shirt, 0.9f));
        rf.set(1.0f, -1.2f, 3.6f, 3.2f);
        c.drawOval(rf, p);
        p.setColor(0xFFC9A227);
        c.drawCircle(2.3f, 1f, 0.55f, p);
        // Schwertarm
        float a = fighting ? 170 - 120 * (1 - sw) * (1 - sw) : walk ? 25 : 40;
        if (fighting && sw <= 0) a = 120 + 15 * (float) Math.sin(wt * 6 + s.id);
        float[] h = arm(c, a, shirt);
        float r = (float) Math.toRadians(a + (fighting ? 10 : 150));
        float dx = (float) Math.sin(r), dy = (float) Math.cos(r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.75f);
        p.setColor(0xFFE3E8EC);
        c.drawLine(h[0], h[1], h[0] + dx * 6.5f, h[1] + dy * 6.5f, p);
        p.setStrokeWidth(0.9f);
        p.setColor(0xFFC9A227);
        c.drawLine(h[0] - dy * 1.2f, h[1] + dx * 1.2f, h[0] + dy * 1.2f, h[1] - dx * 1.2f, p);
        p.setStyle(Paint.Style.FILL);
        endFigure(c);
        // Rang-Streifen auf dem Helm
        if (s.rank > 0) {
            p.setColor(0xFFFFFFFF);
            for (int k = 0; k < s.rank; k++) c.drawCircle(x + lunge - 2f + k * 1.3f, y - 17.6f, 0.45f, p);
        }
        if (fighting || waiting) {
            // Lebensbalken
            float mx = World.maxHp(s.rank), f = Math.max(0, s.hp) / mx;
            p.setColor(0xB0000000);
            c.drawRect(x - 4.2f, y - 21f, x + 4.2f, y - 19.6f, p);
            p.setColor(f > 0.5f ? 0xFF43A047 : f > 0.25f ? 0xFFFFB300 : 0xFFE53935);
            c.drawRect(x - 4f, y - 20.8f, x - 4f + 8f * f, y - 19.8f, p);
        }
        if (wt - s.hitT < 0.3f) {
            float g = 1 - (wt - s.hitT) / 0.3f;
            p.setColor(((int) (255 * g) << 24) | 0xFFF59D);
            for (int k = 0; k < 6; k++) {
                double an = k * Math.PI / 3 + s.id;
                c.drawCircle(x + (float) Math.cos(an) * 3.5f * (1.2f - g), y - 9 + (float) Math.sin(an) * 3.5f * (1.2f - g), 0.7f, p);
            }
        }
    }

    /** Flammen (Größe s) */
    private void fire(Canvas c, float x, float y, float s, float t, int seed) {
        for (int k = 0; k < 5; k++) {
            float ph = t * 7 + k * 1.7f + seed;
            float fx = x + (k - 2) * 2.6f * s + (float) Math.sin(ph) * 0.8f * s;
            float hgt = (5 + 3 * (float) Math.sin(ph * 1.3f)) * s;
            path.reset();
            path.moveTo(fx - 1.8f * s, y);
            path.quadTo(fx - 1.6f * s, y - hgt * 0.6f, fx + (float) Math.sin(ph * 2) * s, y - hgt);
            path.quadTo(fx + 1.6f * s, y - hgt * 0.6f, fx + 1.8f * s, y);
            path.close();
            p.setColor(0xD0FF6D00);
            c.drawPath(path, p);
            c.save();
            c.translate(fx, y);
            c.scale(0.55f, 0.6f);
            c.translate(-fx, -y);
            p.setColor(0xE0FFD54F);
            c.drawPath(path, p);
            c.restore();
        }
    }

    private void smokePuffs(Canvas c, float x, float y, float t, float s, int dark) {
        for (int k = 0; k < 5; k++) {
            float f = ((t * 0.3f + k / 5f) % 1f);
            int a = (int) (0x90 * (1 - f));
            p.setColor((a << 24) | (dark & 0xFFFFFF));
            c.drawCircle(x + f * 8 * s + (float) Math.sin(t + k) * 2, y - f * 26 * s, (2 + f * 5) * s, p);
        }
    }

    /** abgebranntes Gebäude: erst Flammen, dann Rauch, dann Schutt */
    private void ruin(Canvas c, World w, float[] r, float t) {
        int n = (int) r[0];
        float age = w.time - r[1], s = r[2] >= 2 ? 1.4f : 1f;
        float x = nx(n), y = ny(w, n);
        if (age > 230) return;
        float fade = age > 200 ? (230 - age) / 30f : 1f;
        int a = (int) (255 * fade);
        // Schutt
        p.setColor((a << 24) | 0x3E3A36);
        rf.set(x - 11 * s, y - 3 * s, x + 11 * s, y + 3 * s);
        c.drawOval(rf, p);
        for (int k = 0; k < 9; k++) {
            float bx = x + ((k * 37 + n) % 17 - 8) * s, by = y - ((k * 13 + n) % 5) * 0.8f * s;
            p.setColor((a << 24) | (k % 3 == 0 ? 0x1E1A16 : k % 3 == 1 ? 0x5A4A3A : 0x7A6E62));
            c.drawRect(bx - 1.8f, by - 1.2f, bx + 1.8f, by + 0.6f, p);
        }
        p.setColor((a << 24) | 0x2A2420);
        c.drawRect(x - 7 * s, y - 9 * s, x - 5.6f * s, y - 1, p);
        c.drawRect(x + 4 * s, y - 6 * s, x + 5.4f * s, y - 1, p);
        if (age < 30) fire(c, x, y - 1, s * (age < 20 ? 1.2f : (30 - age) / 8f), t, n);
        if (age < 90) smokePuffs(c, x, y - 8, t, s, 0x555555);
    }

    private void banner(Canvas c, float x, float y, float t) {
        p.setColor(0xFF3E2A1C);
        c.drawRect(x - 0.45f, y - 12, x + 0.45f, y, p);
        p.setColor(0xFFD8B040);
        c.drawCircle(x, y - 12.4f, 0.8f, p);
        float w1 = (float) Math.sin(t * 5 + x), w2 = (float) Math.sin(t * 5 + x - 1.5f);
        path.reset();
        path.moveTo(x + 0.45f, y - 11.5f);
        path.quadTo(x + 3.5f, y - 12.2f + w1 * 0.8f, x + 7.5f, y - 11.2f + w2);
        path.lineTo(x + 6.6f, y - 9.3f + w2);
        path.lineTo(x + 7.5f, y - 7.4f + w2);
        path.quadTo(x + 3.5f, y - 8.2f + w1 * 0.8f, x + 0.45f, y - 7.6f);
        path.close();
        p.setColor(pc);
        c.drawPath(path, p);
        p.setColor(0x40000000);
        c.drawRect(x + 0.45f, y - 9.6f + w1 * 0.4f, x + 6.8f, y - 9.0f + w2 * 0.8f, p);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(x + 3.4f, y - 9.7f + w1 * 0.7f, 0.9f, p);
    }

    // ---------- Gebäude-Bilder (vorgezeichnet) ----------
    private Art art;
    private static final float[] LV = {1.7f, 2.7f, 3.8f, 5.2f};
    private final Bitmap[][] spr = new Bitmap[LV.length][World.NB];
    /** Maßstab, in dem das nächste Gebäudebild erscheint (0 = Kartenzoom) */
    public float scaleHint = 0;
    /** Drehwinkel des Sägeblatts */
    private float sawAngle = 0;

    private synchronized Bitmap sprite(int type, int lv) {
        Bitmap b = spr[lv][type];
        if (b != null) return b;
        if (art == null) art = new Art(new Renderer());
        float[] bx = Art.BOX[type];
        float ss = LV[lv];
        int w = (int) Math.ceil((bx[2] - bx[0]) * ss), h = (int) Math.ceil((bx[3] - bx[1]) * ss);
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(b);
        bc.scale(ss, ss);
        bc.translate(-bx[0], -bx[1]);
        art.draw(bc, type);
        spr[lv][type] = b;
        return b;
    }

    private boolean warming;

    /** Alle Gebäudebilder im Hintergrund vorzeichnen, damit es später nicht ruckelt */
    public void warmSprites() {
        if (warming) return;
        warming = true;
        final int first = level(zoom);
        Thread th = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int t = 0; t < World.NB; t++) sprite(t, first);
                    for (int lv = 0; lv < LV.length; lv++)
                        for (int t = 0; t < World.NB; t++) sprite(t, lv);
                } catch (Throwable e) {
                    // zur Not wird beim Zeichnen nachgeholt
                }
            }
        }, "Gebaeudebilder");
        th.setPriority(Thread.MIN_PRIORITY);
        th.start();
    }

    private static int level(float s) {
        int lv = 0;
        while (lv < LV.length - 1 && LV[lv] < s) lv++;
        return lv;
    }

    public void buildingSprite(Canvas c, int type, float x, float y, float t) {
        int lv = level(scaleHint > 0 ? scaleHint : zoom);
        Bitmap b = sprite(type, lv);
        float[] bx = Art.BOX[type];
        float ss = LV[lv];
        c.save();
        c.translate(x + bx[0], y + bx[1]);
        c.scale(1f / ss, 1f / ss);
        c.drawBitmap(b, 0, 0, bmpPaint);
        c.restore();
        // bewegliche Teile
        float[] bn = Art.BANNERS[type];
        for (int i = 0; i + 1 < bn.length; i += 2) banner(c, x + bn[i], y + bn[i + 1], t + i);
        if (type == World.B_MILL) sails(c, x + Art.MILL_HUB[0], y + Art.MILL_HUB[1]);
        if (type == World.B_SAW) sawBlade(c, x + Art.SAW_BLADE[0], y + Art.SAW_BLADE[1]);
    }

    private void sails(Canvas c, float hx, float hy) {
        for (int k = 0; k < 4; k++) {
            float a = millAngle + k * (float) Math.PI / 2;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float ox = -sa, oy = ca;
            float len = 17;
            // Flügelfläche mit Gitter
            path.reset();
            path.moveTo(hx + ca * 4, hy + sa * 4);
            path.lineTo(hx + ca * len, hy + sa * len);
            path.lineTo(hx + ca * len + ox * 3.6f, hy + sa * len + oy * 3.6f);
            path.lineTo(hx + ca * 4 + ox * 3.2f, hy + sa * 4 + oy * 3.2f);
            path.close();
            p.setColor(0xD8EFE6D2);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.25f);
            p.setColor(0xFF7A5A40);
            c.drawPath(path, p);
            for (int j = 1; j < 6; j++) {
                float f = 4 + (len - 4) * j / 6f;
                c.drawLine(hx + ca * f, hy + sa * f, hx + ca * f + ox * 3.4f, hy + sa * f + oy * 3.4f, p);
            }
            c.drawLine(hx + ca * 4 + ox * 1.7f, hy + sa * 4 + oy * 1.7f, hx + ca * len + ox * 1.8f, hy + sa * len + oy * 1.8f, p);
            p.setStrokeWidth(0.9f);
            p.setColor(0xFF5D4037);
            c.drawLine(hx, hy, hx + ca * (len + 0.5f), hy + sa * (len + 0.5f), p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFF3E2A22);
        c.drawCircle(hx, hy, 1.5f, p);
        p.setColor(0xFF8A6A50);
        c.drawCircle(hx - 0.4f, hy - 0.4f, 0.6f, p);
    }

    private void sawBlade(Canvas c, float bx, float by) {
        p.setColor(0xFFD8D8DC);
        c.drawCircle(bx, by, 3.4f, p);
        p.setColor(0xFF9A9AA0);
        for (int k = 0; k < 10; k++) {
            float aa = sawAngle + k * 0.628f;
            c.drawCircle(bx + (float) Math.cos(aa) * 3.3f, by + (float) Math.sin(aa) * 3.3f, 0.45f, p);
        }
        p.setColor(0xFFB8B8BE);
        c.drawCircle(bx, by, 2.2f, p);
        p.setColor(0xFF606066);
        c.drawCircle(bx, by, 0.8f, p);
        p.setColor(0x60FFFFFF);
        c.drawCircle(bx - 1.1f, by - 1.1f, 0.6f, p);
    }

    /** Drehwinkel der Mühlenflügel beim nächsten Zeichnen */
    private float millAngle = 0.4f;

    void building(Canvas c, World w, World.Building b, float x, float y, float t) {
        if (b == null) return;
        pc = World.P_COLORS[b.player];
        if (b.done) {
            World.Settler wk = w.settlers.get(b.worker);
            boolean busy = wk != null && wk.state == World.S_SAW;
            millAngle = b.type == World.B_MILL && busy ? t * 2.2f : 0.4f;
            boolean sawing = b.type == World.B_SAW && wk != null && wk.state == World.S_SAW;
            sawAngle = sawing ? t * 14f : 0;
            buildingSprite(c, b.type, x, y, t);
            if (World.isHq(b) && (w.fallen & (1 << b.player)) != 0) {
                fire(c, x - 20, y - 30, 1.3f, t, 1);
                fire(c, x + 4, y - 32, 1.6f, t, 2);
                fire(c, x + 25, y - 26, 1.2f, t, 3);
                smokePuffs(c, x, y - 40, t, 1.6f, 0x444444);
            }
            millAngle = 0.4f;
            sawAngle = 0;
            boolean working = wk != null && !b.stopped && !b.problem;
            if (b.type == World.B_SMELTER && busy) {
                float g = 0.6f + 0.4f * (float) Math.sin(t * 9);
                p.setColor(((int) (0x90 * g) << 24) | 0xFF6A00);
                c.drawCircle(x + Art.SMELT_GLOW[0], y + Art.SMELT_GLOW[1], 2.6f + g * 0.6f, p);
                p.setColor(((int) (0xD0 * g) << 24) | 0xFFC040);
                c.drawCircle(x + Art.SMELT_GLOW[0], y + Art.SMELT_GLOW[1] + 0.8f, 1.5f, p);
            }
            if (b.type == World.B_BAKERY && working) {
                float g = 0.6f + 0.4f * (float) Math.sin(t * 5);
                p.setColor(((int) (0xA0 * g) << 24) | 0xFF7A20);
                c.drawCircle(x + Art.OVEN_GLOW[0], y + Art.OVEN_GLOW[1], 1.8f + g * 0.4f, p);
            }
            if (sawing) {
                // Sägemehl
                for (int k = 0; k < 6; k++) {
                    float f = ((t * 1.3f + k * 0.17f) % 1f);
                    p.setColor(0xCCE7CF9A);
                    c.drawCircle(x + Art.SAW_BLADE[0] - 1 - f * 5 + (float) Math.sin(k * 3 + t * 5) * 1.2f, y + Art.SAW_BLADE[1] + 2 + f * 5, 0.6f, p);
                }
            }
            float[] sm = Art.SMOKE[b.type];
            if (sm != null && working && World.hasUtil(b.type)) {
                for (int k = 0; k < 4; k++) {
                    float f = ((t * 0.35f + k / 4f) % 1f);
                    int a = (int) (0x80 * (1 - f) * Math.min(1, f * 6));
                    p.setColor((a << 24) | (b.type == World.B_SMELTER ? 0x9A9A9A : 0xDDDDDD));
                    c.drawCircle(x + sm[0] + f * 6 + (float) Math.sin(t * 1.3f + k) * 1.4f, y + sm[1] - f * 15, 1.2f + f * 3f, p);
                }
            }
            if (b.stopped) {
                p.setColor(0xFFC62828);
                c.drawCircle(x + 9, y - 16, 4.2f, p);
                p.setColor(0xFFFFFFFF);
                c.drawRect(x + 6.5f, y - 16.7f, x + 11.5f, y - 15.3f, p);
            }
            if (World.isMilitary(b.type) && !b.occupied) {
                p.setColor(0xCCFFFFFF);
                c.drawCircle(x + 10, y - 20, 4, p);
                p.setColor(0xFF333333);
                p.setTextSize(7);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("?", x + 10, y - 17.5f, p);
            }
            return;
        }
        // Baustelle
        float pr = b.progress();
        p.setColor(0x55000000);
        rf.set(x - 14, y - 4, x + 16, y + 5);
        c.drawOval(rf, p);
        p.setColor(0xFF8B6B45);
        rf.set(x - 12, y - 3, x + 14, y + 4);
        c.drawOval(rf, p);
        if (pr > 0.02f) {
            float top = y - 40;
            c.save();
            float[] bxx = Art.BOX[b.type];
            c.clipRect(x + bxx[0], y + bxx[3] - (bxx[3] - bxx[1]) * pr, x + bxx[2], y + bxx[3]);
            buildingSprite(c, b.type, x, y, t);
            c.restore();
            if (pr < 1) {
                p.setColor(0xFF8D6E4A);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.1f);
                float sh = Math.min(30, 48 * pr + 4);
                c.drawLine(x - 11, y, x - 11, y - sh, p);
                c.drawLine(x + 11, y, x + 11, y - sh, p);
                c.drawLine(x - 11, y - sh * 0.5f, x + 11, y - sh * 0.5f, p);
                p.setStyle(Paint.Style.FILL);
            }
            if (top > 0) top = 0;
        } else {
            p.setColor(0xFF6E5230);
            for (int i = -1; i <= 1; i += 2) {
                c.drawRect(x + i * 9 - 0.8f, y - 6, x + i * 9 + 0.8f, y, p);
            }
        }
        // Material, das schon da ist
        int boards = b.got[World.WARE_BOARD] - Math.min(b.got[World.WARE_BOARD], b.used);
        int st = b.got[World.WARE_STONE] - Math.max(0, Math.min(b.got[World.WARE_STONE], b.used - b.got[World.WARE_BOARD]));
        for (int i = 0; i < boards; i++) ware(c, World.WARE_BOARD, x - 16, y + 3 - i * 2.4f, 0.8f);
        for (int i = 0; i < st; i++) ware(c, World.WARE_STONE, x + 17 + (i % 2) * 4, y + 3 - (i / 2) * 3.5f, 0.8f);
        // Fortschrittsbalken
        p.setColor(0xAA000000);
        c.drawRect(x - 12, y + 7, x + 12, y + 10, p);
        p.setColor(0xFF8BC34A);
        c.drawRect(x - 11.5f, y + 7.5f, x - 11.5f + 23 * pr, y + 9.5f, p);
    }

    // ---------- fallende Bäume ----------

    private void falling(Canvas c, World w, float[] fl) {
        int n = (int) fl[0];
        if (!w.exploredOf(me)[n]) return;
        float x = nx(n), y = ny(w, n);
        float age = w.time - fl[1];
        // Stumpf
        p.setColor(0xFF6B4628);
        c.drawRect(x - 1.8f, y - 2.5f, x + 1.8f, y, p);
        p.setColor(0xFFD9B27A);
        rf.set(x - 1.8f, y - 3.3f, x + 1.8f, y - 1.9f);
        c.drawOval(rf, p);
        if (age < 1.1f) {
            float f = age / 1.1f;
            c.save();
            c.translate(x, y - 1);
            c.rotate(88f * f * f);
            tree(c, 0, 0, (int) fl[2], 3, 0, n);
            c.restore();
        } else {
            // liegender Stamm, wird gleich weggetragen
            p.setColor(0xFF6B4628);
            rf.set(x + 2, y - 2.5f, x + 22, y + 0.5f);
            c.drawRoundRect(rf, 1.5f, 1.5f, p);
            p.setColor(0xFFD9B27A);
            c.drawCircle(x + 2.5f, y - 1, 1.5f, p);
            if (age < 1.5f) {
                // Staubwolke beim Aufschlag
                float d = (age - 1.1f) / 0.4f;
                p.setColor(((int) (0x90 * (1 - d)) << 24) | 0xC8B48C);
                for (int k = 0; k < 5; k++) c.drawCircle(x + 6 + k * 4, y - 1 - d * 4, 1.5f + d * 3, p);
            }
        }
    }

    // ---------- Siedler ----------

    private static final int[] SHIRT = {0xFFD84315, 0xFFF9A825, 0xFF6D4C41, 0xFF2E7D32, 0xFF757575, 0xFFAD7A3D, 0xFF1E5BC6,
            0xFF3C7AA8, 0xFF4E6B2E, 0xFF5C8DB5, 0xFFC9A227, 0xFFE0E0E0, 0xFFF0F0F0, 0xFFC27BA0, 0xFF9E2B2B, 0xFF4E4E4E, 0xFF795548,
            0xFF37474F, 0xFF00897B, 0xFF8E6A3A, 0xFF5D4037};
    private static final float[][] BUILD_SPOTS = {{-13, 1}, {13, 0}, {-2, 7}, {9, 5}};

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Figur zeichnen. Danach steht die Leinwand auf der Schulter (für Werkzeug) – mit endFigure() zurücksetzen. */
    private void beginFigure(Canvas c, float x, float y, int face, int shirt, int hat, float legSwing, float crouch,
                             float lean, float armBack, boolean sit) {
        c.save();
        c.translate(x, y);
        if (face < 0) c.scale(-1, 1);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x40000000);
        rf.set(-3, -1, 3, 1.2f);
        c.drawOval(rf, p);
        float hipY = sit ? -2f : -5f + crouch * 2.2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(1.4f);
        p.setColor(0xFF3B2F2A);
        if (sit) {
            c.drawLine(-0.5f, hipY, 3.5f, hipY, p);
            c.drawLine(3.5f, hipY, 4f, 0, p);
            c.drawLine(0.5f, hipY, 4.5f, hipY + 0.4f, p);
            c.drawLine(4.5f, hipY + 0.4f, 5f, 0, p);
        } else if (crouch > 0.5f) {
            c.drawLine(0, hipY, -1.5f, -0.8f, p);
            c.drawLine(-1.5f, -0.8f, -4f, 0, p);
            c.drawLine(0.5f, hipY, 2.8f, hipY - 0.3f, p);
            c.drawLine(2.8f, hipY - 0.3f, 2.8f, 0, p);
        } else {
            c.drawLine(-0.8f, hipY, -0.8f + legSwing * 1.9f, 0, p);
            c.drawLine(0.8f, hipY, 0.8f - legSwing * 1.9f, 0, p);
        }
        c.save();
        c.translate(0, hipY);
        c.rotate(lean);
        // hinterer Arm
        p.setColor(shade(shirt, 0.7f));
        p.setStrokeWidth(1.3f);
        float ab = (float) Math.toRadians(armBack);
        c.drawLine(-0.4f, -5.2f, -0.4f + (float) Math.sin(ab) * 4.6f, -5.2f + (float) Math.cos(ab) * 4.6f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(shirt);
        rf.set(-2.6f, -6.6f, 2.6f, 0.4f);
        c.drawRoundRect(rf, 1.8f, 1.8f, p);
        p.setColor(0xFF4E342E);
        c.drawRect(-2.6f, -1.4f, 2.6f, -0.6f, p);
        p.setColor(0xFFF0C495);
        c.drawCircle(0.3f, -8.8f, 2.2f, p);
        p.setColor(0xFF3B2F2A);
        c.drawCircle(1.4f, -9.1f, 0.35f, p);
        if (hat != 0) {
            p.setColor(hat);
            rf.set(-2.2f, -11.6f, 2.8f, -9.2f);
            c.drawOval(rf, p);
        }
        c.translate(0.2f, -5.2f); // Schulter
    }

    /** Vorderen Arm im Winkel a (Grad, 0 = hängt nach unten, 90 = nach vorn, 180 = nach oben). Liefert Handposition. */
    private float[] arm(Canvas c, float a, int shirt) {
        float r = (float) Math.toRadians(a);
        float hx = (float) Math.sin(r) * 4.8f, hy = (float) Math.cos(r) * 4.8f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor(shirt);
        c.drawLine(0, 0, hx, hy, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF0C495);
        c.drawCircle(hx, hy, 0.8f, p);
        return new float[]{hx, hy};
    }

    private void endFigure(Canvas c) {
        c.restore();
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    /** Werkzeug in der Hand, zeigt in Richtung a (Grad wie arm). type: 0 Axt, 1 Spitzhacke, 2 Spaten, 3 Hammer, 4 Speer */
    private void tool(Canvas c, float[] h, float a, int type) {
        float r = (float) Math.toRadians(a);
        float dx = (float) Math.sin(r), dy = (float) Math.cos(r);
        float len = type == 3 ? 4f : type == 4 ? 11f : 7f;
        float ex = h[0] + dx * len, ey = h[1] + dy * len;
        float sx = h[0] - dx * (type == 4 ? 6f : 1.5f), sy = h[1] - dy * (type == 4 ? 6f : 1.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.9f);
        p.setColor(0xFF6D4C41);
        c.drawLine(sx, sy, ex, ey, p);
        p.setStyle(Paint.Style.FILL);
        // senkrecht zum Stiel
        float px = -dy, py = dx;
        switch (type) {
            case 0: // Axt
                path.reset();
                path.moveTo(ex - dx * 1.2f, ey - dy * 1.2f);
                path.lineTo(ex + px * 2.8f - dx * 0.4f, ey + py * 2.8f - dy * 0.4f);
                path.lineTo(ex + px * 2.6f + dx * 1.4f, ey + py * 2.6f + dy * 1.4f);
                path.lineTo(ex + dx * 0.6f, ey + dy * 0.6f);
                path.close();
                p.setColor(0xFFB0BEC5);
                c.drawPath(path, p);
                break;
            case 1: // Spitzhacke
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.1f);
                p.setColor(0xFF90A4AE);
                c.drawLine(ex - px * 3.2f - dx, ey - py * 3.2f - dy, ex + px * 3.2f - dx, ey + py * 3.2f - dy, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case 2: // Spaten
                p.setColor(0xFF9E9E9E);
                rf.set(ex - 1.3f, ey - 1.3f, ex + 1.3f, ey + 1.6f);
                c.drawRoundRect(rf, 0.6f, 0.6f, p);
                break;
            case 3: // Hammer
                p.setColor(0xFF78909C);
                c.drawCircle(ex, ey, 1.2f, p);
                c.drawCircle(ex + px * 1.2f, ey + py * 1.2f, 1.2f, p);
                break;
            case 5: { // Angel: lange dünne Rute
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.6f);
                p.setColor(0xFF8D6E4A);
                c.drawLine(ex, ey, ex + dx * 6, ey + dy * 6, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF5D4037);
                c.drawCircle(h[0] + dx * 2, h[1] + dy * 2, 0.8f, p);
                break;
            }
            case 6: { // Sense
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                p.setColor(0xFFB0BEC5);
                path.reset();
                path.moveTo(ex, ey);
                path.quadTo(ex + px * 3.5f + dx * 0.5f, ey + py * 3.5f + dy * 0.5f, ex + px * 5.5f - dx * 2f, ey + py * 5.5f - dy * 2f);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 7: { // Bogen
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.9f);
                p.setColor(0xFF6D4C41);
                path.reset();
                path.moveTo(h[0] + px * 4, h[1] + py * 4);
                path.quadTo(h[0] + dx * 3, h[1] + dy * 3, h[0] - px * 4, h[1] - py * 4);
                c.drawPath(path, p);
                p.setStrokeWidth(0.4f);
                p.setColor(0xFFEEEEEE);
                c.drawLine(h[0] + px * 4, h[1] + py * 4, h[0] - px * 4, h[1] - py * 4, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 8: // Säge
                p.setColor(0xFFB0BEC5);
                path.reset();
                path.moveTo(h[0], h[1]);
                path.lineTo(ex + px * 1.6f, ey + py * 1.6f);
                path.lineTo(ex, ey);
                path.close();
                c.drawPath(path, p);
                break;
            case 9: // Beil
                p.setColor(0xFFB0BEC5);
                rf.set(ex - 1.6f, ey - 1.6f, ex + 1.6f, ey + 1.6f);
                c.drawRect(ex + px * 0.2f - 1.5f, ey + py * 0.2f - 1.5f, ex + px * 0.2f + 1.5f, ey + py * 0.2f + 1.5f, p);
                break;
            case 10: // Nudelholz
                p.setColor(0xFFD7B98A);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f);
                c.drawLine(h[0] + dx, h[1] + dy, ex, ey, p);
                p.setStyle(Paint.Style.FILL);
                break;
            default: // Speer
                p.setColor(0xFFCFD8DC);
                path.reset();
                path.moveTo(ex + dx * 2.5f, ey + dy * 2.5f);
                path.lineTo(ex + px * 0.9f, ey + py * 0.9f);
                path.lineTo(ex - px * 0.9f, ey - py * 0.9f);
                path.close();
                c.drawPath(path, p);
        }
    }

    private void chips(Canvas c, float x, float y, float f, int col, int seed) {
        p.setColor(col);
        for (int k = 0; k < 5; k++) {
            float vx = ((k * 37 + seed) % 7 - 3) * 1.6f, vy = 6f + ((k * 13 + seed) % 5);
            float px = x + vx * f * 2, py = y - vy * f + 14 * f * f;
            c.drawCircle(px, py, 0.7f, p);
        }
    }

    void settler(Canvas c, World w, World.Settler s, float t) {
        float x = nx(s.node), y = ny(w, s.node);
        boolean walk = s.next >= 0;
        if (walk) {
            x += (nx(s.next) - x) * s.prog;
            y += (ny(w, s.next) - y) * s.prog;
        }
        if (!w.exploredOf(me)[s.node]) return;
        int face = walk ? (nx(s.next) < nx(s.node) ? -1 : 1) : ((s.id & 1) == 0 ? 1 : -1);
        float legs = walk ? (float) Math.sin((s.prog + s.id * 0.37f) * Math.PI * 2) : 0;
        int shirt = SHIRT[s.job];
        if (s.helper) shirt = 0xFFEF6C00;
        if (s.job == World.J_SOLDIER) shirt = World.P_COLORS[s.player];
        else if (s.player == 1 && s.job == World.J_CARRIER) shirt = 0xFF8E24AA;
        int hat = s.job == World.J_SOLDIER ? 0xFF9E9E9E : s.job == World.J_BUILDER ? 0xFFF9A825 : 0xFF5D4037;
        float tp = s.tempo();
        int car = s.carried();

        if (s.job == World.J_SOLDIER) {
            soldier(c, w, s, x, y, face, legs, walk);
            return;
        }
        if (s.inBoat) {
            boolean net = s.state == World.S_WORK && !walk;
            boat(c, x, y, face, t, s.id, true, net, net ? s.workPhase : (walk ? s.prog : 0));
            return;
        }

        // ---- arbeitslos und traurig auf dem Heimweg ----
        if (s.sad) {
            float sob = (float) Math.sin(t * 15 + s.id);
            beginFigure(c, x, y, face, shirt, hat, legs * 0.5f, 0, 16 + sob * 3, 25, false);
            arm(c, 166, shirt);
            endFigure(c);
            for (int k = 0; k < 4; k++) {
                float f = frac(t * 1.5f + k / 4f + s.id * 0.13f);
                float side = (k & 1) == 0 ? 1 : -0.6f;
                float tx = x + face * side * (2.2f + f * 2.4f), ty = y - 14f + f * 6f + f * f * 4f;
                int a = (int) (255 * (1 - f * f));
                p.setColor((a << 24) | 0x2F8FEA);
                c.drawCircle(tx, ty, 1.0f - f * 0.3f, p);
                path.reset();
                path.moveTo(tx - 0.8f, ty - 0.3f);
                path.lineTo(tx, ty - 2f);
                path.lineTo(tx + 0.8f, ty - 0.3f);
                path.close();
                c.drawPath(path, p);
                p.setColor((a << 24) | 0xFFFFFF);
                c.drawCircle(tx - 0.3f, ty - 0.3f, 0.3f, p);
            }
            return;
        }

        // ---- bei der Arbeit ----
        if (s.state == World.S_WORK && !walk) {
            float ph = s.workPhase;
            if (s.job == World.J_WOODCUTTER) {
                float f = frac(ph / 0.75f);
                float a = f < 0.65f ? 60 + 145 * (f / 0.65f) : Math.max(80, 205 - 125 * ((f - 0.65f) / 0.12f));
                float lean = f < 0.65f ? -6 * f : 12;
                beginFigure(c, x - 9, y, 1, shirt, hat, 0, 0, lean, a - 20, false);
                float[] h = arm(c, a, shirt);
                tool(c, h, a, 0);
                endFigure(c);
                if (f > 0.77f) chips(c, x - 1, y - 6, (f - 0.77f) / 0.23f, 0xFFE0C08A, s.id);
                return;
            }
            if (s.job == World.J_MASON) {
                float f = frac(ph / 0.85f);
                float a = f < 0.6f ? 40 + 150 * (f / 0.6f) : Math.max(25, 190 - 165 * ((f - 0.6f) / 0.15f));
                beginFigure(c, x - 11, y, 1, shirt, hat, 0, 0, f < 0.6f ? -4 : 16, a, false);
                float[] h = arm(c, a, shirt);
                tool(c, h, a, 1);
                endFigure(c);
                if (f > 0.75f) {
                    float g = (f - 0.75f) / 0.25f;
                    chips(c, x - 3, y - 3, g, 0xFFBDBDBD, s.id);
                    if (g < 0.3f) {
                        p.setColor(0xFFFFF59D);
                        c.drawCircle(x - 3, y - 4, 1.4f * (1 - g * 3), p);
                    }
                }
                return;
            }
            if (s.job == World.J_FISHER) {
                int wn = s.aux;
                int fc = wn >= 0 && wn < World.N && nx(wn) < x ? -1 : 1;
                float bob = (float) Math.sin(t * 3 + s.id) * 0.8f;
                beginFigure(c, x, y, fc, shirt, hat, 0, 0, 6, 70, false);
                float[] h = arm(c, 125, shirt);
                tool(c, h, 150, 5);
                endFigure(c);
                // Schnur und Schwimmer
                float tipX = x + fc * 9.5f, tipY = y - 19;
                float wx = wn >= 0 && wn < World.N ? nx(wn) : x + fc * 14, wy = wn >= 0 && wn < World.N ? ny(w, wn) + 1 : y;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.35f);
                p.setColor(0xCCFFFFFF);
                c.drawLine(tipX, tipY, wx, wy + bob, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFE53935);
                c.drawCircle(wx, wy + bob, 0.9f, p);
                if (frac(t * 0.5f + s.id * 0.3f) < 0.15f) {
                    p.setColor(0x88FFFFFF);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(0.4f);
                    float rr = 1.5f + frac(t * 0.5f + s.id * 0.3f) * 20;
                    rf.set(wx - rr, wy + bob - rr * 0.4f, wx + rr, wy + bob + rr * 0.4f);
                    c.drawOval(rf, p);
                    p.setStyle(Paint.Style.FILL);
                }
                return;
            }
            if (s.job == World.J_HUNTER) {
                World.Animal tgt = null;
                if (w.animals != null) for (World.Animal an : w.animals) if (an.id == s.aux) tgt = an;
                int fc = tgt != null && nx(tgt.node) < x ? -1 : 1;
                float pull = Math.min(1f, ph / 1.3f);
                beginFigure(c, x - fc * 6, y, fc, shirt, hat, 0, 0, 0, 90 - 20 * pull, false);
                float[] h = arm(c, 92, shirt);
                tool(c, h, 92, 7);
                endFigure(c);
                if (ph >= 1.3f && ph < 1.7f && tgt != null) {
                    float f2 = (ph - 1.3f) / 0.4f;
                    float sx = x - fc * 6 + fc * 5, sy = y - 9;
                    float ax = sx + (nx(tgt.node) - sx) * f2, ay = sy + (ny(w, tgt.node) - 6 - sy) * f2;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(0.6f);
                    p.setColor(0xFF5D4037);
                    c.drawLine(ax - fc * 3, ay, ax, ay, p);
                    p.setStyle(Paint.Style.FILL);
                }
                return;
            }
            if (s.job == World.J_FARMER) {
                if (s.aux == 1) {
                    float f = frac(ph / 0.9f);
                    float a = f < 0.55f ? 150 - 60 * (f / 0.55f) : 90 - 70 * ((f - 0.55f) / 0.45f);
                    beginFigure(c, x - 8, y + 1, 1, shirt, 0xFFE0C050, 0, 0, 14, a - 10, false);
                    float[] h = arm(c, a, shirt);
                    tool(c, h, a + 10, 6);
                    endFigure(c);
                } else {
                    float f = frac(ph / 1.0f);
                    beginFigure(c, x - 7, y + 1, 1, shirt, 0xFFE0C050, (float) Math.sin(f * Math.PI * 2) * 0.4f, 0, 6, 30, false);
                    arm(c, 40 + 70 * f, shirt);
                    endFigure(c);
                    if (f > 0.4f) chips(c, x - 2, y - 7, (f - 0.4f) / 0.6f, 0xFFE0C050, s.id);
                }
                return;
            }
            if (s.job == World.J_SCOUT) {
                int fc = ((int) (t / 0.9f + s.id) & 1) == 0 ? 1 : -1;
                beginFigure(c, x, y, fc, shirt, 0xFF00695C, 0, 0, -3, 10, false);
                arm(c, 168, shirt);
                endFigure(c);
                return;
            }
            if (s.job == World.J_GEOLOGIST) {
                float f = frac(ph / 0.6f);
                float a = f < 0.6f ? 70 + 80 * (f / 0.6f) : 150 - 110 * ((f - 0.6f) / 0.4f);
                beginFigure(c, x - 5, y, 1, shirt, 0xFF8E6A3A, 0, 1, 25, 50, false);
                float[] h = arm(c, a, shirt);
                tool(c, h, a, 3);
                endFigure(c);
                if (f > 0.85f) chips(c, x + 1, y - 1, (f - 0.85f) / 0.15f, 0xFF9E9E9E, s.id);
                return;
            }
            if (s.job == World.J_FORESTER) {
                float f = frac(ph / 1.1f);
                float a = 45 + 45 * (float) Math.sin(f * Math.PI * 2);
                boolean planting = s.timer < 0.9f / tp;
                beginFigure(c, x - 7, y, 1, shirt, hat, 0, 1, 22, 40, false);
                float[] h = arm(c, planting ? 60 : a, shirt);
                if (planting) tree(c, h[0] + 1, h[1] + 3, 1, 0, t, 7);
                else tool(c, h, a + 20, 2);
                endFigure(c);
                p.setColor(0xFF6D4C2F);
                rf.set(x - 3, y - 1, x + 3, y + 1.5f);
                c.drawOval(rf, p);
                if (!planting && f > 0.5f) chips(c, x - 1, y - 1, (f - 0.5f) * 2, 0xFF7B5A3A, s.id);
                return;
            }
        }
        // ---- Bauarbeiter ----
        if (s.state == World.S_BUILD && !walk) {
            World.Building b = w.buildings.get(s.home);
            boolean busy = false;
            if (b != null) {
                int have = 0;
                for (int g : b.got) have += g;
                busy = b.used < have;
            }
            if (busy) {
                float ph = s.workPhase;
                int k = (int) (ph / 2.5f) % BUILD_SPOTS.length;
                int prev = (k + BUILD_SPOTS.length - 1) % BUILD_SPOTS.length;
                float f = frac(ph / 2.5f);
                float bx, by;
                if (f < 0.22f) {
                    float g = f / 0.22f;
                    bx = BUILD_SPOTS[prev][0] + (BUILD_SPOTS[k][0] - BUILD_SPOTS[prev][0]) * g;
                    by = BUILD_SPOTS[prev][1] + (BUILD_SPOTS[k][1] - BUILD_SPOTS[prev][1]) * g;
                    int fc = BUILD_SPOTS[k][0] > BUILD_SPOTS[prev][0] ? 1 : -1;
                    beginFigure(c, x + bx, y + by, fc, shirt, hat, (float) Math.sin(g * Math.PI * 4), 0, 0, -20, false);
                    float[] h = arm(c, 150, shirt);
                    tool(c, h, 160, 3);
                    endFigure(c);
                } else {
                    bx = BUILD_SPOTS[k][0];
                    by = BUILD_SPOTS[k][1];
                    int fc = bx > 0 ? -1 : 1;
                    float hf = frac(ph / 0.5f);
                    float a = hf < 0.7f ? 70 + 80 * (hf / 0.7f) : 150 - 95 * ((hf - 0.7f) / 0.3f);
                    beginFigure(c, x + bx, y + by, fc, shirt, hat, 0, k == 2 ? 1 : 0, 14, 60, false);
                    float[] h = arm(c, a, shirt);
                    tool(c, h, a, 3);
                    endFigure(c);
                }
                return;
            }
            // wartet auf Material: Arme verschränkt, schaut sich um
            int fc = ((int) (t / 1.7f + s.id) & 1) == 0 ? 1 : -1;
            beginFigure(c, x - 13, y + 1, fc, shirt, hat, 0, 0, 0, 60, false);
            arm(c, 65, shirt);
            endFigure(c);
            return;
        }

        // ---- Träger bückt sich an der Fahne ----
        if (s.pause > 0 && s.job == World.J_CARRIER) {
            float bend = Math.min(1f, (0.4f - Math.abs(s.pause - 0.2f)) / 0.2f + 0.3f);
            beginFigure(c, x, y, face, shirt, hat, 0, 0, 45 * bend, 20, false);
            float[] h = arm(c, 15, shirt);
            if (car >= 0) ware(c, car, h[0] + 1, h[1] + 1, 0.85f);
            endFigure(c);
            return;
        }

        // ---- wartender Träger ----
        if (!walk && s.job == World.J_CARRIER && s.state == World.C_IDLE) {
            float id = s.idle;
            if (id > 9f) {
                beginFigure(c, x - 1, y, face, shirt, hat, 0, 0, -4, 30, true);
                arm(c, 40, shirt);
                endFigure(c);
                return;
            }
            int fc = ((int) (t / 1.8f * tp + s.id) & 1) == 0 ? 1 : -1;
            boolean scratch = id > 3.5f && frac((id - 3.5f) / 5f) < 0.45f;
            beginFigure(c, x, y, scratch ? face : fc, shirt, hat, 0, 0, 0, 5, false);
            if (scratch) {
                float a = 165 + 12 * (float) Math.sin(t * 16);
                arm(c, a, shirt);
            } else arm(c, 8, shirt);
            endFigure(c);
            if (scratch && frac((id - 3.5f) / 5f) > 0.15f) question(c, x + 1, y - 21, t);
            return;
        }

        // ---- gehen ----
        float armSwing = 28 * legs;
        float lean = car >= 0 ? 5 : 0;
        if (s.job == World.J_SOLDIER) {
            beginFigure(c, x, y, face, shirt, hat, legs, 0, 0, -armSwing, false);
            float[] h = arm(c, 30, shirt);
            tool(c, h, 175, 4);
            endFigure(c);
            return;
        }
        if (car >= 0 && s.job == World.J_CARRIER) {
            // Ware über dem Kopf
            beginFigure(c, x, y, face, shirt, hat, legs, 0, lean, 165, false);
            arm(c, 165, shirt);
            endFigure(c);
            ware(c, car, x, y - 17.5f + (float) Math.abs(legs) * 0.4f, 0.85f);
            return;
        }
        if (car >= 0) {
            // Arbeiter trägt seine Ware auf der Schulter
            beginFigure(c, x, y, face, shirt, hat, legs, 0, lean, 150, false);
            arm(c, 160, shirt);
            endFigure(c);
            ware(c, car, x + face * 1.5f, y - 13.5f, 0.85f);
            return;
        }
        int toolType = s.job == World.J_WOODCUTTER ? 0 : s.job == World.J_MASON ? 1 : s.job == World.J_FORESTER ? 2
                : s.job == World.J_BUILDER || s.job == World.J_GEOLOGIST ? 3 : s.job == World.J_FISHER ? 5
                : s.job == World.J_FARMER ? 6 : s.job == World.J_HUNTER ? 7 : -1;
        if (s.job == World.J_FARMER) hat = 0xFFE0C050;
        if (s.job == World.J_SCOUT) hat = 0xFF00695C;
        beginFigure(c, x, y, face, shirt, hat, legs, 0, 0, -armSwing, false);
        if (toolType >= 0) {
            float[] h = arm(c, 150, shirt);
            tool(c, h, 200, toolType);
        } else arm(c, armSwing, shirt);
        endFigure(c);
    }

    /** schwebendes Fragezeichen */
    public void question(Canvas c, float x, float y, float t) {
        float bob = (float) Math.sin(t * 3) * 1.2f;
        p.setColor(0xEEFFFFFF);
        c.drawCircle(x, y + bob, 4.2f, p);
        p.setColor(0xFF3B2A1A);
        p.setTextSize(7.5f);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("?", x, y + bob + 2.7f, p);
        p.setFakeBoldText(false);
    }

    /** Handwerker, der sich am Kopf kratzt (Titelbild). */
    public void scratcher(Canvas c, float x, float y, float t) {
        beginFigure(c, x, y, 1, SHIRT[World.J_BUILDER], 0xFFF9A825, 0, 0, -3, 10, false);
        float[] h = arm(c, 165 + 14 * (float) Math.sin(t * 14), SHIRT[World.J_BUILDER]);
        endFigure(c);
        tool(c, new float[]{x - 3, y - 4}, 200, 3);
        if (h != null) question(c, x + 2, y - 23, t);
    }

    // ---------- Felder, Schilder, Tiere ----------

    void field(Canvas c, float x, float y, int stage, int n, float t) {
        p.setColor(0xFF7B5A36);
        rf.set(x - 12, y - 5, x + 12, y + 5);
        c.drawOval(rf, p);
        p.setColor(0xFF6A4B2C);
        for (int i = -2; i <= 2; i++) c.drawRect(x - 10 + Math.abs(i) * 1.5f, y + i * 1.8f - 0.3f, x + 10 - Math.abs(i) * 1.5f, y + i * 1.8f + 0.3f, p);
        if (stage == 0) return;
        float hgt = stage == 1 ? 2f : stage == 2 ? 4.5f : 6.5f;
        int col = stage == 3 ? 0xFFE3C456 : stage == 2 ? 0xFF8DB848 : 0xFF6FA845;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f);
        for (int i = -2; i <= 2; i++) {
            float half = 9 - Math.abs(i) * 1.5f;
            for (float xx = -half; xx <= half; xx += 2.2f) {
                float sway = (float) Math.sin(t * 2 + xx * 0.4f + n) * 0.5f * (stage / 3f);
                p.setColor(col);
                float bx = x + xx, by = y + i * 1.8f;
                c.drawLine(bx, by, bx + sway, by - hgt, p);
                if (stage == 3) {
                    p.setColor(0xFFF0D878);
                    c.drawLine(bx + sway, by - hgt, bx + sway * 1.3f, by - hgt - 1.6f, p);
                }
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    void geoSign(Canvas c, float x, float y, int kind) {
        p.setColor(0xFF6D4C41);
        c.drawRect(x - 0.5f, y - 10, x + 0.5f, y, p);
        int col = kind == 1 ? 0xFF212121 : kind == 2 ? 0xFFB5651D : kind == 3 ? 0xFFFFD54F : kind == 4 ? 0xFF9E9E9E : 0xFFFFFFFF;
        p.setColor(0xFF4E342E);
        c.drawRect(x - 3.6f, y - 13.6f, x + 3.6f, y - 7.4f, p);
        p.setColor(col);
        c.drawRect(x - 3f, y - 13f, x + 3f, y - 8f, p);
        if (kind == 9) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.8f);
            p.setColor(0xFFC62828);
            c.drawLine(x - 2, y - 12.2f, x + 2, y - 8.8f, p);
            c.drawLine(x + 2, y - 12.2f, x - 2, y - 8.8f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    void animal(Canvas c, World w, World.Animal a, float t) {
        if (!w.exploredOf(me)[a.node]) return;
        float x = nx(a.node), y = ny(w, a.node);
        boolean walk = a.next >= 0;
        int face = 1;
        if (walk) {
            x += (nx(a.next) - x) * a.prog;
            y += (ny(w, a.next) - y) * a.prog;
            face = nx(a.next) < nx(a.node) ? -1 : 1;
        } else face = (a.id & 1) == 0 ? 1 : -1;
        float leg = walk ? (float) Math.sin(a.prog * Math.PI * 4) * 1.5f : 0;
        c.save();
        c.translate(x, y);
        if (face < 0) c.scale(-1, 1);
        p.setColor(0x38000000);
        rf.set(-5, -1, 5, 1.4f);
        c.drawOval(rf, p);
        if (a.kind == 0) {
            // Reh
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.9f);
            p.setColor(0xFF6D4C2F);
            c.drawLine(-3, -5, -3 + leg, 0, p);
            c.drawLine(-1.5f, -5, -1.5f - leg, 0, p);
            c.drawLine(2.5f, -5, 2.5f - leg, 0, p);
            c.drawLine(3.8f, -5, 3.8f + leg, 0, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFA0703F);
            rf.set(-4.5f, -8, 5, -4.2f);
            c.drawOval(rf, p);
            p.setColor(0xFFF5E6D0);
            c.drawCircle(-4.4f, -6.6f, 0.9f, p);
            boolean graze = !walk && frac(t * 0.2f + a.id * 0.37f) < 0.4f;
            float hx = graze ? 6 : 6.2f, hy = graze ? -3 : -11;
            p.setColor(0xFFA0703F);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            c.drawLine(4, -7, hx, hy + 1, p);
            p.setStyle(Paint.Style.FILL);
            rf.set(hx - 1.3f, hy - 0.8f, hx + 2.6f, hy + 1.6f);
            c.drawOval(rf, p);
            p.setColor(0xFF3B2A1A);
            c.drawCircle(hx + 2.3f, hy + 0.6f, 0.4f, p);
            p.setColor(0xFF7A5230);
            c.drawCircle(hx - 0.6f, hy - 1.3f, 0.6f, p);
        } else {
            // Hase
            float hop = walk ? Math.abs((float) Math.sin(a.prog * Math.PI * 3)) * 2.5f : 0;
            p.setColor(0xFF9C8A74);
            rf.set(-2.8f, -4.2f - hop, 2.2f, -0.3f - hop);
            c.drawOval(rf, p);
            c.drawCircle(2.4f, -4.4f - hop, 1.5f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.9f);
            c.drawLine(2, -5.5f - hop, 1.4f, -8.5f - hop, p);
            c.drawLine(2.8f, -5.5f - hop, 3f, -8.4f - hop, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFF5F0E6);
            c.drawCircle(-2.7f, -2.6f - hop, 0.9f, p);
        }
        c.restore();
    }
}
