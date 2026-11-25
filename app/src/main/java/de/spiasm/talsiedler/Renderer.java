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
    public boolean showSpots;
    public int selected = -1;
    public int roadFrom = -1;

    private Bitmap terrainBmp;
    private World terrainOf;
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
        c.drawColor(0xFF24577F);
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
        c.drawColor(0xFF1F4C70);
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
        for (World.Road r : w.roads.values()) drawRoad(c, w, r.path, r.carrier < 0);
        for (World.Building b : w.buildings.values()) {
            World.Flag f = w.flags.get(b.flag);
            if (f != null) drawRoad(c, w, new int[]{b.node, f.node}, false);
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
        for (World.Settler s : w.settlers.values()) {
            if (!s.visible) continue;
            int row = s.node / World.W;
            if (s.next >= 0) row = Math.max(row, s.next / World.W);
            if (row >= y0 - 1 && row <= y1 + 1) rows[row].add(s);
        }

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int n = y * World.W + x;
                float px = nx(n), py = ny(w, n);
                switch (w.obj[n]) {
                    case World.O_TREE: tree(c, px, py, treeKind(w, n), w.objVal[n], t, n); break;
                    case World.O_STONE: stones(c, px, py, w.objVal[n], n); break;
                    case World.O_FLAG: flag(c, w, w.flagAt(n), px, py, t); break;
                    case World.O_BUILDING: building(c, w, w.buildings.get(w.objId[n]), px, py, t); break;
                }
                if (showSpots && w.owned[n] && w.obj[n] == World.O_NONE) spot(c, w, n, px, py);
            }
            for (World.Settler s : rows[y]) settler(c, w, s, t);
        }
        c.restore();
    }

    private static int treeKind(World w, int n) {
        int h = n * 73856093 ^ (int) w.seed * 19349663;
        int k = Math.abs(h) % 7;
        return k < 3 ? 0 : k < 6 ? 1 : 2;
    }

    private void drawRoad(Canvas c, World w, int[] pth, boolean noCarrier) {
        path.reset();
        path.moveTo(nx(pth[0]), ny(w, pth[0]));
        for (int i = 1; i < pth.length; i++) path.lineTo(nx(pth[i]), ny(w, pth[i]));
        p.setStyle(Paint.Style.STROKE);
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

    // ---------- Siedler ----------

    private static final int[] SHIRT = {0xFFD84315, 0xFFF9A825, 0xFF6D4C41, 0xFF2E7D32, 0xFF757575, 0xFFAD7A3D, 0xFF1E5BC6};

    void settler(Canvas c, World w, World.Settler s, float t) {
        float x = nx(s.node), y = ny(w, s.node);
        if (s.next >= 0) {
            x += (nx(s.next) - x) * s.prog;
            y += (ny(w, s.next) - y) * s.prog;
        }
        boolean walk = s.next >= 0;
        float ph = walk ? (float) Math.sin((s.prog + s.id * 0.37f) * Math.PI * 4) : 0;
        int shirt = SHIRT[s.job];
        boolean left = s.next >= 0 && nx(s.next) < nx(s.node);
        p.setColor(0x40000000);
        rf.set(x - 3, y - 1, x + 3, y + 1.2f);
        c.drawOval(rf, p);
        // Beine
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setColor(0xFF3B2F2A);
        c.drawLine(x - 1, y - 5, x - 1 + ph * 1.8f, y, p);
        c.drawLine(x + 1, y - 5, x + 1 - ph * 1.8f, y, p);
        p.setStyle(Paint.Style.FILL);
        // Körper
        p.setColor(shirt);
        rf.set(x - 2.6f, y - 11, x + 2.6f, y - 4.5f);
        c.drawRoundRect(rf, 1.8f, 1.8f, p);
        // Kopf
        p.setColor(0xFFF0C495);
        c.drawCircle(x, y - 13, 2.2f, p);
        if (s.job == World.J_SOLDIER) {
            p.setColor(0xFF9E9E9E);
            rf.set(x - 2.5f, y - 16, x + 2.5f, y - 12.6f);
            c.drawOval(rf, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.9f);
            p.setColor(0xFF5D4037);
            c.drawLine(x + 3.5f, y - 1, x + 3.5f, y - 19, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFCFD8DC);
            c.drawCircle(x + 3.5f, y - 19.5f, 1f, p);
        } else {
            p.setColor(s.job == World.J_BUILDER ? 0xFFF9A825 : 0xFF5D4037);
            rf.set(x - 2.4f, y - 15.6f, x + 2.4f, y - 13.4f);
            c.drawOval(rf, p);
        }
        int car = s.carried();
        if (car >= 0) ware(c, car, x, y - 17.5f, 0.85f);
        if (s.working()) {
            float a = (float) Math.sin(t * 9 + s.id) * 0.9f;
            float hx = x + (left ? -1 : 1) * 3, hy = y - 9;
            float ex = hx + (float) Math.sin(a) * 6 * (left ? -1 : 1), ey = hy - (float) Math.cos(a) * 6;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.1f);
            p.setColor(0xFF6D4C41);
            c.drawLine(hx, hy, ex, ey, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFB0BEC5);
            c.drawCircle(ex, ey, 1.4f, p);
        }
    }
}
