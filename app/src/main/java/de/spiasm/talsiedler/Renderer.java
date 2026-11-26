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
    private final ArrayList<World.Settler>[] rows = new ArrayList[World.H + 2];

    public Renderer() {
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

    private int baseColor(World w, int t, int h, Random r) {
        float v = 0.93f + r.nextFloat() * 0.12f;
        switch (t) {
            case World.T_WATER: return shade(0xFF2F6DA8, 0.95f + r.nextFloat() * 0.08f);
            case World.T_MOUNTAIN:
                if (h >= 8) return shade(0xFFE8ECEF, v);
                return shade(mix(0xFF8C7A62, 0xFF9E9488, r.nextFloat()), v);
            case World.T_SAND: return shade(0xFFD9C48A, v);
            default: return shade(mix(0xFF6FA845, 0xFF83B34E, r.nextFloat()), v);
        }
    }

    private int triTerrain(World w, int a, int b, int c) {
        int ta = w.terrain[a], tb = w.terrain[b], tc = w.terrain[c];
        if (ta == tb || ta == tc) return ta;
        if (tb == tc) return tb;
        return ta;
    }

    public void buildTerrain(World w) {
        int bw = (int) worldW(), bh = (int) worldH();
        if (terrainBmp == null) terrainBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(terrainBmp);
        c.drawColor(0xFF000000);
        Random r = new Random(w.seed * 31 + 7);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setStrokeWidth(1.2f);
        for (int y = 0; y < World.H - 1; y++) {
            for (int x = 0; x < World.W; x++) {
                int n = y * World.W + x;
                int e = World.nb(n, 0), se = World.nb(n, 1), sw = World.nb(n, 2);
                if (se >= 0 && sw >= 0) tri(c, tp, w, n, se, sw, r);
                if (e >= 0 && se >= 0) tri(c, tp, w, n, e, se, r);
            }
        }
        // Gras-Büschel und Steinchen als Struktur
        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < World.N * 3; i++) {
            int n = r.nextInt(World.N);
            float x = nx(n) + (r.nextFloat() - 0.5f) * TW, y = ny(w, n) + (r.nextFloat() - 0.5f) * RH;
            int t = w.terrain[n];
            if (t == World.T_GRASS) {
                dp.setColor(r.nextBoolean() ? 0x553B6E24 : 0x44A9D46A);
                c.drawCircle(x, y, 1.2f + r.nextFloat() * 1.3f, dp);
            } else if (t == World.T_MOUNTAIN) {
                dp.setColor(r.nextBoolean() ? 0x55584A3A : 0x40FFFFFF);
                c.drawCircle(x, y, 0.8f + r.nextFloat() * 1.6f, dp);
            } else if (t == World.T_WATER) {
                dp.setColor(0x305FA3DE);
                c.drawRect(x - 4, y, x + 4, y + 1, dp);
            } else {
                dp.setColor(0x40A08850);
                c.drawCircle(x, y, 1f, dp);
            }
        }
        terrainOf = w;
    }

    private void tri(Canvas c, Paint tp, World w, int a, int b, int d, Random r) {
        int t = triTerrain(w, a, b, d);
        int ha = w.height[a], hb = w.height[b], hd = w.height[d];
        int col = baseColor(w, t, Math.max(ha, Math.max(hb, hd)), r);
        // Licht kommt von links oben
        float ax = nx(a), ay = ny(w, a), bx = nx(b), by = ny(w, b), dx = nx(d), dy = ny(w, d);
        float slopeX = ((hb + hd) / 2f - ha) * (bx > ax ? 1 : -1);
        float slopeY = (a / World.W == b / World.W) ? (hd - (ha + hb) / 2f) : ((hb + hd) / 2f - ha);
        float f = 1f + slopeX * 0.07f - slopeY * 0.09f;
        if (t == World.T_WATER) f = 1f;
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
        if (fogOf != w || fogVer != w.exploredVer) buildFog(w);
        c.drawColor(0xFF000000);
        c.save();
        c.scale(zoom, zoom);
        c.translate(-camX, -camY);
        c.drawBitmap(terrainBmp, 0, 0, bmpPaint);

        int y0 = Math.max(0, (int) ((camY - MY) / RH) - 2);
        int y1 = Math.min(World.H - 1, (int) ((camY + viewH / zoom - MY) / RH) + 4);
        int x0 = Math.max(0, (int) ((camX - MX) / TW) - 2);
        int x1 = Math.min(World.W - 1, (int) ((camX + viewW / zoom - MX) / TW) + 2);

        // Grenze
        p.setStyle(Paint.Style.FILL);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                int n = y * World.W + x;
                if (!w.owned[n]) continue;
                boolean edge = false;
                for (int d = 0; d < 6 && !edge; d++) {
                    int m = World.nb(n, d);
                    if (m < 0 || !w.owned[m]) edge = true;
                }
                if (edge) borderStone(c, nx(n), ny(w, n));
            }

        // Wege
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (World.Road r : w.roads.values()) drawRoad(c, w, r.path, r.carrier < 0, r.level);
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
                if (!w.explored[n]) continue;
                float px = nx(n), py = ny(w, n);
                switch (w.obj[n]) {
                    case World.O_TREE: {
                        float shake = 0;
                        for (int i = 0; i < nChop; i++) if (chopping[i] == n) shake = (float) Math.sin(t * 40) * 0.8f;
                        tree(c, px + shake, py, treeKind(w, n), w.objVal[n], t, n);
                        break;
                    }
                    case World.O_STONE: stones(c, px, py, w.objVal[n], n); break;
                    case World.O_FLAG: flag(c, w, w.flagAt(n), px, py, t); break;
                    case World.O_BUILDING: building(c, w, w.buildings.get(w.objId[n]), px, py, t); break;
                }
                if (showSpots && w.owned[n] && w.obj[n] == World.O_NONE) spot(c, w, n, px, py);
            }
            for (float[] fl : w.falls) if ((int) fl[0] / World.W == y) falling(c, w, fl);
            for (World.Settler s : rows[y]) settler(c, w, s, t);
        }
        if (showUtil) {
            for (World.Building b : w.buildings.values()) {
                int by = b.node / World.W;
                if (by < y0 || by > y1 || !b.done || b.id == w.hq || b.type >= World.B_GUARD) continue;
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
        for (int n = 0; n < World.N; n++) {
            if (w.explored[n]) continue;
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
            if (!w.explored[n] || w.owned[n]) continue;
            c.drawCircle(nx(n) / FOG, ny(w, n) / FOG, r, fp);
        }
        fogOf = w;
        fogVer = w.exploredVer;
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
        for (int n = 0; n < World.N; n++) {
            if (!w.explored[n]) continue;
            int x = n % World.W, y = n / World.W;
            int col;
            switch (w.terrain[n]) {
                case World.T_WATER: col = 0xFF2F6DA8; break;
                case World.T_MOUNTAIN: col = w.height[n] >= 8 ? 0xFFE0E4E8 : 0xFF8C7A62; break;
                case World.T_SAND: col = 0xFFD9C48A; break;
                default: col = 0xFF6FA845;
            }
            if (w.obj[n] == World.O_TREE) col = 0xFF2F5E25;
            else if (w.obj[n] == World.O_STONE) col = 0xFFA5A19A;
            if (w.owned[n]) col = mix(col, 0xFF3D7BFF, 0.22f);
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
        if (miniB != null && miniBOf == w && miniBVer == w.exploredVer) return miniB;
        int x0 = World.W, x1 = 0, y0 = World.H, y1 = 0;
        for (int n = 0; n < World.N; n++) {
            if (!w.explored[n]) continue;
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
        miniBVer = w.exploredVer;
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

    private void borderStone(Canvas c, float x, float y) {
        p.setColor(0x66000000);
        c.drawCircle(x + 1, y + 1, 2.2f, p);
        p.setColor(0xFF2F64D0);
        c.drawCircle(x, y - 1, 2.2f, p);
        p.setColor(0xFFBFD4FF);
        c.drawCircle(x - 0.6f, y - 1.8f, 0.8f, p);
    }

    private void spot(Canvas c, World w, int n, float x, float y) {
        int s = w.buildSize(n);
        if (s >= 2) {
            miniHouse(c, x, y, 1.15f, 0xFFFF9800);
        } else if (s == 1) {
            miniHouse(c, x, y, 0.85f, 0xFFFFEB3B);
        } else if (w.roadDirs[n] == 0 && w.canFlag(n)) {
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
        if (type == World.WARE_LOG) {
            p.setColor(0xFF7A4A22);
            rf.set(x - 4 * s, y - 1.6f * s, x + 4 * s, y + 1.6f * s);
            c.drawRoundRect(rf, 1.6f * s, 1.6f * s, p);
            p.setColor(0xFFD9A86A);
            c.drawCircle(x + 3.6f * s, y, 1.4f * s, p);
        } else if (type == World.WARE_BOARD) {
            p.setColor(0xFFB98B4E);
            c.drawRect(x - 4.5f * s, y - 1.2f * s, x + 4.5f * s, y + 1.4f * s, p);
            p.setColor(0xFFEBC98A);
            c.drawRect(x - 4.5f * s, y - 1.2f * s, x + 4.5f * s, y - 0.2f * s, p);
        } else {
            p.setColor(0xFF8A8680);
            c.drawCircle(x, y, 2.6f * s, p);
            p.setColor(0xFFC4C0BA);
            c.drawCircle(x - 0.8f * s, y - 0.8f * s, 1f * s, p);
        }
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
        p.setColor(0xFF1E5BC6);
        c.drawPath(path, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(x + 0.7f, y - 15.7f, x + 8.5f, y - 14.7f + wv * 0.4f, p);
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

    private void banner(Canvas c, float x, float y, float t) {
        p.setColor(0xFF3E2A1C);
        c.drawRect(x - 0.6f, y - 12, x + 0.6f, y, p);
        float wv = (float) Math.sin(t * 5 + x);
        path.reset();
        path.moveTo(x + 0.6f, y - 12);
        path.lineTo(x + 8, y - 11 + wv);
        path.lineTo(x + 0.6f, y - 7.5f);
        path.close();
        p.setColor(0xFF1E5BC6);
        c.drawPath(path, p);
    }

    public void buildingSprite(Canvas c, int type, float x, float y, float t) {
        switch (type) {
            case World.B_HQ:
                tower(c, x - 17, y - 2, 13, 34, 0xFFB8AE9C);
                house(c, x + 2, y + 2, 30, 20, 13, 0xFFD8CBB0, 0xFFB5452E);
                tower(c, x + 22, y - 1, 11, 28, 0xFFB0A692);
                banner(c, x - 17, y - 38, t);
                banner(c, x + 22, y - 31, t);
                break;
            case World.B_WOOD:
                house(c, x, y, 18, 11, 9, 0xFF9B6B3E, 0xFF5B3B22);
                ware(c, World.WARE_LOG, x - 13, y - 1, 0.9f);
                ware(c, World.WARE_LOG, x - 13, y - 4, 0.9f);
                p.setColor(0xFF777777);
                c.drawRect(x + 4, y - 9, x + 7, y - 6, p);
                break;
            case World.B_FOREST:
                house(c, x, y, 18, 11, 9, 0xFFC9A574, 0xFF3F7D34);
                tree(c, x - 13, y, 1, 1, t, 3);
                break;
            case World.B_QUARRY:
                house(c, x, y, 18, 11, 8, 0xFFA8A49C, 0xFF6D5D50);
                stones(c, x - 14, y + 1, 2, 1);
                break;
            case World.B_SAW: {
                house(c, x, y, 28, 15, 11, 0xFFB98A55, 0xFFA13D2A);
                p.setColor(0xFFD0D0D0);
                c.drawCircle(x - 7, y - 9, 3.5f, p);
                p.setColor(0xFF7A7A7A);
                c.drawCircle(x - 7, y - 9, 1.2f, p);
                for (int i = 0; i < 3; i++) ware(c, World.WARE_BOARD, x - 20, y - 1 - i * 2.4f, 0.9f);
                break;
            }
            case World.B_GUARD:
                tower(c, x, y, 13, 22, 0xFFB5AB98);
                banner(c, x, y - 25, t);
                break;
            case World.B_TOWER:
                tower(c, x, y, 18, 34, 0xFFA89E8A);
                banner(c, x, y - 37, t);
                break;
        }
    }

    void building(Canvas c, World w, World.Building b, float x, float y, float t) {
        if (b == null) return;
        if (b.done) {
            buildingSprite(c, b.type, x, y, t);
            World.Settler wk = w.settlers.get(b.worker);
            if (b.type == World.B_SAW && wk != null && wk.state == World.S_SAW) {
                // drehendes Sägeblatt und Sägemehl
                float a = t * 14f;
                p.setColor(0xFFE8E8E8);
                c.drawCircle(x - 7, y - 9, 3.8f, p);
                p.setColor(0xFF707070);
                for (int k = 0; k < 6; k++) {
                    float aa = a + k * 1.047f;
                    c.drawCircle(x - 7 + (float) Math.cos(aa) * 3.4f, y - 9 + (float) Math.sin(aa) * 3.4f, 0.7f, p);
                }
                c.drawCircle(x - 7, y - 9, 1.2f, p);
                for (int k = 0; k < 5; k++) {
                    float f = ((t * 1.3f + k * 0.2f) % 1f);
                    p.setColor(0xCCE7CF9A);
                    c.drawCircle(x - 9 - f * 6 + (float) Math.sin(k * 3 + t * 5) * 1.5f, y - 7 + f * 7, 0.8f, p);
                }
            } else if (b.type == World.B_WOOD || b.type == World.B_QUARRY || b.type == World.B_SAW || b.type == World.B_FOREST) {
                // Rauch aus dem Schornstein, wenn gearbeitet wird
                if (wk != null && !b.stopped && !b.problem) {
                    for (int k = 0; k < 3; k++) {
                        float f = ((t * 0.35f + k / 3f) % 1f);
                        p.setColor(((int) (0x70 * (1 - f)) << 24) | 0xDDDDDD);
                        c.drawCircle(x + 6 + f * 5 + (float) Math.sin(t + k) * 1.5f, y - 22 - f * 14, 1.5f + f * 2.5f, p);
                    }
                }
            }
            if (b.stopped) {
                p.setColor(0xFFC62828);
                c.drawCircle(x + 9, y - 16, 4.2f, p);
                p.setColor(0xFFFFFFFF);
                c.drawRect(x + 6.5f, y - 16.7f, x + 11.5f, y - 15.3f, p);
            }
            if (b.type >= World.B_GUARD && !b.occupied) {
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
            c.clipRect(x - 40, y + 8 - (48 * pr), x + 40, y + 8);
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
        if (!w.explored[n]) return;
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

    private static final int[] SHIRT = {0xFFD84315, 0xFFF9A825, 0xFF6D4C41, 0xFF2E7D32, 0xFF757575, 0xFFAD7A3D, 0xFF1E5BC6};
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
        if (!w.explored[s.node]) return;
        int face = walk ? (nx(s.next) < nx(s.node) ? -1 : 1) : ((s.id & 1) == 0 ? 1 : -1);
        float legs = walk ? (float) Math.sin((s.prog + s.id * 0.37f) * Math.PI * 2) : 0;
        int shirt = SHIRT[s.job];
        if (s.helper) shirt = 0xFFEF6C00;
        int hat = s.job == World.J_SOLDIER ? 0xFF9E9E9E : s.job == World.J_BUILDER ? 0xFFF9A825 : 0xFF5D4037;
        float tp = s.tempo();
        int car = s.carried();

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
                : s.job == World.J_BUILDER ? 3 : -1;
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
}
