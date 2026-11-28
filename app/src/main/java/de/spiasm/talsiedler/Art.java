package de.spiasm.talsiedler;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/**
 * Detaillierte, unbewegte Gebäudebilder. Wird einmal pro Gebäudeart in ein
 * Bild gezeichnet (siehe Renderer), bewegte Teile kommen danach live dazu.
 * Koordinaten: (0,0) ist der Bauplatz-Punkt am Boden.
 */
final class Art {
    static final float SK = 0.55f;

    // Wandarten
    static final int PLASTER = 0, TIMBER = 1, STONE = 2, LOG = 3, PLANK = 4, BRICK = 5;
    // Dacharten
    static final int TILE = 0, SHINGLE = 1, THATCH = 2, SLATE = 3, BOARDS = 4;

    private static final int BEAM = 0xFF4A3020, WOOD = 0xFF8D6440, DARKWOOD = 0xFF5A3C26, IRON = 0xFF3A3A3E,
            GLASS = 0xFF2A3A4C, CREAM = 0xFFEADCC0, GREY = 0xFF9C968C;

    /** Zeichenbereich je Gebäude {links, oben, rechts, unten} */
    static final float[][] BOX = new float[World.NB][];
    /** Rauchpunkt (Schornsteinspitze) oder null */
    static final float[][] SMOKE = new float[World.NB][];
    /** Fahnenstangen-Fußpunkte (x,y paarweise) */
    static final float[][] BANNERS = new float[World.NB][];
    static final float[] SAW_BLADE = {-8.5f, -5.5f};
    static final float[] MILL_HUB = {0.5f, -29f};
    static final float[] SMELT_GLOW = {-6.5f, -4f};
    static final float[] OVEN_GLOW = {-17.2f, -3.2f};

    static {
        for (int i = 0; i < World.NB; i++) {
            int s = World.B_SIZE[i];
            BOX[i] = s == 3 ? new float[]{-44, -76, 46, 11} : s == 2 ? new float[]{-34, -60, 36, 10}
                    : s == 4 ? new float[]{-30, -40, 30, 10} : new float[]{-24, -48, 26, 9};
            BANNERS[i] = new float[0];
        }
        float[][] bx = {{-40, -50, 43, 12}, {-23, -28, 26, 9}, {-23, -28.5f, 26, 9}, {-23, -26, 26, 9}, {-33, -37, 34, 10},
                {-21, -34, 21, 9}, {-29, -44, 29, 10}, {-23, -28, 26.5f, 9.5f}, {-25, -28, 26.5f, 9}, {-17, -27, 17, 8},
                {-33, -35.5f, 36, 10}, {-23, -36, 21, 8.5f}, {-31, -35.5f, 35, 10}, {-31, -31, 36, 10}, {-30, -36.5f, 34, 10},
                {-29, -28, 29, 10.5f}, {-29, -28, 29, 10.5f}, {-29, -28, 29, 10.5f}, {-29, -28, 29, 10.5f},
                {-31, -57.5f, 31, 10}, {-31, -36.5f, 32, 10}, {-33, -41.5f, 37, 10}, {-19, -47, 19, 9},
                {-31, -37, 33, 10}, {-38, -50, 40, 11}};
        for (int i = 0; i < World.NB; i++) BOX[i] = bx[i];
        BANNERS[World.B_HQ] = new float[]{-21f, -42.5f, 28.6f, -47f, 1.5f, -34.5f};
        BANNERS[World.B_GUARD] = new float[]{5.5f, -26.5f};
        BANNERS[World.B_TOWER] = new float[]{7.6f, -36.5f};
        BANNERS[World.B_STORE] = new float[]{2f, -30.6f};
        BANNERS[World.B_SCOUT] = new float[]{0.5f, -41.4f};
        SMOKE[World.B_WOOD] = new float[]{9.5f, -27f};
        SMOKE[World.B_FOREST] = new float[]{7.5f, -25f};
        SMOKE[World.B_QUARRY] = new float[]{8.5f, -24f};
        SMOKE[World.B_SAW] = new float[]{12.5f, -33f};
        SMOKE[World.B_FISH] = new float[]{8f, -24f};
        SMOKE[World.B_HUNT] = new float[]{8.5f, -25f};
        SMOKE[World.B_FARM] = new float[]{17.5f, -31f};
        SMOKE[World.B_BAKERY] = new float[]{-17.5f, -24f};
        SMOKE[World.B_PIGS] = new float[]{14f, -28f};
        SMOKE[World.B_BUTCHER] = new float[]{13f, -33f};
        SMOKE[World.B_SMELTER] = new float[]{12.5f, -55f};
        SMOKE[World.B_TOOLS] = new float[]{13f, -33f};
        SMOKE[World.B_STORE] = null;
        SMOKE[World.B_ARMORY] = new float[]{12f, -33f};
        BANNERS[World.B_FORT] = new float[]{-23.5f, -31.8f, 31.5f, -31.8f, 2f, -34.5f};
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pa = new Path();
    private final RectF rf = new RectF();
    private final Renderer ren;
    private Canvas c;
    private Random r = new Random(1);

    Art(Renderer ren) {
        this.ren = ren;
    }

    // ================= Farben =================

    static int sh(int c, float f) {
        int a = c >>> 24, rr = Math.min(255, (int) (((c >> 16) & 255) * f)),
                g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, float f) {
        int ra = (a >> 16) & 255, ga = (a >> 8) & 255, ba = a & 255;
        int rb = (b >> 16) & 255, gb = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ra + (rb - ra) * f) << 16) | ((int) (ga + (gb - ga) * f) << 8) | (int) (ba + (bb - ba) * f);
    }

    static int alpha(int c, int a) {
        return (c & 0xFFFFFF) | (a << 24);
    }

    private int jit(int col, float amt) {
        return sh(col, 1f + (r.nextFloat() * 2 - 1) * amt);
    }

    private float rn(float a, float b) {
        return a + r.nextFloat() * (b - a);
    }

    // ================= Grundformen =================

    private void rect(float l, float t, float rr, float b, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawRect(l, t, rr, b, p);
    }

    private void rrect(float l, float t, float rr, float b, float rad, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        rf.set(l, t, rr, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    private void oval(float l, float t, float rr, float b, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        rf.set(l, t, rr, b);
        c.drawOval(rf, p);
    }

    private void circ(float x, float y, float rad, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawCircle(x, y, rad, p);
    }

    private void ring(float x, float y, float rad, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawCircle(x, y, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void ln(float x1, float y1, float x2, float y2, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x1, y1, x2, y2, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    private void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int col) {
        pa.reset();
        pa.moveTo(x1, y1);
        pa.lineTo(x2, y2);
        pa.lineTo(x3, y3);
        pa.lineTo(x4, y4);
        pa.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawPath(pa, p);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        pa.reset();
        pa.moveTo(x1, y1);
        pa.lineTo(x2, y2);
        pa.lineTo(x3, y3);
        pa.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawPath(pa, p);
    }

    /** Quader: Vorderseite l..rr / t..b, Tiefe d nach rechts hinten */
    private void box(float l, float t, float rr, float b, float d, int col) {
        float dy = d * SK;
        quad(rr, b, rr + d, b - dy, rr + d, t - dy, rr, t, sh(col, 0.7f));
        quad(l, t, rr, t, rr + d, t - dy, l + d, t - dy, sh(col, 1.18f));
        rect(l, t, rr, b, col);
    }

    private void sideBegin(float x, float y) {
        c.save();
        c.translate(x, y);
        c.skew(0, -SK);
    }

    private void sideEnd() {
        c.restore();
    }

    // ================= Boden =================

    private void ground(float x, float y, float rx, float ry, int col) {
        oval(x - rx - 2, y - ry - 1, x + rx + 2, y + ry + 1, alpha(col, 0x55));
        oval(x - rx, y - ry, x + rx, y + ry, alpha(col, 0xB0));
        for (int i = 0; i < (int) (rx * ry / 5); i++) {
            float a = rn(0, 6.283f), d = (float) Math.sqrt(r.nextFloat());
            float px = x + (float) Math.cos(a) * rx * d, py = y + (float) Math.sin(a) * ry * d;
            circ(px, py, rn(0.2f, 0.45f), jit(r.nextBoolean() ? 0xFF7E6A50 : 0xFFB8A684, 0.1f));
        }
        // Grasbüschel am Rand
        for (int i = 0; i < (int) (rx * 0.9f); i++) {
            float a = rn(0, 6.283f);
            float px = x + (float) Math.cos(a) * rx * rn(0.9f, 1.05f), py = y + (float) Math.sin(a) * ry * rn(0.9f, 1.05f);
            tuft(px, py);
        }
    }

    private void tuft(float x, float y) {
        int g = jit(0xFF4E8A2E, 0.15f);
        ln(x, y, x - 0.7f, y - 1.6f, 0.3f, g);
        ln(x, y, x + 0.1f, y - 2f, 0.3f, sh(g, 1.15f));
        ln(x, y, x + 0.8f, y - 1.4f, 0.3f, g);
    }

    private void shadow(float l, float rr, float y, float d) {
        float dy = d * SK;
        quad(l - 1, y + 1, rr + 1, y + 1, rr + d + 4, y - dy, l + d + 2, y - dy, 0x38000000);
    }

    // ================= Wände =================

    private void tex(float l, float t, float rr, float b, int mat, int col) {
        c.save();
        c.clipRect(l, t, rr, b);
        switch (mat) {
            case PLASTER:
                rect(l, t, rr, b, col);
                speckle(l, t, rr, b, col);
                if ((rr - l) * (b - t) > 120) {
                    float px = rn(l + 2, rr - 5), py = rn(t + 2, b - 6);
                    oval(px - 0.5f, py - 0.4f, px + 4.5f, py + 3.2f, sh(col, 0.9f));
                    for (int k = 0; k < 3; k++) rect(px + (k % 2) * 1.2f, py + k * 1f, px + 1.9f + (k % 2) * 1.2f, py + k + 0.75f, 0xFFA0624A);
                }
                stones(l, b - 2.2f, rr, b, GREY);
                break;
            case TIMBER: {
                rect(l, t, rr, b, col);
                speckle(l, t, rr, b, col);
                float sill = b - 2.2f, mid = t + (sill - t) * 0.48f;
                stones(l, sill, rr, b, GREY);
                beamH(l, sill - 0.9f, rr, sill);
                beamH(l, t, rr, t + 0.9f);
                if (b - t > 9) beamH(l, mid - 0.45f, rr, mid + 0.45f);
                int n = Math.max(2, Math.round((rr - l) / 5.5f));
                float st = (rr - l) / n;
                for (int k = 0; k <= n; k++) {
                    float px = l + k * st;
                    beamV(px - 0.45f, t, px + 0.45f, sill);
                }
                // Streben in den Eckfeldern
                ln(l + 0.5f, sill - 0.9f, l + st - 0.5f, b - t > 9 ? mid : t + 1, 0.8f, BEAM);
                ln(rr - 0.5f, sill - 0.9f, rr - st + 0.5f, b - t > 9 ? mid : t + 1, 0.8f, BEAM);
                break;
            }
            case STONE:
                stones(l, t, rr, b, col);
                break;
            case LOG: {
                rect(l, t, rr, b, sh(col, 0.6f));
                float lh = 2.1f;
                for (float yy = b; yy > t - lh; yy -= lh) {
                    int lc = jit(col, 0.08f);
                    rrect(l - 1, yy - lh + 0.15f, rr + 1, yy - 0.1f, 1f, lc);
                    ln(l, yy - lh + 0.5f, rr, yy - lh + 0.5f, 0.35f, sh(lc, 1.2f));
                    ln(l, yy - 0.35f, rr, yy - 0.35f, 0.3f, sh(lc, 0.7f));
                    if (r.nextFloat() < 0.6f) {
                        float kx = rn(l + 1, rr - 1);
                        oval(kx - 0.5f, yy - lh * 0.65f, kx + 0.5f, yy - lh * 0.3f, sh(lc, 0.65f));
                    }
                    for (int k = 0; k < (rr - l) / 3; k++) {
                        float kx = rn(l, rr);
                        ln(kx, yy - lh * 0.5f, kx + rn(1, 2.5f), yy - lh * 0.5f + rn(-0.2f, 0.2f), 0.15f, sh(lc, 0.82f));
                    }
                }
                break;
            }
            case PLANK: {
                float pw = 1.8f;
                for (float xx = l; xx < rr; xx += pw) {
                    int pc = jit(col, 0.07f);
                    rect(xx, t, xx + pw, b, pc);
                    ln(xx + 0.35f, t, xx + 0.35f, b, 0.2f, sh(pc, 1.12f));
                    rect(xx + pw - 0.22f, t, xx + pw, b, sh(pc, 0.6f));
                    for (int k = 0; k < (b - t) / 4; k++) {
                        float ky = rn(t, b);
                        ln(xx + 0.6f, ky, xx + 0.8f, ky + rn(1, 2.5f), 0.12f, sh(pc, 0.82f));
                    }
                }
                if (b - t > 6) {
                    rect(l, t + 1.5f, rr, t + 2.5f, sh(col, 0.82f));
                    rect(l, b - 2.5f, rr, b - 1.5f, sh(col, 0.82f));
                    for (float xx = l + 0.9f; xx < rr; xx += pw) {
                        circ(xx, t + 2, 0.18f, IRON);
                        circ(xx, b - 2, 0.18f, IRON);
                    }
                }
                rect(l, b - 1f, rr, b, sh(GREY, 0.8f));
                break;
            }
            case BRICK: {
                rect(l, t, rr, b, 0xFFB8A898);
                float bh = 1.3f, bw = 2.6f;
                int row = 0;
                for (float yy = b; yy > t - bh; yy -= bh, row++) {
                    for (float xx = l - (row % 2) * bw / 2; xx < rr; xx += bw) {
                        int bc = jit(col, 0.12f);
                        rect(xx + 0.12f, yy - bh + 0.14f, xx + bw - 0.12f, yy - 0.12f, bc);
                        rect(xx + 0.12f, yy - bh + 0.14f, xx + bw - 0.12f, yy - bh + 0.4f, sh(bc, 1.12f));
                    }
                }
                break;
            }
        }
        c.restore();
    }

    private void speckle(float l, float t, float rr, float b, int col) {
        int n = (int) ((rr - l) * (b - t) / 2.5f);
        for (int i = 0; i < n; i++)
            circ(rn(l, rr), rn(t, b), rn(0.15f, 0.35f), jit(col, 0.07f));
        // Wasserflecken unten
        for (float xx = l; xx < rr; xx += 1.5f)
            rect(xx, b - 3.4f, xx + 1.5f, b - 2.2f, alpha(sh(col, 0.7f), (int) rn(10, 50)));
    }

    private void beamH(float l, float t, float rr, float b) {
        rect(l, t, rr, b, BEAM);
        ln(l, t + 0.15f, rr, t + 0.15f, 0.18f, sh(BEAM, 1.4f));
    }

    private void beamV(float l, float t, float rr, float b) {
        rect(l, t, rr, b, BEAM);
        ln(l + 0.15f, t, l + 0.15f, b, 0.18f, sh(BEAM, 1.4f));
    }

    private void stones(float l, float t, float rr, float b, int col) {
        c.save();
        c.clipRect(l, t, rr, b);
        rect(l, t, rr, b, sh(col, 0.62f));
        float rh = 2.3f;
        int row = 0;
        for (float yy = b; yy > t - rh; yy -= rh, row++) {
            float xx = l - (row % 2) * 1.6f;
            while (xx < rr) {
                float w = rn(2.3f, 4.2f);
                int sc = jit(col, 0.12f);
                rrect(xx + 0.18f, yy - rh + 0.2f, xx + w - 0.18f, yy - 0.15f, 0.55f, sc);
                ln(xx + 0.5f, yy - rh + 0.5f, xx + w - 0.6f, yy - rh + 0.5f, 0.22f, sh(sc, 1.17f));
                ln(xx + 0.5f, yy - 0.4f, xx + w - 0.5f, yy - 0.4f, 0.2f, sh(sc, 0.78f));
                if (r.nextFloat() < 0.25f) circ(rn(xx + 0.8f, xx + w - 0.8f), yy - rh * 0.5f, 0.2f, sh(sc, 0.75f));
                xx += w;
            }
        }
        c.restore();
    }

    // ================= Dächer =================

    private float ax, ay, bx, by, dx, dyy;

    private float px(float s, float q) {
        return ax + s * (bx - ax) + q * dx;
    }

    private float py(float s, float q) {
        return ay + s * (by - ay) + q * dyy;
    }

    private void squad(float s0, float q0, float s1, float q1, int col) {
        quad(px(s0, q0), py(s0, q0), px(s1, q0), py(s1, q0), px(s1, q1), py(s1, q1), px(s0, q1), py(s0, q1), col);
    }

    private void sline(float s0, float q0, float s1, float q1, float w, int col) {
        ln(px(s0, q0), py(s0, q0), px(s1, q1), py(s1, q1), w, col);
    }

    /** Dachfläche: Firstpunkt vorne (x0,y0), Traufe vorne (x1,y1), Tiefe d */
    private void roofPlane(float x0, float y0, float x1, float y1, float d, int mat, int col) {
        ax = x0;
        ay = y0;
        bx = x1;
        by = y1;
        dx = d;
        dyy = -d * SK;
        float slen = (float) Math.hypot(x1 - x0, y1 - y0);
        squad(0, 0, 1, 1, sh(col, 0.55f));
        switch (mat) {
            case TILE:
            case SHINGLE:
            case SLATE: {
                float rh = mat == TILE ? 1.7f : mat == SHINGLE ? 1.35f : 1.15f;
                float tw = mat == TILE ? 1.6f : mat == SHINGLE ? 1.3f : 1.5f;
                int rows = Math.max(3, Math.round(slen / rh));
                int cols = Math.max(3, Math.round(d / tw));
                for (int i = 0; i < rows; i++) {
                    float s0 = i / (float) rows, s1 = (i + 1) / (float) rows;
                    float off = (i % 2) * 0.5f;
                    for (int j = -1; j < cols; j++) {
                        float q0 = Math.max(0, (j + off) / cols), q1 = Math.min(1, (j + 1 + off) / cols);
                        if (q1 <= q0) continue;
                        int tc = jit(col, mat == SLATE ? 0.1f : 0.08f);
                        if (mat == SHINGLE && r.nextFloat() < 0.08f) tc = mix(tc, 0xFF5E7A3A, 0.5f);
                        squad(s0 + 0.02f, q0, s1, q1, tc);
                        sline(s0 + 0.05f, q0 + 0.02f, s1 - 0.05f, q0 + 0.02f, 0.18f, sh(tc, 1.18f));
                        sline(s0, q1, s1, q1, 0.2f, sh(tc, 0.62f));
                        if (mat == TILE) sline(s0 + 0.1f, (q0 + q1) / 2, s1 - 0.05f, (q0 + q1) / 2, 0.25f, sh(tc, 1.12f));
                    }
                    sline(s1, 0, s1, 1, 0.32f, sh(col, 0.5f));
                }
                break;
            }
            case THATCH: {
                squad(0, 0, 1, 1, sh(col, 0.85f));
                int n = (int) (slen * d * 2.2f);
                for (int i = 0; i < n; i++) {
                    float s = r.nextFloat() * 0.95f, q = r.nextFloat();
                    int tc = jit(col, 0.18f);
                    sline(s, q, Math.min(1, s + rn(0.08f, 0.2f)), q + rn(-0.01f, 0.01f), 0.28f, tc);
                }
                for (int i = 1; i < 4; i++) sline(i / 4f, 0, i / 4f, 1, 0.4f, alpha(sh(col, 0.5f), 0x70));
                for (int i = 0; i < 6; i++) {
                    float s = rn(0.1f, 0.8f), q = rn(0.1f, 0.9f);
                    circ(px(s, q), py(s, q), rn(0.5f, 1.1f), alpha(0xFF6B8A3A, 0x60));
                }
                break;
            }
            case BOARDS: {
                int n = Math.max(3, Math.round(d / 1.7f));
                for (int j = 0; j < n; j++) {
                    float q0 = j / (float) n, q1 = (j + 1) / (float) n;
                    int tc = jit(col, 0.09f);
                    squad(0, q0, 1, q1, tc);
                    sline(0, q1, 1, q1, 0.25f, sh(tc, 0.6f));
                }
                sline(0.15f, 0, 0.15f, 1, 0.5f, sh(col, 0.75f));
                sline(0.85f, 0, 0.85f, 1, 0.5f, sh(col, 0.75f));
                break;
            }
        }
        // Traufe und First
        sline(1, 0, 1, 1, 0.7f, sh(col, 0.42f));
        sline(0, 0, 0, 1, 1.5f, sh(col, 0.55f));
        sline(0, 0, 0, 1, 0.5f, sh(col, 0.95f));
    }

    // ================= Haus =================

    float hl, hr, hy, hh, hx, hd, hrh;

    /** Haus mit Giebel vorne */
    private void house(float x, float y, float w, float h, float rh, float d, int wm, int wc, int gm, int rm, int rc) {
        float l = x - w / 2, rr = x + w / 2;
        hl = l;
        hr = rr;
        hy = y;
        hh = h;
        hx = x;
        hd = d;
        hrh = rh;
        shadow(l, rr, y, d);
        // Seitenwand
        sideBegin(rr, y);
        tex(0, -h, d, 0, wm, wc);
        rect(0, -h, d, 0, 0x40000000);
        rect(0, -h, d, -h + 1.8f, 0x55000000);
        sideEnd();
        // Vorderwand
        tex(l, y - h, rr, y, wm, wc);
        // Giebel
        pa.reset();
        pa.moveTo(l, y - h);
        pa.lineTo(x, y - h - rh);
        pa.lineTo(rr, y - h);
        pa.close();
        c.save();
        c.clipPath(pa);
        tex(l, y - h - rh, rr, y - h, gm, gm == wm ? wc : gm == PLANK ? 0xFF9A6E44 : gm == TIMBER ? CREAM : sh(wc, 0.95f));
        // Schatten unter dem Ortgang
        pa.reset();
        pa.moveTo(l, y - h);
        pa.lineTo(x, y - h - rh);
        pa.lineTo(rr, y - h);
        pa.lineTo(rr, y - h + 0.2f);
        pa.lineTo(x, y - h - rh + 1.6f);
        pa.lineTo(l, y - h + 0.2f);
        pa.close();
        p.setColor(0x45000000);
        c.drawPath(pa, p);
        c.restore();
        if (gm == TIMBER) {
            beamH(l, y - h - 0.5f, rr, y - h + 0.4f);
            beamV(x - 0.4f, y - h - rh, x + 0.4f, y - h);
            ln(x - w * 0.25f, y - h, x - w * 0.25f, y - h - rh * 0.5f, 0.8f, BEAM);
            ln(x + w * 0.25f, y - h, x + w * 0.25f, y - h - rh * 0.5f, 0.8f, BEAM);
        } else {
            rect(l, y - h - 0.3f, rr, y - h + 0.4f, sh(wc, 0.7f));
        }
        // Dach
        float ov = 1.8f, ovy = 1.3f;
        roofPlane(x, y - h - rh - 0.4f, rr + ov, y - h + ovy, d + 1.4f, rm, rc);
        // Windbretter
        int bb = sh(rc, 0.5f);
        ln(l - ov, y - h + ovy, x, y - h - rh - 0.4f, 1.3f, bb);
        ln(x, y - h - rh - 0.4f, rr + ov, y - h + ovy, 1.3f, bb);
        ln(l - ov + 0.3f, y - h + ovy - 0.5f, x, y - h - rh - 0.9f, 0.3f, sh(rc, 0.9f));
        ln(x, y - h - rh - 0.9f, rr + ov - 0.3f, y - h + ovy - 0.5f, 0.3f, sh(rc, 0.9f));
        // Hausecke
        ln(rr, y - h + 0.5f, rr, y, 0.35f, sh(wc, 0.6f));
    }

    private void gableWindow(boolean round) {
        float cx = hx, cy = hy - hh - hrh * 0.42f;
        if (round) {
            circ(cx, cy, 1.9f, DARKWOOD);
            circ(cx, cy, 1.4f, GLASS);
            ln(cx - 1.4f, cy, cx + 1.4f, cy, 0.3f, DARKWOOD);
            ln(cx, cy - 1.4f, cx, cy + 1.4f, 0.3f, DARKWOOD);
            ln(cx - 0.8f, cy - 0.4f, cx - 0.3f, cy - 0.9f, 0.25f, 0x60FFFFFF);
        } else {
            window(cx, cy, 2.6f, 2.4f, 0, false);
        }
    }

    // ================= Fenster, Türen =================

    private void window(float cx, float cy, float w, float h, int shutter, boolean flowers) {
        float l = cx - w / 2, rr = cx + w / 2, t = cy - h / 2, b = cy + h / 2;
        if (shutter != 0) {
            for (int s = -1; s <= 1; s += 2) {
                float sl = s < 0 ? l - w * 0.55f - 0.3f : rr + 0.3f, sr = sl + w * 0.55f;
                rect(sl, t - 0.2f, sr, b + 0.2f, shutter);
                for (float xx = sl + 0.6f; xx < sr - 0.2f; xx += 0.7f) ln(xx, t, xx, b, 0.12f, sh(shutter, 0.7f));
                ln(sl + 0.2f, b - 0.4f, sr - 0.2f, t + 0.4f, 0.25f, sh(shutter, 0.75f));
                rect(sl, t - 0.2f, sr, t + 0.2f, sh(shutter, 1.15f));
            }
        }
        rect(l - 0.45f, t - 0.45f, rr + 0.45f, b + 0.45f, 0xFFD8C8A8);
        rect(l, t, rr, b, GLASS);
        rect(l, t, rr, cy, 0xFF34485E);
        // warmes Licht innen
        rect(l + 0.3f, cy + 0.2f, rr - 0.3f, b - 0.2f, 0x30FFC060);
        ln(l + 0.4f, cy - 0.3f, l + w * 0.45f, t + 0.35f, 0.35f, 0x55FFFFFF);
        ln(cx, t, cx, b, 0.3f, 0xFFD8C8A8);
        ln(l, cy, rr, cy, 0.3f, 0xFFD8C8A8);
        rect(l - 0.8f, t - 1.1f, rr + 0.8f, t - 0.45f, DARKWOOD);
        rect(l - 0.8f, b + 0.45f, rr + 0.8f, b + 1.0f, 0xFFC8B898);
        rect(l - 0.8f, b + 1.0f, rr + 0.8f, b + 1.25f, 0x50000000);
        if (flowers) {
            rect(l - 0.5f, b + 1.0f, rr + 0.5f, b + 2.1f, 0xFF7A4E2C);
            for (float xx = l - 0.2f; xx <= rr + 0.2f; xx += 0.6f) {
                circ(xx, b + 0.9f, 0.45f, jit(0xFF3E7A2A, 0.15f));
            }
            int[] fl = {0xFFE53935, 0xFFFFD54F, 0xFFF06292, 0xFFFFFFFF};
            for (float xx = l; xx <= rr; xx += 0.9f) circ(xx + rn(-0.2f, 0.2f), b + rn(0.2f, 0.7f), 0.35f, fl[r.nextInt(4)]);
        }
    }

    private void archWindow(float cx, float cy, float w, float h) {
        float l = cx - w / 2, rr = cx + w / 2, b = cy + h / 2, t = cy - h / 2;
        pa.reset();
        pa.moveTo(l - 0.6f, b + 0.5f);
        pa.lineTo(l - 0.6f, t + w / 2);
        pa.quadTo(l - 0.6f, t - 0.6f, cx, t - 0.6f);
        pa.quadTo(rr + 0.6f, t - 0.6f, rr + 0.6f, t + w / 2);
        pa.lineTo(rr + 0.6f, b + 0.5f);
        pa.close();
        p.setColor(0xFFCFC6B4);
        c.drawPath(pa, p);
        pa.reset();
        pa.moveTo(l, b);
        pa.lineTo(l, t + w / 2);
        pa.quadTo(l, t, cx, t);
        pa.quadTo(rr, t, rr, t + w / 2);
        pa.lineTo(rr, b);
        pa.close();
        p.setColor(GLASS);
        c.drawPath(pa, p);
        ln(cx, t, cx, b, 0.25f, IRON);
        ln(l, cy, rr, cy, 0.25f, IRON);
        ln(l + 0.4f, cy - 0.3f, cx - 0.3f, t + 0.8f, 0.3f, 0x50FFFFFF);
        rect(l - 0.8f, b + 0.4f, rr + 0.8f, b + 1f, 0xFFCFC6B4);
    }

    private void slit(float cx, float cy, float h) {
        rect(cx - 0.9f, cy - h / 2 - 0.5f, cx + 0.9f, cy + h / 2 + 0.5f, 0xFFC8C0B0);
        rect(cx - 0.4f, cy - h / 2, cx + 0.4f, cy + h / 2, 0xFF1A1A1A);
    }

    private void door(float cx, float y, float w, float h, boolean arch, int col) {
        float l = cx - w / 2, rr = cx + w / 2, t = y - h;
        // Rahmen
        if (arch) {
            pa.reset();
            pa.moveTo(l - 0.8f, y);
            pa.lineTo(l - 0.8f, t + w / 2);
            pa.quadTo(l - 0.8f, t - 0.9f, cx, t - 0.9f);
            pa.quadTo(rr + 0.8f, t - 0.9f, rr + 0.8f, t + w / 2);
            pa.lineTo(rr + 0.8f, y);
            pa.close();
            p.setColor(0xFFBDB4A2);
            c.drawPath(pa, p);
            for (int k = -2; k <= 2; k++) {
                float a = (float) (Math.PI * (0.5 + k * 0.2));
                ln(cx - (float) Math.cos(a) * (w / 2 + 0.4f), t + w / 2 - (float) Math.sin(a) * (w / 2 + 0.4f),
                        cx - (float) Math.cos(a) * (w / 2 + 0.9f), t + w / 2 - (float) Math.sin(a) * (w / 2 + 0.9f), 0.15f, 0xFF7E766A);
            }
            pa.reset();
            pa.moveTo(l, y);
            pa.lineTo(l, t + w / 2);
            pa.quadTo(l, t, cx, t);
            pa.quadTo(rr, t, rr, t + w / 2);
            pa.lineTo(rr, y);
            pa.close();
            c.save();
            c.clipPath(pa);
        } else {
            rect(l - 0.7f, t - 0.8f, rr + 0.7f, y, DARKWOOD);
            c.save();
            c.clipRect(l, t, rr, y);
        }
        float pw = w / Math.max(2, Math.round(w / 1.2f));
        for (float xx = l; xx < rr; xx += pw) {
            int pc = jit(col, 0.08f);
            rect(xx, t, xx + pw, y, pc);
            rect(xx + pw - 0.15f, t, xx + pw, y, sh(pc, 0.6f));
        }
        rect(l, t + h * 0.22f, rr, t + h * 0.22f + 0.6f, IRON);
        rect(l, y - h * 0.25f, rr, y - h * 0.25f + 0.6f, IRON);
        for (float xx = l + 0.4f; xx < rr; xx += 0.9f) {
            circ(xx, t + h * 0.22f + 0.3f, 0.15f, 0xFF888888);
            circ(xx, y - h * 0.25f + 0.3f, 0.15f, 0xFF888888);
        }
        rect(l, t, rr, t + 0.8f, 0x40000000);
        c.restore();
        ring(rr - w * 0.22f, y - h * 0.45f, 0.4f, 0.18f, 0xFFB09040);
        oval(l - 1.2f, y - 0.4f, rr + 1.2f, y + 1.1f, 0xFFA8A196);
        oval(l - 1.0f, y - 0.4f, rr + 1.0f, y + 0.5f, 0xFFBDB6AA);
    }

    private float[] chimney(float x, float y, float w, float h, int mat) {
        float d = w * 0.7f, dy = d * SK;
        sideBegin(x + w / 2, y);
        tex(0, -h, d, 0, mat, mat == BRICK ? 0xFFA0503A : GREY);
        rect(0, -h, d, 0, 0x50000000);
        sideEnd();
        tex(x - w / 2, y - h, x + w / 2, y, mat, mat == BRICK ? 0xFFA0503A : GREY);
        box(x - w / 2 - 0.5f, y - h - 0.9f, x + w / 2 + 0.5f, y - h, d + 0.5f, 0xFF6E6A64);
        quad(x - w / 2 + 0.3f, y - h - 1f, x + w / 2 - 0.2f, y - h - 1f, x + w / 2 + d - 0.6f, y - h - 1f - dy + 0.5f,
                x - w / 2 + d - 0.3f, y - h - 1f - dy + 0.5f, 0xFF222020);
        // Ruß
        rect(x - w / 2, y - h, x + w / 2, y - h + 1.2f, 0x40000000);
        return new float[]{x + d / 2, y - h - dy / 2};
    }

    // ================= Kleinkram =================

    private void barrel(float x, float y, float s) {
        float w = 2.2f * s, h = 3f * s;
        oval(x - w - 0.4f, y - 0.6f, x + w + 0.8f, y + 0.8f, 0x40000000);
        pa.reset();
        pa.moveTo(x - w * 0.88f, y);
        pa.quadTo(x - w * 1.12f, y - h / 2, x - w * 0.88f, y - h);
        pa.lineTo(x + w * 0.88f, y - h);
        pa.quadTo(x + w * 1.12f, y - h / 2, x + w * 0.88f, y);
        pa.close();
        p.setColor(0xFF8A5A32);
        c.drawPath(pa, p);
        for (int k = -2; k <= 2; k++) ln(x + k * w * 0.36f, y - h + 0.3f, x + k * w * 0.38f, y - 0.2f, 0.15f, 0xFF5E3C20);
        rect(x + w * 0.3f, y - h, x + w, y, 0x30000000);
        ln(x - w * 0.97f, y - h * 0.22f, x + w * 0.97f, y - h * 0.22f, 0.45f * s, IRON);
        ln(x - w * 0.97f, y - h * 0.78f, x + w * 0.97f, y - h * 0.78f, 0.45f * s, IRON);
        oval(x - w * 0.88f, y - h - 0.7f * s, x + w * 0.88f, y - h + 0.7f * s, 0xFFA87448);
        oval(x - w * 0.6f, y - h - 0.4f * s, x + w * 0.6f, y - h + 0.4f * s, 0xFF96633A);
    }

    private void crate(float x, float y, float s) {
        float w = 2.4f * s, d = 1.6f * s;
        oval(x - w - 0.3f, y - 0.6f, x + w + d + 0.6f, y + 0.8f, 0x40000000);
        box(x - w, y - w * 1.6f, x + w, y, d, 0xFFB08350);
        int e = 0xFF7A5430;
        ln(x - w, y - w * 1.6f, x + w, y, 0.4f * s, e);
        rect(x - w, y - w * 1.6f, x + w, y - w * 1.6f + 0.45f * s, e);
        rect(x - w, y - 0.45f * s, x + w, y, e);
        rect(x - w, y - w * 1.6f, x - w + 0.45f * s, y, e);
        rect(x + w - 0.45f * s, y - w * 1.6f, x + w, y, e);
    }

    private void sack(float x, float y, float s, int col) {
        oval(x - 2.2f * s, y - 0.5f, x + 2.6f * s, y + 0.8f, 0x40000000);
        pa.reset();
        pa.moveTo(x - 1.9f * s, y);
        pa.quadTo(x - 2.3f * s, y - 2.6f * s, x - 0.7f * s, y - 3.4f * s);
        pa.lineTo(x + 0.7f * s, y - 3.4f * s);
        pa.quadTo(x + 2.3f * s, y - 2.6f * s, x + 1.9f * s, y);
        pa.close();
        p.setColor(col);
        c.drawPath(pa, p);
        ln(x + 0.8f * s, y - 2.6f * s, x + 1.4f * s, y - 0.5f * s, 0.6f * s, sh(col, 0.85f));
        tri(x - 0.7f * s, y - 3.4f * s, x + 0.7f * s, y - 3.4f * s, x, y - 4.4f * s, sh(col, 0.9f));
        ln(x - 0.8f * s, y - 3.5f * s, x + 0.8f * s, y - 3.5f * s, 0.35f * s, 0xFF8D6E4A);
        ln(x - 1.2f * s, y - 1.8f * s, x - 0.6f * s, y - 2.4f * s, 0.25f * s, sh(col, 1.1f));
    }

    private void logEnd(float x, float y, float rad) {
        circ(x, y, rad, 0xFF5E3C22);
        circ(x - 0.1f * rad, y - 0.1f * rad, rad * 0.82f, jit(0xFFD8B47E, 0.06f));
        ring(x - 0.1f * rad, y - 0.1f * rad, rad * 0.5f, 0.12f, 0xFFA07A48);
        ring(x - 0.1f * rad, y - 0.1f * rad, rad * 0.25f, 0.12f, 0xFFA07A48);
    }

    private void woodpile(float x, float y, int cols, int rows, float rad) {
        oval(x - rad * 1.2f, y - rad, x + cols * rad * 2 + rad, y + rad * 0.8f, 0x40000000);
        for (int ro = 0; ro < rows; ro++) {
            int n = cols - (ro > rows / 2 ? 1 : 0);
            for (int k = 0; k < n; k++)
                logEnd(x + k * rad * 1.95f + (ro % 2) * rad * 0.97f, y - rad - ro * rad * 1.7f, rad);
        }
    }

    /** Liegender Stamm von der Seite */
    private void logSide(float x1, float x2, float y, float rad) {
        rrect(x1, y - 2 * rad, x2, y, rad, 0xFF7A5232);
        ln(x1 + rad, y - 1.5f * rad, x2 - rad, y - 1.5f * rad, 0.25f, 0xFF96704A);
        ln(x1 + rad, y - 0.5f * rad, x2 - rad, y - 0.5f * rad, 0.25f, 0xFF5A3A20);
        logEnd(x2 - rad * 0.4f, y - rad, rad * 0.9f);
    }

    private void fence(float x1, float y1, float x2, float y2, int posts) {
        for (int k = 0; k <= posts; k++) {
            float f = k / (float) posts, x = x1 + (x2 - x1) * f, y = y1 + (y2 - y1) * f;
            rect(x - 0.35f, y - 4f, x + 0.35f, y + 0.3f, 0xFF6E4C30);
            tri(x - 0.35f, y - 4f, x + 0.35f, y - 4f, x, y - 4.6f, 0xFF6E4C30);
        }
        ln(x1, y1 - 3.2f, x2, y2 - 3.2f, 0.45f, 0xFF8D6440);
        ln(x1, y1 - 1.5f, x2, y2 - 1.5f, 0.45f, 0xFF8D6440);
        ln(x1, y1 - 3.4f, x2, y2 - 3.4f, 0.15f, 0xFFB08860);
    }

    private void bush(float x, float y, float s, boolean flowers) {
        oval(x - 2.6f * s, y - 0.6f * s, x + 3f * s, y + 0.9f * s, 0x40000000);
        float[][] pts = {{-1.4f, -1.2f, 1.5f}, {1.3f, -1.1f, 1.4f}, {0, -2.2f, 1.6f}, {-0.6f, -0.7f, 1.3f}, {0.8f, -0.6f, 1.2f}};
        for (float[] q : pts) circ(x + q[0] * s, y + q[1] * s, q[2] * s, jit(0xFF3C7226, 0.1f));
        for (float[] q : pts) circ(x + q[0] * s - 0.4f * s, y + q[1] * s - 0.5f * s, q[2] * 0.5f * s, jit(0xFF5A9A38, 0.1f));
        if (flowers) {
            int[] fl = {0xFFE53935, 0xFFFFEB3B, 0xFFFFFFFF, 0xFFBA68C8};
            int fc = fl[r.nextInt(4)];
            for (int k = 0; k < 6; k++) circ(x + rn(-2, 2) * s, y + rn(-3, -0.5f) * s, 0.35f * s, fc);
        }
    }

    private void flowers(float x, float y, int n) {
        int[] fl = {0xFFE53935, 0xFFFFEB3B, 0xFFFFFFFF, 0xFFBA68C8, 0xFFFF9800};
        for (int k = 0; k < n; k++) {
            float fx = x + rn(-2.5f, 2.5f), fy = y + rn(-1, 1);
            ln(fx, fy, fx, fy - 1.3f, 0.2f, 0xFF3E7A2A);
            circ(fx, fy - 1.4f, 0.4f, fl[r.nextInt(fl.length)]);
        }
    }

    private void lantern(float x, float y, float h) {
        rect(x - 0.35f, y - h, x + 0.35f, y, 0xFF3E2A1C);
        ln(x, y - h, x + 1.6f, y - h, 0.35f, 0xFF3E2A1C);
        ln(x + 1.6f, y - h, x + 1.6f, y - h + 0.6f, 0.15f, IRON);
        circ(x + 1.6f, y - h + 1.9f, 1.4f, 0x30FFD060);
        rect(x + 0.9f, y - h + 0.7f, x + 2.3f, y - h + 2.8f, 0xFFFFD27A);
        rect(x + 0.9f, y - h + 0.7f, x + 2.3f, y - h + 1.0f, IRON);
        rect(x + 0.9f, y - h + 2.6f, x + 2.3f, y - h + 2.9f, IRON);
        ln(x + 1.6f, y - h + 0.8f, x + 1.6f, y - h + 2.7f, 0.15f, IRON);
    }

    private void torch(float x, float y) {
        rect(x - 0.3f, y - 3, x + 0.3f, y, 0xFF5A3C26);
        circ(x, y - 3.6f, 1.3f, 0x40FF9800);
        tri(x - 0.7f, y - 3, x + 0.7f, y - 3, x, y - 5f, 0xFFFF9800);
        tri(x - 0.35f, y - 3, x + 0.35f, y - 3, x, y - 4.2f, 0xFFFFEB3B);
    }

    private void wheel(float x, float y, float rad) {
        ring(x, y, rad, 0.5f, 0xFF5A3C26);
        ring(x, y, rad - 0.3f, 0.15f, IRON);
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            ln(x, y, x + (float) Math.cos(a) * rad, y + (float) Math.sin(a) * rad, 0.25f, 0xFF7A5232);
        }
        circ(x, y, 0.5f, 0xFF3E2A1C);
    }

    private void cart(float x, float y, int load) {
        oval(x - 5, y - 0.5f, x + 6, y + 1.4f, 0x40000000);
        box(x - 4.5f, y - 4.2f, x + 3.5f, y - 2f, 2.5f, 0xFF9A6E44);
        for (float xx = x - 4.5f; xx < x + 3.5f; xx += 1.6f) ln(xx, y - 4.2f, xx, y - 2f, 0.15f, 0xFF6E4C30);
        ln(x - 4.5f, y - 3f, x - 8f, y - 1.2f, 0.4f, 0xFF7A5232);
        ln(x - 4.5f, y - 2.4f, x - 8f, y - 0.4f, 0.4f, 0xFF6E4C30);
        if (load >= 0) {
            ren.ware(c, load, x - 2, y - 5, 0.55f);
            ren.ware(c, load, x + 1.5f, y - 5.5f, 0.55f);
        }
        wheel(x + 0.5f, y - 1.8f, 1.9f);
    }

    private void wheelbarrow(float x, float y, int load) {
        oval(x - 4, y - 0.4f, x + 4, y + 1f, 0x40000000);
        quad(x - 2.6f, y - 3.3f, x + 2.6f, y - 3.3f, x + 1.8f, y - 1.4f, x - 1.6f, y - 1.4f, 0xFF6E7B84);
        quad(x - 2.6f, y - 3.3f, x + 2.6f, y - 3.3f, x + 2.2f, y - 3.9f, x - 2.2f, y - 3.9f, 0xFF4E5A62);
        ln(x - 1.8f, y - 2.6f, x - 5f, y - 2.2f, 0.35f, 0xFF7A5232);
        ln(x - 1.2f, y - 1.4f, x - 1.6f, y, 0.3f, 0xFF5A3C26);
        if (load >= 0) ren.ware(c, load, x, y - 4.2f, 0.5f);
        wheel(x + 2.6f, y - 1.1f, 1.2f);
    }

    private void chopBlock(float x, float y) {
        oval(x - 2, y - 0.5f, x + 2.6f, y + 0.9f, 0x40000000);
        rect(x - 1.7f, y - 2.6f, x + 1.7f, y, 0xFF7A5232);
        for (float xx = x - 1.4f; xx < x + 1.6f; xx += 0.7f) ln(xx, y - 2.4f, xx, y - 0.1f, 0.12f, 0xFF5A3A20);
        oval(x - 1.7f, y - 3.3f, x + 1.7f, y - 1.9f, 0xFFD8B47E);
        ring(x, y - 2.6f, 0.8f, 0.12f, 0xFFA07A48);
        // Axt im Klotz
        ln(x + 0.3f, y - 2.8f, x + 2.6f, y - 6.2f, 0.45f, 0xFFB08860);
        quad(x - 0.6f, y - 2.7f, x + 0.9f, y - 2.4f, x + 1.1f, y - 3.6f, x - 0.2f, y - 3.9f, 0xFFB0B4B8);
        // Späne
        for (int k = 0; k < 6; k++) {
            float sx = x + rn(-4, 4), sy = y + rn(-0.5f, 1.2f);
            ln(sx, sy, sx + rn(0.4f, 0.9f), sy + rn(-0.3f, 0.3f), 0.3f, 0xFFD8B47E);
        }
    }

    private void anvil(float x, float y) {
        oval(x - 2.5f, y - 0.5f, x + 3, y + 0.9f, 0x40000000);
        rect(x - 1.2f, y - 2.2f, x + 1.2f, y, 0xFF6E4C30);
        quad(x - 0.7f, y - 2.2f, x + 0.7f, y - 2.2f, x + 1f, y - 3.2f, x - 1f, y - 3.2f, IRON);
        quad(x - 2.6f, y - 3.2f, x + 1.8f, y - 3.2f, x + 1.8f, y - 4.2f, x - 1.4f, y - 4.2f, 0xFF4A4A50);
        tri(x + 1.8f, y - 3.2f, x + 1.8f, y - 4.2f, x + 3.3f, y - 4.0f, 0xFF4A4A50);
        ln(x - 1.4f, y - 4.1f, x + 2.8f, y - 4.1f, 0.2f, 0xFF8A8A92);
    }

    private void grindstone(float x, float y) {
        oval(x - 3, y - 0.5f, x + 3, y + 0.9f, 0x40000000);
        ln(x - 2.2f, y, x - 1.4f, y - 3.5f, 0.45f, 0xFF6E4C30);
        ln(x + 2.2f, y, x + 1.4f, y - 3.5f, 0.45f, 0xFF6E4C30);
        oval(x - 0.8f, y - 6.4f, x + 0.8f, y - 0.8f, 0xFFA8A296);
        oval(x - 0.5f, y - 6.2f, x + 0.5f, y - 1f, 0xFFC4BEB0);
        ln(x, y - 3.6f, x + 2.4f, y - 3.6f, 0.3f, IRON);
        ln(x + 2.4f, y - 3.6f, x + 2.4f, y - 2.4f, 0.4f, 0xFF7A5232);
        rect(x - 1.6f, y - 1.2f, x + 1.6f, y, 0xFF7A5232);
    }

    private void trough(float x, float y, float w, int fill) {
        oval(x - w / 2 - 0.5f, y - 0.5f, x + w / 2 + 1.5f, y + 0.9f, 0x40000000);
        box(x - w / 2, y - 1.6f, x + w / 2, y, 1.4f, 0xFF7A5232);
        quad(x - w / 2 + 0.3f, y - 1.6f, x + w / 2 - 0.2f, y - 1.6f, x + w / 2 + 1f, y - 2.2f, x - w / 2 + 1.1f, y - 2.2f, fill);
    }

    private void hay(float x, float y, float s) {
        oval(x - 3 * s, y - 0.6f, x + 3.6f * s, y + 0.9f, 0x40000000);
        box(x - 2.6f * s, y - 2.8f * s, x + 2.6f * s, y, 1.8f * s, 0xFFD8B850);
        for (int k = 0; k < 14; k++) {
            float hx = x + rn(-2.5f, 2.5f) * s, hy = y - rn(0.2f, 2.6f) * s;
            ln(hx, hy, hx + rn(0.5f, 1.4f) * s, hy + rn(-0.2f, 0.2f), 0.18f, jit(0xFFB89A38, 0.15f));
        }
        ln(x - 0.9f * s, y - 2.8f * s, x - 0.9f * s, y, 0.25f, 0xFF8A6A2A);
        ln(x + 0.9f * s, y - 2.8f * s, x + 0.9f * s, y, 0.25f, 0xFF8A6A2A);
    }

    private void hayStack(float x, float y, float s) {
        oval(x - 4 * s, y - 0.8f, x + 5 * s, y + 1.2f, 0x40000000);
        pa.reset();
        pa.moveTo(x - 4 * s, y);
        pa.quadTo(x - 4 * s, y - 7 * s, x, y - 8 * s);
        pa.quadTo(x + 4 * s, y - 7 * s, x + 4 * s, y);
        pa.close();
        p.setColor(0xFFC8A848);
        c.drawPath(pa, p);
        c.save();
        c.clipPath(pa);
        for (int k = 0; k < 50; k++) {
            float hx = x + rn(-4, 4) * s, hy = y - rn(0, 8) * s;
            ln(hx, hy, hx + rn(-0.6f, 0.6f), hy + rn(0.8f, 1.6f), 0.2f, jit(r.nextBoolean() ? 0xFFE0C060 : 0xFF9C7E30, 0.1f));
        }
        oval(x + 0.5f * s, y - 8 * s, x + 6 * s, y + 1, 0x30000000);
        c.restore();
        ln(x, y - 8 * s, x + 0.3f, y - 9.6f * s, 0.35f, 0xFF6E4C30);
    }

    private void chicken(float x, float y, boolean brown) {
        int b = brown ? 0xFFA0643A : 0xFFF5F0E6;
        oval(x - 1.1f, y - 0.2f, x + 1.3f, y + 0.4f, 0x40000000);
        oval(x - 1.2f, y - 1.9f, x + 0.9f, y - 0.2f, b);
        tri(x - 1.1f, y - 1.6f, x - 1.9f, y - 2.6f, x - 0.7f, y - 1.4f, sh(b, 0.85f));
        circ(x + 0.8f, y - 2.1f, 0.6f, b);
        circ(x + 0.8f, y - 2.8f, 0.3f, 0xFFE53935);
        tri(x + 1.3f, y - 2.2f, x + 1.9f, y - 2f, x + 1.3f, y - 1.9f, 0xFFFFB300);
        circ(x + 0.95f, y - 2.2f, 0.12f, 0xFF111111);
        ln(x - 0.2f, y - 0.3f, x - 0.3f, y + 0.3f, 0.15f, 0xFFFFB300);
        ln(x + 0.3f, y - 0.3f, x + 0.4f, y + 0.3f, 0.15f, 0xFFFFB300);
    }

    private void pig(float x, float y, float s) {
        int b = 0xFFF2A5A8;
        oval(x - 2.4f * s, y - 0.3f, x + 2.6f * s, y + 0.6f, 0x40000000);
        oval(x - 2.2f * s, y - 2.6f * s, x + 1.8f * s, y - 0.4f * s, b);
        circ(x + 2f * s, y - 1.6f * s, 1f * s, b);
        oval(x + 2.6f * s, y - 1.8f * s, x + 3.3f * s, y - 1.1f * s, 0xFFE88A90);
        tri(x + 1.6f * s, y - 2.4f * s, x + 2.2f * s, y - 3.1f * s, x + 2.3f * s, y - 2.3f * s, 0xFFE88A90);
        circ(x + 2.2f * s, y - 1.9f * s, 0.15f, 0xFF111111);
        for (float lx : new float[]{-1.6f, -0.6f, 0.6f, 1.3f}) rect(x + lx * s, y - 0.7f * s, x + (lx + 0.45f) * s, y, sh(b, 0.85f));
        ln(x - 2.2f * s, y - 1.8f * s, x - 2.8f * s, y - 2.3f * s, 0.2f, sh(b, 0.85f));
        oval(x - 1.6f * s, y - 1.6f * s, x - 0.2f * s, y - 0.8f * s, 0x40704020);
    }

    private void hangSign(float x, float y, int ware, int tool) {
        ln(x, y, x + 4.5f, y, 0.45f, IRON);
        ln(x + 0.2f, y + 1.6f, x + 1.8f, y, 0.25f, IRON);
        ln(x + 1.2f, y, x + 1.2f, y + 0.8f, 0.15f, IRON);
        ln(x + 4f, y, x + 4f, y + 0.8f, 0.15f, IRON);
        rrect(x + 0.6f, y + 0.8f, x + 4.6f, y + 4.4f, 0.5f, 0xFFB08350);
        rrect(x + 0.9f, y + 1.1f, x + 4.3f, y + 4.1f, 0.4f, 0xFFE8D8B0);
        if (ware >= 0) ren.ware(c, ware, x + 2.6f, y + 3.2f, 0.4f);
    }

    private void ladder(float x1, float y1, float x2, float y2) {
        float ox = 1.1f;
        ln(x1, y1, x2, y2, 0.35f, 0xFF8D6440);
        ln(x1 + ox, y1, x2 + ox, y2, 0.35f, 0xFF8D6440);
        int n = (int) (Math.hypot(x2 - x1, y2 - y1) / 1.6f);
        for (int k = 1; k < n; k++) {
            float f = k / (float) n;
            ln(x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, x1 + ox + (x2 - x1) * f, y1 + (y2 - y1) * f, 0.25f, 0xFFA07A50);
        }
    }

    private void stoneBlocks(float x, float y) {
        box(x - 3, y - 2.4f, x + 0.5f, y, 2, jit(0xFFB4ADA0, 0.05f));
        box(x + 0.8f, y - 2.2f, x + 4, y, 2, jit(0xFFA8A194, 0.05f));
        box(x - 1.5f, y - 4.6f, x + 2f, y - 2.4f, 2, jit(0xFFBCB5A8, 0.05f));
    }

    private void rubble(float x, float y, int n, int col) {
        for (int k = 0; k < n; k++) {
            float sx = x + rn(-3, 3), sy = y + rn(-1, 1.2f), s = rn(0.4f, 1f);
            oval(sx - s, sy - s * 0.7f, sx + s, sy + s * 0.4f, jit(col, 0.12f));
            oval(sx - s * 0.6f, sy - s * 0.65f, sx + s * 0.2f, sy - s * 0.2f, sh(col, 1.2f));
        }
    }

    private void heap(float x, float y, float w, float h, int col) {
        oval(x - w - 0.5f, y - 0.8f, x + w + 1.5f, y + 1, 0x40000000);
        pa.reset();
        pa.moveTo(x - w, y);
        pa.quadTo(x - w * 0.3f, y - h * 1.3f, x + w * 0.2f, y - h);
        pa.quadTo(x + w * 0.8f, y - h * 0.6f, x + w, y);
        pa.close();
        p.setColor(col);
        c.drawPath(pa, p);
        c.save();
        c.clipPath(pa);
        for (int k = 0; k < (int) (w * h * 1.2f); k++) {
            float sx = x + rn(-w, w), sy = y - rn(0, h), s = rn(0.3f, 0.7f);
            circ(sx, sy, s, jit(col, 0.22f));
            circ(sx - s * 0.3f, sy - s * 0.3f, s * 0.35f, sh(col, 1.35f));
        }
        c.restore();
    }

    private void tool(float x, float y, int t, float s) {
        ren.ware(c, t, x, y, s);
    }

    private void bench(float x, float y, float w) {
        rect(x - w / 2, y - 2, x + w / 2, y - 1.4f, 0xFF9A6E44);
        rect(x - w / 2, y - 2, x + w / 2, y - 1.8f, 0xFFB88A5A);
        rect(x - w / 2 + 0.5f, y - 1.4f, x - w / 2 + 1, y, 0xFF6E4C30);
        rect(x + w / 2 - 1, y - 1.4f, x + w / 2 - 0.5f, y, 0xFF6E4C30);
    }

    private void sapling(float x, float y, float s) {
        ln(x, y, x, y - 2.5f * s, 0.25f, 0xFF6E4C30);
        circ(x, y - 3f * s, 1.2f * s, jit(0xFF3E8A2A, 0.12f));
        circ(x - 0.4f * s, y - 3.4f * s, 0.6f * s, 0xFF6AAE44);
    }

    // ================= Türme =================

    /** Steinturm, gibt Höhe der Plattform zurück */
    private void tower(float x, float y, float w, float h, float d, int col, boolean pointed, int roofCol) {
        float l = x - w / 2, rr = x + w / 2, dy = d * SK;
        shadow(l, rr, y, d);
        sideBegin(rr, y);
        tex(0, -h, d, 0, STONE, col);
        rect(0, -h, d, 0, 0x45000000);
        sideEnd();
        tex(l, y - h, rr, y, STONE, col);
        // Sockel
        rect(l - 0.6f, y - 2.4f, rr + 0.2f, y, sh(col, 0.75f));
        quad(rr + 0.2f, y, rr + d + 0.4f, y - dy - 0.2f, rr + d + 0.4f, y - dy - 2.6f, rr + 0.2f, y - 2.4f, sh(col, 0.55f));
        ln(l - 0.6f, y - 2.4f, rr + 0.2f, y - 2.4f, 0.3f, sh(col, 1.15f));
        // Eckquader
        for (float yy = y - 2.4f; yy > y - h; yy -= 2.6f) {
            boolean alt = ((int) ((y - yy) / 2.6f)) % 2 == 0;
            rect(l, yy - 2.4f, l + (alt ? 2.2f : 1.3f), yy - 0.1f, sh(col, 1.12f));
            rect(rr - (alt ? 1.3f : 2.2f), yy - 2.4f, rr, yy - 0.1f, sh(col, 1.08f));
        }
        // Gesims
        if (!pointed) {
            box(l - 0.8f, y - h - 1.4f, rr + 0.8f, y - h, d + 0.4f, sh(col, 1.05f));
            for (float xx = l; xx < rr; xx += 1.6f) rect(xx, y - h, xx + 0.8f, y - h + 0.8f, sh(col, 0.6f));
            // Plattform und Zinnen
            float tl = l - 0.8f, tr = rr + 0.8f, tt = y - h - 1.4f, dd = d + 0.4f, ddy = dd * SK;
            quad(tl, tt, tr, tt, tr + dd, tt - ddy, tl + dd, tt - ddy, 0xFF5E5850);
            int mer = 5;
            float mw = (tr - tl) / (mer * 2 - 1);
            // hintere Zinnen
            for (int k = 0; k < mer; k++) {
                float ml = tl + dd + k * 2 * mw;
                box(ml, tt - ddy - 3f, ml + mw, tt - ddy, 0.8f, sh(col, 0.9f));
            }
            // seitliche Zinnen
            for (int k = 0; k < 3; k++) {
                float f = (k * 2 + 0.5f) / 5.5f;
                float mx = tr + dd * f, my = tt - ddy * f;
                quad(mx, my, mx + dd * 0.18f, my - ddy * 0.18f, mx + dd * 0.18f, my - ddy * 0.18f - 3f, mx, my - 3f, sh(col, 0.68f));
                rect(mx - 0.7f, my - 3f, mx, my, sh(col, 0.85f));
            }
            for (int k = 0; k < mer; k++) {
                float ml = tl + k * 2 * mw;
                box(ml, tt - 3f, ml + mw, tt, 0.8f, jit(col, 0.05f));
                ln(ml, tt - 3f, ml + mw, tt - 3f, 0.25f, sh(col, 1.25f));
            }
        } else {
            float ax0 = x + d * 0.5f, ay0 = y - h - dy * 0.5f - w * 1.15f;
            rect(l - 0.6f, y - h - 1f, rr + 0.6f, y - h, sh(col, 1.1f));
            quad(rr + 0.6f, y - h, rr + d + 0.8f, y - h - dy, rr + d + 0.8f, y - h - dy - 1f, rr + 0.6f, y - h - 1f, sh(col, 0.75f));
            float bl = l - 1.4f, br = rr + 1.4f, bb = y - h - 0.6f;
            // Seitenfläche des Turmdachs
            pa.reset();
            pa.moveTo(br, bb);
            pa.lineTo(br + d + 0.6f, bb - dy);
            pa.lineTo(ax0, ay0);
            pa.close();
            scallops(pa, bl, ay0, br + d + 1, bb + 1, sh(roofCol, 0.72f));
            pa.reset();
            pa.moveTo(bl, bb);
            pa.lineTo(br, bb);
            pa.lineTo(ax0, ay0);
            pa.close();
            scallops(pa, bl, ay0, br + d + 1, bb + 1, roofCol);
            ln(bl, bb, br, bb, 0.6f, sh(roofCol, 0.5f));
            ln(br, bb, ax0, ay0, 0.5f, sh(roofCol, 0.55f));
            // Gauben-Luke
            tri(x - 1.6f, bb - 0.5f, x + 1.6f, bb - 0.5f, x, bb - 3.5f, sh(roofCol, 0.85f));
            rect(x - 1, bb - 2.4f, x + 1, bb - 0.5f, 0xFF201A16);
            // Spitze
            ln(ax0, ay0, ax0, ay0 - 2, 0.35f, 0xFFB09040);
            circ(ax0, ay0 - 0.2f, 0.5f, 0xFFB09040);
        }
    }

    /** Fläche mit Schuppen (Turmdächer) */
    private void scallops(Path shape, float l, float t, float rr, float b, int col) {
        c.save();
        c.clipPath(shape);
        rect(l, t, rr, b, sh(col, 0.6f));
        int row = 0;
        for (float yy = t; yy < b + 1; yy += 1.1f, row++) {
            for (float xx = l - (row % 2) * 0.7f; xx < rr; xx += 1.4f) {
                int tc = jit(col, 0.1f);
                circ(xx, yy, 0.8f, sh(tc, 0.7f));
                circ(xx, yy - 0.15f, 0.7f, tc);
                circ(xx - 0.15f, yy - 0.35f, 0.3f, sh(tc, 1.2f));
            }
        }
        c.restore();
    }

    // ================= Gebäude =================

    void draw(Canvas cv, int type) {
        c = cv;
        r = new Random(type * 7919L + 13);
        switch (type) {
            case World.B_HQ: hq(); break;
            case World.B_WOOD: woodcutter(); break;
            case World.B_FOREST: forester(); break;
            case World.B_QUARRY: quarry(); break;
            case World.B_SAW: sawmill(); break;
            case World.B_GUARD: guard(); break;
            case World.B_TOWER: watchtower(); break;
            case World.B_FISH: fisher(); break;
            case World.B_HUNT: hunter(); break;
            case World.B_WELL: well(); break;
            case World.B_FARM: farm(); break;
            case World.B_MILL: mill(); break;
            case World.B_BAKERY: bakery(); break;
            case World.B_PIGS: pigs(); break;
            case World.B_BUTCHER: butcher(); break;
            case World.B_COAL:
            case World.B_IRON:
            case World.B_GOLD:
            case World.B_GRANITE: mine(type); break;
            case World.B_SMELTER: smelter(); break;
            case World.B_TOOLS: tools(); break;
            case World.B_STORE: store(); break;
            case World.B_SCOUT: scout(); break;
            case World.B_ARMORY: armory(); break;
            case World.B_FORT: fort(); break;
        }
    }

    private void hq() {
        ground(1, 1, 38, 9, 0xFF9C8462);
        // Pflasterweg vor dem Tor
        for (int k = 0; k < 18; k++) {
            float sx = rn(-4, 7), sy = rn(2, 8);
            oval(sx - 1, sy - 0.5f, sx + 1, sy + 0.5f, jit(0xFFAAA294, 0.1f));
        }
        // Mauer hinten
        tex(-20, -12, 22, -4, STONE, 0xFFADA38E);
        for (float xx = -20; xx < 22; xx += 2.4f) box(xx, -14f, xx + 1.3f, -12, 0.6f, 0xFFB5AB98);
        tower(-24.5f, -2, 13, 36, 6, 0xFFB8AE9C, false, 0);
        slit(-24.5f, -26, 3.5f);
        slit(-24.5f, -16, 3.5f);
        archWindow(-24.5f, -8, 2.6f, 3.4f);
        // Haupthaus
        house(1.5f, 2, 30, 22, 14, 13, STONE, 0xFFC8BCA4, TIMBER, SLATE, 0xFF4E5868);
        gableWindow(true);
        archWindow(-7.5f, -12, 3, 5);
        archWindow(10.5f, -12, 3, 5);
        archWindow(-7.5f, -2.5f, 2.6f, 3.6f);
        archWindow(10.5f, -2.5f, 2.6f, 3.6f);
        // Balkon über dem Tor
        box(-3.5f, -11.5f, 6.5f, -10.5f, 2, 0xFF8D6440);
        for (float xx = -3.2f; xx <= 6.2f; xx += 1.1f) rect(xx, -14.2f, xx + 0.3f, -11.5f, 0xFF6E4C30);
        rect(-3.5f, -14.6f, 6.5f, -14f, 0xFF8D6440);
        door(1.5f, 2, 7, 11, true, 0xFF6E4424);
        // Wappen
        oval(-1, -20, 4, -15, 0xFFD8C060);
        ring(1.5f, -17.5f, 2.3f, 0.3f, 0xFF8A6A20);
        torch(-3.5f, -2f);
        torch(6.5f, -2f);
        // Seitenfenster
        sideBegin(16.5f, 2);
        archWindow(4, -12, 2.2f, 4);
        archWindow(9, -12, 2.2f, 4);
        archWindow(6.5f, -4, 2.2f, 3.4f);
        sideEnd();
        chimney(10, -28.5f, 2.6f, 6, BRICK);
        tower(25.6f, 0, 12, 30, 6, 0xFFB0A692, true, 0xFF4E5868);
        slit(25.6f, -20, 3.5f);
        archWindow(25.6f, -11, 2.6f, 3.6f);
        door(25.6f, 0, 3.4f, 5, true, 0xFF6E4424);
        // Vorplatz
        barrel(-18, 5, 1);
        barrel(-14.5f, 6, 0.9f);
        crate(-21, 7.5f, 0.9f);
        sack(-11, 6.5f, 0.9f, 0xFFD8C8A0);
        cart(17, 7, World.WARE_BOARD);
        bush(-31, 4, 1.1f, true);
        bush(34, 4, 1, false);
        lantern(-5.5f, 6, 7);
        flowers(32, 7, 6);
        stoneBlocks(-28, 8);
    }

    private void woodcutter() {
        ground(0, 1, 20, 6, 0xFF8E7656);
        // Holzschuppen links
        float sl = -18, sr = -7;
        for (float xx : new float[]{sl, sr}) rect(xx - 0.4f, -8, xx + 0.4f, 0, 0xFF5A3C26);
        woodpile(sl + 1.2f, 0, 5, 4, 1.15f);
        quad(sl - 1.5f, -8, sr + 1.5f, -8, sr + 3.5f, -11.5f, sl + 0.5f, -11.5f, 0xFF7A5232);
        for (float xx = sl - 1; xx < sr + 1.5f; xx += 1.5f) ln(xx, -8, xx + 2, -11.5f, 0.2f, 0xFF5A3A20);
        ln(sl - 1.5f, -8, sr + 1.5f, -8, 0.5f, 0xFF4A3020);
        house(3, 0, 18, 11, 8.5f, 9, LOG, 0xFF8A5E36, PLANK, SHINGLE, 0xFF5A4A3E);
        window(-1.5f, -6, 2.8f, 2.6f, 0xFF3E6A3A, false);
        door(6.5f, 0, 3.4f, 7.5f, false, 0xFF6E4424);
        gableWindow(false);
        sideBegin(12, 0);
        window(4.5f, -6, 2.4f, 2.4f, 0, false);
        sideEnd();
        // Geweih-Säge an der Wand
        ln(-5.5f, -9.5f, -0.2f, -9.5f, 0.3f, 0xFF8A8A92);
        for (float xx = -5.3f; xx < -0.3f; xx += 0.5f) tri(xx, -9.4f, xx + 0.25f, -8.9f, xx + 0.5f, -9.4f, 0xFF8A8A92);
        chimney(8.5f, -16, 2.2f, 6, STONE);
        chopBlock(14, 4);
        logSide(-4, 4, 6, 1.1f);
        bush(20, 2, 0.9f, false);
        rect(-21.5f, 3.6f, -18.5f, 4.4f, 0xFF7A5232);
        logEnd(-20, 3, 1.4f);
    }

    private void forester() {
        ground(0, 1, 20, 6, 0xFF8A7252);
        // Baumschule
        fence(-21, 5, -8, 5, 4);
        for (int k = 0; k < 3; k++) {
            for (int j = 0; j < 2; j++) sapling(-19 + k * 4 + j * 2, 1 + j * 2.5f, 0.7f + 0.2f * r.nextFloat());
        }
        fence(-21, -3, -8, -3, 4);
        house(3, 0, 18, 11, 9, 9, TIMBER, CREAM, TIMBER, THATCH, 0xFFC8A458);
        window(-1.5f, -5.5f, 2.6f, 2.6f, 0xFF4E7A3A, true);
        door(6.5f, 0, 3.4f, 7.5f, false, 0xFF5E8A4A);
        gableWindow(false);
        sideBegin(12, 0);
        window(4.5f, -5.5f, 2.4f, 2.4f, 0xFF4E7A3A, false);
        sideEnd();
        chimney(7.5f, -16, 2, 5, STONE);
        // Gießkanne und Spaten
        rect(13, 2, 15.5f, 4.5f, 0xFF7A9AA8);
        ln(15.5f, 2.5f, 17.5f, 1.5f, 0.35f, 0xFF7A9AA8);
        ring(13.8f, 2.2f, 1, 0.3f, 0xFF6A8A98);
        tool(17.5f, -1, World.T_SHOVEL, 0.6f);
        // Bienenstock
        rect(16, -3, 16.4f, 0, 0xFF6E4C30);
        oval(14.6f, -6.5f, 17.8f, -2.8f, 0xFFD8A848);
        for (float yy = -6; yy < -3; yy += 0.8f) ln(14.8f, yy, 17.6f, yy, 0.2f, 0xFFA87C28);
        circ(16.2f, -3.6f, 0.4f, 0xFF3E2A1C);
        bush(19, 3, 0.9f, true);
        flowers(10, 5, 5);
    }

    private void quarry() {
        ground(0, 1, 20, 6, 0xFF9A9284);
        rubble(-8, 3, 12, 0xFFA8A194);
        // Holzkran
        ln(-20, 2, -16, -16, 0.7f, 0xFF6E4C30);
        ln(-12, 2, -16, -16, 0.7f, 0xFF6E4C30);
        ln(-16, -16, -7, -14, 0.6f, 0xFF7A5232);
        ln(-19, -4, -13, -4, 0.4f, 0xFF7A5232);
        ln(-8, -14, -8, -5, 0.15f, IRON);
        circ(-16, -16, 0.6f, IRON);
        circ(-8, -14, 0.5f, IRON);
        box(-9.4f, -5, -6.6f, -3, 1.5f, 0xFFB4ADA0);
        house(5, 0, 16, 10, 7.5f, 8, STONE, 0xFFA8A49C, PLANK, BOARDS, 0xFF7A6A58);
        window(1.5f, -5.5f, 2.4f, 2.4f, 0xFF6E5A44, false);
        door(8, 0, 3.2f, 7, false, 0xFF6E5A44);
        chimney(8.5f, -15, 2, 5, STONE);
        stoneBlocks(-13, 5);
        wheelbarrow(17, 4, World.WARE_STONE);
        tool(-2, 2, World.T_PICK, 0.6f);
        rubble(14, 7, 6, 0xFFB4ADA0);
    }

    private void sawmill() {
        ground(0, 1, 30, 7, 0xFF907656);
        // Bretterstapel links
        for (int k = 0; k < 5; k++) {
            float yy = 2 - k * 1.3f;
            box(-31, yy - 0.9f, -20, yy, 3, jit(0xFFD8B47E, 0.05f));
            if (k % 2 == 1) {
                rect(-30, yy - 0.3f, -29.3f, yy, 0xFF8A6440);
                rect(-21.5f, yy - 0.3f, -20.8f, yy, 0xFF8A6440);
            }
        }
        house(2, 0, 28, 15, 11, 13, PLANK, 0xFF9A6A3E, PLANK, TILE, 0xFFA8402C);
        // offener Sägeplatz
        rect(-11.5f, -10, -4.5f, 0, 0xFF2A1E16);
        rect(-11.5f, -10, -4.5f, -9, 0xFF4A3020);
        rect(-12, -10.5f, -11.3f, 0, 0xFF4A3020);
        rect(-4.7f, -10.5f, -4f, 0, 0xFF4A3020);
        // Sägetisch
        box(-11, -3.2f, -5, -2.4f, 1.5f, 0xFFB08350);
        rect(-10.5f, -2.4f, -10, 0, 0xFF6E4C30);
        rect(-6, -2.4f, -5.5f, 0, 0xFF6E4C30);
        window(3, -9.5f, 2.6f, 2.6f, 0, false);
        window(10, -9.5f, 2.6f, 2.6f, 0, false);
        door(8, 0, 4.5f, 7.5f, false, 0xFF7A4A2A);
        gableWindow(true);
        sideBegin(16, 0);
        window(4, -9.5f, 2.4f, 2.6f, 0, false);
        window(9.5f, -9.5f, 2.4f, 2.6f, 0, false);
        sideEnd();
        chimney(12.5f, -26, 2.4f, 6, BRICK);
        // Stämme rechts
        logSide(14, 26, 6, 1.2f);
        logSide(16, 27, 3.8f, 1.1f);
        // Sägemehl
        heap(-8, 4, 3.5f, 1.6f, 0xFFE0C890);
        crate(-17, 7, 0.8f);
        lantern(-14, 2, 9);
    }

    private void guard() {
        ground(0, 1, 18, 6, 0xFF8E7E64);
        // Holzanbau
        house(-9, 2, 10, 8, 6, 7, PLANK, 0xFF8A5E36, PLANK, SHINGLE, 0xFF6A4A36);
        door(-9, 2, 3, 6, false, 0xFF6E4424);
        tower(2, 0, 13, 23, 7, 0xFFB5AB98, false, 0);
        slit(2, -16, 3.2f);
        archWindow(2, -8, 2.4f, 3.2f);
        door(2, 0, 3.6f, 5.5f, true, 0xFF6E4424);
        // Schild mit Spitze
        pa.reset();
        pa.moveTo(-1.5f, -21);
        pa.lineTo(5.5f, -21);
        pa.lineTo(5.5f, -18.5f);
        pa.quadTo(5.5f, -15.5f, 2, -14.2f);
        pa.quadTo(-1.5f, -15.5f, -1.5f, -18.5f);
        pa.close();
        p.setColor(0xFFD8C060);
        c.drawPath(pa, p);
        ln(2, -21, 2, -14.5f, 0.4f, 0xFF8A6A20);
        ln(-1.5f, -19, 5.5f, -19, 0.4f, 0xFF8A6A20);
        // Speerständer
        for (int k = 0; k < 3; k++) {
            ln(12 + k * 1.3f, 4, 13 + k * 0.9f, -6, 0.3f, 0xFF8D6440);
            tri(12.7f + k * 0.9f, -6, 13.3f + k * 0.9f, -6, 13 + k * 0.9f, -7.8f, 0xFFB0B4B8);
        }
        rect(11, 1, 16, 1.6f, 0xFF6E4C30);
        barrel(-16, 6, 0.8f);
        torch(-3, 1);
        torch(7, 1);
    }

    private void watchtower() {
        ground(0, 1, 26, 7, 0xFF8E7E64);
        // Ringmauer hinten
        tex(-24, -9, 26, -3, STONE, 0xFFA89E8A);
        for (float xx = -24; xx < 26; xx += 2.4f) box(xx, -11f, xx + 1.3f, -9, 0.6f, 0xFFB0A692);
        house(-14, 3, 13, 10, 7, 9, TIMBER, CREAM, TIMBER, TILE, 0xFF9A3A2A);
        window(-14, -3, 2.4f, 2.4f, 0xFF7A3A2A, true);
        door(-14, 3, 3, 0.1f + 6, false, 0xFF6E4424);
        tower(3.6f, 1, 16, 34, 8, 0xFFA89E8A, false, 0);
        slit(3.6f, -27, 3.5f);
        slit(3.6f, -17, 3.5f);
        archWindow(-0.5f, -9, 2.4f, 3.2f);
        archWindow(7.7f, -9, 2.4f, 3.2f);
        door(3.6f, 1, 4.4f, 7, true, 0xFF6E4424);
        sideBegin(11.6f, 1);
        slit(4, -22, 3.5f);
        slit(4, -10, 3.5f);
        sideEnd();
        // Fackeln, Fässer, Zielscheibe
        torch(-1.5f, 0);
        torch(9, 0);
        barrel(18, 5, 0.9f);
        crate(22, 7, 0.8f);
        rect(-25, -1, -24.5f, 6, 0xFF6E4C30);
        circ(-24.7f, -1.5f, 2.6f, 0xFFF5F0E6);
        circ(-24.7f, -1.5f, 1.8f, 0xFFC62828);
        circ(-24.7f, -1.5f, 0.9f, 0xFFF5F0E6);
        circ(-24.7f, -1.5f, 0.4f, 0xFFC62828);
    }

    private void fisher() {
        ground(0, 1, 20, 6, 0xFFB8A27A);
        // Netzgestell
        for (float xx : new float[]{-20, -10}) rect(xx - 0.35f, -12, xx + 0.35f, 2, 0xFF6E4C30);
        ln(-20.5f, -12, -9.5f, -12, 0.45f, 0xFF7A5232);
        c.save();
        c.clipRect(-20, -12, -10, -1);
        for (float xx = -22; xx < -8; xx += 1.1f) {
            ln(xx, -12, xx + 3, -1, 0.12f, 0xFF8A7A5A);
            ln(xx + 3, -12, xx, -1, 0.12f, 0xFF8A7A5A);
        }
        c.restore();
        pa.reset();
        pa.moveTo(-20, -1);
        pa.quadTo(-15, 1.5f, -10, -1);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.3f);
        p.setColor(0xFF8A7A5A);
        c.drawPath(pa, p);
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 4; k++) circ(-19 + k * 2.7f, -1.2f + (k == 1 || k == 2 ? 0.9f : 0), 0.4f, 0xFFE8A030);
        house(4, 0, 17, 10.5f, 8.5f, 9, PLANK, 0xFF7A8A8E, PLANK, SHINGLE, 0xFF4E6478);
        window(0.5f, -5.5f, 2.6f, 2.4f, 0xFF3E6A8A, false);
        door(7.5f, 0, 3.2f, 7.5f, false, 0xFF4E6478);
        gableWindow(true);
        chimney(8, -15.5f, 2, 5, STONE);
        // Fische an der Leine
        ln(-3.5f, -9.5f, 2.5f, -9.5f, 0.15f, 0xFF8A7A5A);
        for (int k = 0; k < 3; k++) {
            float fx = -2.6f + k * 2;
            ln(fx, -9.5f, fx, -8.8f, 0.12f, 0xFF8A7A5A);
            oval(fx - 0.45f, -8.8f, fx + 0.45f, -6.5f, 0xFF9AB0B8);
            tri(fx - 0.5f, -6.6f, fx + 0.5f, -6.6f, fx, -5.8f, 0xFF7A9098);
        }
        // umgedrehtes Boot
        pa.reset();
        pa.moveTo(8, 7);
        pa.quadTo(14, 2.5f, 23, 4.5f);
        pa.lineTo(22, 6);
        pa.quadTo(15, 7.8f, 8, 7);
        pa.close();
        p.setColor(0xFF6E4C30);
        c.drawPath(pa, p);
        ln(9, 6.3f, 22, 4.9f, 0.2f, 0xFF8D6440);
        ln(9.5f, 5.4f, 21.5f, 4.6f, 0.2f, 0xFF8D6440);
        ln(12, 8, 21, 2, 0.35f, 0xFFB08860);
        oval(19.6f, 1.2f, 22, 2.6f, 0xFFB08860);
        barrel(-4, 5, 0.8f);
        // Reuse
        oval(-8.5f, 4.5f, -5.5f, 7f, 0xFFA88A58);
        for (float xx = -8; xx < -5.5f; xx += 0.6f) ln(xx, 4.6f, xx, 6.9f, 0.12f, 0xFF7A5A30);
    }

    private void hunter() {
        ground(0, 1, 20, 6, 0xFF7E6A4C);
        // Fellrahmen
        ln(-20, 2, -17, -11, 0.5f, 0xFF6E4C30);
        ln(-10, 2, -13, -11, 0.5f, 0xFF6E4C30);
        ln(-19.6f, -9.5f, -10.4f, -9.5f, 0.4f, 0xFF6E4C30);
        ln(-20, -1, -10, -1, 0.4f, 0xFF6E4C30);
        pa.reset();
        pa.moveTo(-17.5f, -8.8f);
        pa.quadTo(-15, -10, -12.5f, -8.8f);
        pa.quadTo(-12, -5, -12.8f, -1.6f);
        pa.quadTo(-15, -0.8f, -17.2f, -1.6f);
        pa.quadTo(-18, -5, -17.5f, -8.8f);
        pa.close();
        p.setColor(0xFFA0744A);
        c.drawPath(pa, p);
        oval(-16.5f, -7.5f, -13.5f, -3, 0xFFB88A5E);
        for (float[] q : new float[][]{{-17.5f, -8.8f, -19.6f, -9.4f}, {-12.5f, -8.8f, -10.5f, -9.4f}, {-17.2f, -1.6f, -19.6f, -1.1f}, {-12.8f, -1.6f, -10.4f, -1.1f}})
            ln(q[0], q[1], q[2], q[3], 0.12f, 0xFFD8C8A0);
        house(4, 0, 17, 10.5f, 8.5f, 9, LOG, 0xFF6E4A2E, PLANK, SHINGLE, 0xFF4E5A36);
        window(0.5f, -5.5f, 2.6f, 2.4f, 0xFF5A4A2A, false);
        door(7.5f, 0, 3.2f, 7.5f, false, 0xFF5A3C22);
        chimney(8.5f, -15.5f, 2.2f, 6, STONE);
        // Geweih über der Tür (am Giebel)
        float gx = 4, gy = -14;
        oval(gx - 1, gy - 0.4f, gx + 1, gy + 1.6f, 0xFF8A6A48);
        for (int s = -1; s <= 1; s += 2) {
            ln(gx + s * 0.5f, gy, gx + s * 2.5f, gy - 3, 0.45f, 0xFFE8DCC8);
            ln(gx + s * 1.5f, gy - 1.5f, gx + s * 3.3f, gy - 1.8f, 0.35f, 0xFFE8DCC8);
            ln(gx + s * 2.1f, gy - 2.4f, gx + s * 1.8f, gy - 4, 0.3f, 0xFFE8DCC8);
        }
        // Bogen und Köcher
        rect(13, -2, 13.4f, 4, 0xFF5A3C22);
        tool(14.5f, 0, World.T_BOW, 0.65f);
        rect(16.5f, -1.5f, 18, 3.5f, 0xFF7A4A2A);
        for (int k = 0; k < 3; k++) ln(16.8f + k * 0.5f, -1.5f, 16.6f + k * 0.6f, -3.2f, 0.15f, 0xFFD8C8A0);
        woodpile(-6, 6, 3, 2, 0.9f);
        bush(20, 3, 1, false);
        bush(-22, 4, 0.9f, false);
    }

    private void well() {
        ground(0, 1, 14, 5, 0xFF8E7E64);
        float rad = 6.5f;
        // Rückwand des Brunnens
        oval(-rad, -7.5f, rad, -3.5f, 0xFF7A746A);
        oval(-rad + 1, -7f, rad - 1, -4f, 0xFF1E3A58);
        oval(-rad + 2, -6.5f, rad - 3, -4.6f, 0xFF2D5D8A);
        ln(-2, -5.8f, 1, -5.2f, 0.3f, 0x50FFFFFF);
        // Steinring vorne
        c.save();
        pa.reset();
        pa.moveTo(-rad, -5.5f);
        pa.quadTo(0, -3.2f, rad, -5.5f);
        pa.lineTo(rad, 0);
        pa.quadTo(0, 2.6f, -rad, 0);
        pa.close();
        c.clipPath(pa);
        stones(-rad, -6, rad, 2, 0xFF9C968C);
        rect(rad * 0.2f, -6, rad, 2, 0x30000000);
        c.restore();
        // Pfosten, Dach, Winde
        for (int s = -1; s <= 1; s += 2) {
            rect(s * 5.8f - 0.5f, -17, s * 5.8f + 0.5f, -4, 0xFF6D4C41);
            ln(s * 5.8f - 0.2f, -17, s * 5.8f - 0.2f, -4, 0.15f, 0xFF8D6C51);
        }
        rect(-5.3f, -12.6f, 5.3f, -11.6f, 0xFF7A5232);
        ring(0, -12.1f, 1.2f, 0.4f, 0xFF5A3C26);
        ln(5.8f, -12.1f, 7.8f, -12.1f, 0.35f, IRON);
        ln(7.8f, -12.1f, 7.8f, -10.2f, 0.45f, 0xFF7A5232);
        ln(0, -11, 0, -6.5f, 0.2f, 0xFFB09A70);
        quad(-0.9f, -6.8f, 0.9f, -6.8f, 0.7f, -4.9f, -0.7f, -4.9f, 0xFF8A6A48);
        rect(-0.9f, -6.6f, 0.9f, -6.3f, IRON);
        // Dach
        float rl = -8.5f, rr = 8.5f, ry = -16.5f, rt = -23.5f;
        tri(rl + 1, ry, rr - 1, ry, 0, rt + 1, 0xFF6E4C30);
        for (float xx = rl + 1.8f; xx < rr - 1; xx += 1.5f) ln(xx, ry, xx, ry - 0.2f - (1 - Math.abs(xx) / (rr - 1)) * 6, 0.15f, 0xFF4A3020);
        rect(rl + 1, ry - 0.6f, rr - 1, ry, 0xFF5A3C26);
        roofPlane(0, rt, rr + 0.8f, ry + 0.6f, 4.5f, SHINGLE, 0xFF9E4B3A);
        ln(rl - 0.8f, ry + 0.6f, 0, rt, 0.9f, 0xFF4A2E1A);
        ln(0, rt, rr + 0.8f, ry + 0.6f, 0.9f, 0xFF4A2E1A);
        // Eimer am Boden, Trog
        quad(7.5f, 1.5f, 10.5f, 1.5f, 10.1f, 4.5f, 7.9f, 4.5f, 0xFF8A6A48);
        oval(7.5f, 1, 10.5f, 2, 0xFF2D5D8A);
        rect(7.6f, 2.5f, 10.4f, 2.9f, IRON);
        trough(-10, 5, 5, 0xFF2D5D8A);
        flowers(12, 5, 4);
    }

    private void farm() {
        ground(0, 1, 30, 7, 0xFF907A52);
        // Scheune links
        house(-15, -1, 18, 13, 11, 12, PLANK, 0xFF9A3A2C, PLANK, THATCH, 0xFFC8A458);
        // großes Scheunentor
        float dl = -19.5f, dr = -10.5f;
        rect(dl - 0.6f, -10.5f, dr + 0.6f, -1, 0xFFE8DCC0);
        rect(dl, -10, dr, -1, 0xFF7A2A20);
        for (float xx = dl; xx < dr; xx += 1.5f) ln(xx, -10, xx, -1, 0.15f, 0xFF5A1E16);
        ln(dl, -10, (dl + dr) / 2, -1, 0.5f, 0xFFE8DCC0);
        ln((dl + dr) / 2, -10, dl, -1, 0.5f, 0xFFE8DCC0);
        ln((dl + dr) / 2, -10, dr, -1, 0.5f, 0xFFE8DCC0);
        ln(dr, -10, (dl + dr) / 2, -1, 0.5f, 0xFFE8DCC0);
        ln((dl + dr) / 2, -10, (dl + dr) / 2, -1, 0.4f, 0xFFE8DCC0);
        // Heuluke
        rect(-17, -19, -13, -15, 0xFF2A1E16);
        for (int k = 0; k < 10; k++) {
            float hx = rn(-17, -13);
            ln(hx, -15.5f, hx + rn(-0.8f, 0.8f), -14.5f + rn(0, 1), 0.25f, 0xFFD8B850);
        }
        rect(-17.5f, -19.6f, -12.5f, -19, 0xFFE8DCC0);
        // Wohnhaus rechts
        house(10, 2, 20, 12, 10, 11, TIMBER, CREAM, TIMBER, TILE, 0xFFA8502E);
        window(4.8f, -5, 2.6f, 2.6f, 0xFF2E6A3A, true);
        window(15.2f, -5, 2.6f, 2.6f, 0xFF2E6A3A, true);
        door(10, 2, 3.4f, 7, false, 0xFF2E6A3A);
        gableWindow(false);
        sideBegin(20, 2);
        window(5.5f, -5, 2.4f, 2.4f, 0xFF2E6A3A, false);
        sideEnd();
        chimney(17.5f, -20, 2.4f, 7, BRICK);
        // Hof
        hayStack(-27, 6, 0.8f);
        hay(-6, 7, 0.75f);
        sack(0.5f, 7, 0.8f, 0xFFE0D0A0);
        sack(3, 7.5f, 0.75f, 0xFFD8C890);
        wheel(25, 3, 2.6f);
        tool(22, 6, World.T_SCYTHE, 0.6f);
        chicken(-3, 3, false);
        chicken(-1, 5, true);
        chicken(4, 4.6f, false);
        chicken(-6, 2.6f, true);
        fence(21, 8, 31, 4, 3);
    }

    private void mill() {
        ground(0, 1, 16, 5.5f, 0xFF907A58);
        // Holländermühle, nach oben schmaler
        float bw = 9, tw = 5.8f, h = 27;
        shadow(-bw, bw, 0, 7);
        // Seitenfläche
        quad(bw, 0, bw + 4, -2.2f, tw + 2.5f, -h - 1.4f, tw, -h, 0xFF8A8070);
        pa.reset();
        pa.moveTo(-bw, 0);
        pa.lineTo(bw, 0);
        pa.lineTo(tw, -h);
        pa.lineTo(-tw, -h);
        pa.close();
        c.save();
        c.clipPath(pa);
        // senkrechte Schindeln
        for (float xx = -bw; xx < bw; xx += 1.3f) {
            int pc = jit(0xFFD8CCB4, 0.05f);
            rect(xx, -h, xx + 1.3f, 0, pc);
            rect(xx + 1.1f, -h, xx + 1.3f, 0, sh(pc, 0.75f));
        }
        for (float yy = -2; yy > -h; yy -= 2.2f) rect(-bw, yy, bw, yy + 0.25f, 0x30000000);
        rect(2, -h, bw, 0, 0x30000000);
        c.restore();
        // Steinsockel
        tex(-bw - 0.5f, -4.5f, bw + 0.5f, 0, STONE, 0xFFA8A194);
        quad(bw + 0.5f, 0, bw + 4.4f, -2.2f, bw + 4.4f, -6.7f, bw + 0.5f, -4.5f, 0xFF7A746A);
        // Galerie
        float gy = -11;
        float gl = -bw - 3, gr = bw + 2;
        box(gl, gy, gr, gy + 0.9f, 4, 0xFF7A5232);
        for (float xx = gl + 0.5f; xx < gr; xx += 1.5f) rect(xx, gy - 3, xx + 0.3f, gy, 0xFF6E4C30);
        rect(gl, gy - 3.3f, gr, gy - 2.7f, 0xFF8D6440);
        ln(gl + 2, gy + 0.9f, gl - 1, 0, 0.4f, 0xFF6E4C30);
        ln(gr - 2, gy + 0.9f, gr + 1, 0, 0.4f, 0xFF6E4C30);
        window(-1.8f, -18, 2, 2.4f, 0, false);
        window(1.8f, -7.5f, 2, 2.2f, 0, false);
        door(-2.5f, 0, 3, 6, true, 0xFF5D4037);
        door(0, gy, 2.6f, 4.5f, false, 0xFF5D4037);
        // Haube
        float ct = -h - 7;
        pa.reset();
        pa.moveTo(-tw - 1.5f, -h);
        pa.quadTo(-tw, ct, 0.5f, ct);
        pa.quadTo(tw + 1, ct, tw + 1.5f, -h);
        pa.close();
        p.setColor(0xFF6A4E3A);
        c.drawPath(pa, p);
        c.save();
        c.clipPath(pa);
        for (int k = 0; k < 120; k++) {
            float sx = rn(-tw - 2, tw + 2), sy = rn(ct, -h);
            ln(sx, sy, sx + rn(-0.3f, 0.3f), sy + rn(0.8f, 1.6f), 0.25f, jit(0xFF7A5A40, 0.15f));
        }
        oval(1, ct, tw + 6, -h + 3, 0x35000000);
        c.restore();
        rect(-tw - 1.5f, -h - 0.4f, tw + 1.5f, -h + 0.5f, 0xFF4A3424);
        // Achse
        circ(MILL_HUB[0], MILL_HUB[1], 1.6f, 0xFF4E342E);
        // Mehlsäcke
        sack(10, 5, 0.85f, 0xFFF0EAD8);
        sack(12.6f, 6.2f, 0.8f, 0xFFF0EAD8);
        sack(-11, 6, 0.8f, 0xFFE0C890);
        cart(-15, 3, World.WARE_GRAIN);
        flowers(15, 2, 3);
    }

    private void bakery() {
        ground(0, 1, 28, 7, 0xFF907656);
        // Backofen links
        float ox = -17, oy = 0;
        oval(ox - 8, oy - 1.5f, ox + 9, oy + 2.5f, 0x40000000);
        tex(ox - 7.5f, oy - 3.5f, ox + 7.5f, oy, STONE, 0xFF9C968C);
        pa.reset();
        pa.moveTo(ox - 7, oy - 3.5f);
        pa.quadTo(ox - 7, oy - 13, ox, oy - 13);
        pa.quadTo(ox + 7, oy - 13, ox + 7, oy - 3.5f);
        pa.close();
        c.save();
        c.clipPath(pa);
        tex(ox - 7, oy - 13, ox + 7, oy - 3.5f, BRICK, 0xFFB0603E);
        oval(ox - 2, oy - 15, ox + 12, oy - 2, 0x40000000);
        c.restore();
        // Ofenloch
        pa.reset();
        pa.moveTo(ox - 2.4f, oy - 1.5f);
        pa.lineTo(ox - 2.4f, oy - 4);
        pa.quadTo(ox - 2.4f, oy - 6.5f, ox, oy - 6.5f);
        pa.quadTo(ox + 2.4f, oy - 6.5f, ox + 2.4f, oy - 4);
        pa.lineTo(ox + 2.4f, oy - 1.5f);
        pa.close();
        p.setColor(0xFF2A1410);
        c.drawPath(pa, p);
        rect(ox - 3, oy - 1.8f, ox + 3, oy - 1, 0xFF8A847A);
        chimney(ox - 0.5f, oy - 12, 2.2f, 7, BRICK);
        // Holzvorrat für den Ofen
        woodpile(ox - 11, oy + 2, 2, 3, 0.85f);
        // Bäckerhaus
        house(6, 0, 24, 14, 11, 12, TIMBER, 0xFFF0DCC0, TIMBER, TILE, 0xFFB04A2E);
        window(0.5f, -8, 2.6f, 2.6f, 0xFF8A4A2A, true);
        window(11.5f, -8, 2.6f, 2.6f, 0xFF8A4A2A, true);
        // Ladenfenster mit Broten
        rect(-1.5f, -4, 3.5f, -1, 0xFF2A3A4C);
        rect(-2, -4.5f, 4, -4, DARKWOOD);
        box(-2.2f, -1, 4.2f, -0.3f, 1.2f, 0xFF9A6E44);
        ren.ware(c, World.WARE_BREAD, -0.2f, -2, 0.45f);
        ren.ware(c, World.WARE_BREAD, 2, -2, 0.45f);
        door(9.5f, 0, 3.4f, 7.5f, false, 0xFF8A4A2A);
        gableWindow(false);
        sideBegin(18, 0);
        window(5.5f, -8, 2.4f, 2.4f, 0xFF8A4A2A, false);
        sideEnd();
        // Brezel-Schild
        ln(14, -11, 19, -11, 0.4f, IRON);
        ln(14.2f, -9.4f, 15.8f, -11, 0.25f, IRON);
        ln(17.5f, -11, 17.5f, -10, 0.15f, IRON);
        float bx0 = 17.5f, by0 = -8.4f;
        ring(bx0 - 0.9f, by0, 1, 0.55f, 0xFFC8862E);
        ring(bx0 + 0.9f, by0, 1, 0.55f, 0xFFC8862E);
        ln(bx0 - 1.6f, by0 + 0.9f, bx0 + 1.6f, by0 + 0.9f, 0.55f, 0xFFC8862E);
        // Mehlsäcke und Bank mit Körben
        sack(22, 6, 0.85f, 0xFFF0EAD8);
        sack(24.5f, 4.5f, 0.8f, 0xFFF0EAD8);
        bench(1, 6, 7);
        oval(-1.5f, 3.2f, 1.5f, 4.6f, 0xFFB08A50);
        ren.ware(c, World.WARE_BREAD, 3, 3.6f, 0.4f);
        flowers(-6, 7, 4);
    }

    private void pigs() {
        ground(0, 1, 28, 7, 0xFF8A7252);
        // Stall
        house(10, -1, 20, 11, 9, 11, PLANK, 0xFFA07040, PLANK, BOARDS, 0xFF7A5A3A);
        rect(5, -7, 9, -1, 0xFF2A1E16);
        rect(5, -4, 9, -1, 0xFF7A5232);
        rect(5, -7.5f, 9, -7, 0xFF5A3C26);
        window(14, -6, 2.4f, 2, 0, false);
        door(14, -1, 3.2f, 6.5f, false, 0xFF6E4424);
        sideBegin(20, -1);
        window(5, -6, 2.4f, 2, 0, false);
        sideEnd();
        chimney(14, -20, 2, 5, STONE);
        // Gehege mit Schlamm
        oval(-24, -2, -2, 8, 0xFF6A5236);
        oval(-17, 1, -8, 5.5f, 0xFF5A4028);
        oval(-15, 2, -10, 4.5f, 0x407AA0C0);
        fence(-25, -2, -3, -2, 5);
        pig(-18, 2.5f, 1);
        pig(-9, 4.5f, 0.9f);
        pig(-14, 6.5f, 0.8f);
        trough(-21, 7, 5, 0xFFC8A848);
        fence(-25, 8.5f, -3, 8.5f, 5);
        hay(24, 5, 0.75f);
        hay(25, 1.5f, 0.7f);
        sack(1, 6, 0.8f, 0xFFD8C890);
    }

    private void butcher() {
        ground(0, 1, 26, 7, 0xFF907656);
        // Räucherkammer
        house(-15, 1, 10, 9, 6.5f, 7, STONE, 0xFF8C867C, STONE, SLATE, 0xFF505660);
        door(-15, 1, 3, 5.5f, false, 0xFF4A3020);
        house(5, 0, 24, 14, 11, 12, TIMBER, 0xFFF3E6D6, TIMBER, TILE, 0xFF8E2A2A);
        window(-1, -8, 2.6f, 2.6f, 0xFF7A2020, true);
        window(11, -8, 2.6f, 2.6f, 0xFF7A2020, true);
        // Ladentheke mit Markise
        rect(-3, -4.5f, 4.5f, -1.2f, 0xFF2A3A4C);
        box(-3.4f, -1.4f, 4.8f, -0.4f, 1.2f, 0xFF9A6E44);
        for (int k = 0; k < 3; k++) {
            float wx = -2 + k * 2.6f;
            ln(wx, -4.5f, wx, -3.6f, 0.12f, 0xFF8A7A5A);
            ring(wx, -2.8f, 0.8f, 0.45f, 0xFFA84A3A);
        }
        for (int k = 0; k < 6; k++) {
            float ml = -3.8f + k * 1.5f;
            quad(ml, -6.5f, ml + 1.5f, -6.5f, ml + 1.7f, -4.6f, ml + 0.2f, -4.6f, k % 2 == 0 ? 0xFFC62828 : 0xFFF5F0E6);
        }
        for (int k = 0; k < 6; k++) {
            float ml = -3.6f + k * 1.5f;
            tri(ml, -4.6f, ml + 1.5f, -4.6f, ml + 0.75f, -4f, k % 2 == 0 ? 0xFFC62828 : 0xFFF5F0E6);
        }
        door(9, 0, 3.4f, 7.5f, false, 0xFF7A2020);
        gableWindow(false);
        sideBegin(17, 0);
        window(5.5f, -8, 2.4f, 2.4f, 0xFF7A2020, false);
        sideEnd();
        chimney(13, -26.5f, 2.2f, 6, BRICK);
        hangSign(-1.5f - 0.5f + 16, -11.5f, World.T_CLEAVER, 0);
        barrel(21, 6, 0.85f);
        barrel(24, 4, 0.8f);
        rect(-9, 5, -5, 5.6f, 0xFF8A6440);
        tool(-7, 4.5f, World.T_CLEAVER, 0.5f);
        crate(-21, 7, 0.75f);
        bush(-26, 3, 0.9f, true);
    }

    private void mine(int type) {
        int[] oreCol = {0xFF2A2A2E, 0xFF8A5A44, 0xFFE0B030, 0xFFA8A194};
        int[] ore = {World.WARE_COAL, World.WARE_IRONORE, World.WARE_GOLD, World.WARE_STONE};
        int k = type - World.B_COAL;
        ground(0, 2, 26, 6, 0xFF8A8070);
        // Felsen
        float[][] rocks = {{-24, 2, -10, -19}, {-14, 0, 2, -26}, {-2, 0, 14, -24}, {8, 2, 22, -16}, {16, 3, 26, -9}, {-28, 4, -18, -10}};
        for (float[] rk : rocks) {
            float l = rk[0], b = rk[1], rr = rk[2], t = rk[3];
            pa.reset();
            pa.moveTo(l, b);
            pa.lineTo(l + (rr - l) * 0.15f, t + (b - t) * 0.35f);
            pa.lineTo(l + (rr - l) * 0.45f, t);
            pa.lineTo(l + (rr - l) * 0.8f, t + (b - t) * 0.2f);
            pa.lineTo(rr, b);
            pa.close();
            int rc = jit(0xFF847868, 0.06f);
            p.setColor(rc);
            c.drawPath(pa, p);
            tri(l + (rr - l) * 0.45f, t, l + (rr - l) * 0.8f, t + (b - t) * 0.2f, rr, b, sh(rc, 0.75f));
            tri(l + (rr - l) * 0.45f, t, l + (rr - l) * 0.15f, t + (b - t) * 0.35f, l + (rr - l) * 0.4f, b, sh(rc, 1.12f));
            ln(l + (rr - l) * 0.45f, t + 0.3f, l + (rr - l) * 0.6f, t + (b - t) * 0.6f, 0.2f, sh(rc, 0.65f));
            for (int i = 0; i < 4; i++) {
                float sx = rn(l + 2, rr - 2), sy = rn(t + (b - t) * 0.4f, b - 1);
                circ(sx, sy, 0.3f, alpha(oreCol[k], 0xC0));
            }
            if (r.nextBoolean()) tuft(rn(l + 2, rr - 2), t + (b - t) * 0.6f);
        }
        // Stollenmund
        pa.reset();
        pa.moveTo(-5.5f, 1);
        pa.lineTo(-5.5f, -8);
        pa.quadTo(0, -12.5f, 5.5f, -8);
        pa.lineTo(5.5f, 1);
        pa.close();
        p.setColor(0xFF120E0C);
        c.drawPath(pa, p);
        oval(-3.5f, -6, 3.5f, 1, 0xFF201A16);
        // Holzrahmen
        for (int s = -1; s <= 1; s += 2) {
            rect(s * 5.5f - 0.9f, -10.5f, s * 5.5f + 0.9f, 1, 0xFF7A5232);
            ln(s * 5.5f - 0.6f, -10.5f, s * 5.5f - 0.6f, 1, 0.2f, 0xFF9A7250);
            for (int i = 0; i < 4; i++) ln(s * 5.5f - 0.8f, -9 + i * 2.6f, s * 5.5f + 0.8f, -8.8f + i * 2.6f, 0.12f, 0xFF5A3A20);
        }
        box(-7.5f, -12.6f, 7.5f, -10.5f, 2, 0xFF8A6040);
        ln(-7.5f, -12.4f, 7.5f, -12.4f, 0.2f, 0xFFA07A50);
        circ(-6.5f, -11.5f, 0.3f, IRON);
        circ(6.5f, -11.5f, 0.3f, IRON);
        // Schild mit Rohstoff
        rrect(-2.5f, -16.4f, 2.5f, -12.6f, 0.5f, 0xFF9A6E44);
        rrect(-2.1f, -16, 2.1f, -13, 0.4f, 0xFFE8D8B0);
        ren.ware(c, ore[k], 0, -13.8f, 0.45f);
        lantern(-8.5f, 1, 8.5f);
        // Gleise heraus
        for (int i = 0; i < 6; i++) {
            float yy = -1 + i * 1.6f, sp = 2.4f + i * 0.4f;
            rect(-sp - 0.6f, yy - 0.3f, sp + 0.6f, yy + 0.3f, 0xFF6E4C30);
        }
        ln(-2.2f, -1, -4.6f, 8.6f, 0.35f, 0xFF7A7A82);
        ln(2.2f, -1, 4.6f, 8.6f, 0.35f, 0xFF7A7A82);
        // Lore
        float lx = 0, ly = 4;
        quad(lx - 3.6f, ly - 3.4f, lx + 3.6f, ly - 3.4f, lx + 2.8f, ly - 0.6f, lx - 2.8f, ly - 0.6f, 0xFF5D4037);
        rect(lx - 3.6f, ly - 3.4f, lx + 3.6f, ly - 2.9f, IRON);
        rect(lx - 3, ly - 2f, lx + 3, ly - 1.6f, IRON);
        heap(lx, ly - 3.2f, 3, 1.4f, oreCol[k]);
        circ(lx - 2, ly - 0.2f, 0.9f, 0xFF333333);
        circ(lx + 2, ly - 0.2f, 0.9f, 0xFF333333);
        circ(lx - 2, ly - 0.2f, 0.35f, 0xFF777777);
        circ(lx + 2, ly - 0.2f, 0.35f, 0xFF777777);
        // Erzhaufen und Werkzeug
        heap(15, 7, 4.5f, 2.6f, oreCol[k]);
        tool(-14, 5, World.T_PICK, 0.6f);
        tool(-11, 7, World.T_SHOVEL, 0.55f);
        crate(-19, 8, 0.7f);
        rubble(9, 8, 5, 0xFF8A8070);
        if (k == 2) {
            // Goldmine: kleiner Förderturm
            ln(-21, 1, -18, -12, 0.5f, 0xFF6E4C30);
            ln(-15, 1, -18, -12, 0.5f, 0xFF6E4C30);
            ln(-20.2f, -3, -15.8f, -3, 0.4f, 0xFF6E4C30);
            wheel(-18, -12, 2);
            ln(-18, -10, -18, -4, 0.15f, IRON);
        }
    }

    private void smelter() {
        ground(0, 1, 28, 7, 0xFF7E7466);
        // Kohlehaufen und Erz
        heap(-24, 5, 5, 3, 0xFF2A2A2E);
        heap(-15, 7, 4, 2.4f, 0xFF8A5A44);
        house(-2, 0, 24, 14, 9, 12, STONE, 0xFF9E958A, STONE, SLATE, 0xFF4E4A48);
        // Schmelzofen-Öffnung
        pa.reset();
        pa.moveTo(-9.5f, -1.2f);
        pa.lineTo(-9.5f, -4.8f);
        pa.quadTo(-9.5f, -8, -6.5f, -8);
        pa.quadTo(-3.5f, -8, -3.5f, -4.8f);
        pa.lineTo(-3.5f, -1.2f);
        pa.close();
        p.setColor(0xFF3A1A10);
        c.drawPath(pa, p);
        rect(-10.4f, -1.4f, -2.6f, 0, 0xFF6E6860);
        // Blasebalg
        quad(-1, -3.5f, 3.5f, -4.5f, 3.5f, -1, -1, -1.6f, 0xFF7A4A2A);
        for (int i = 0; i < 4; i++) ln(-0.5f + i * 1.1f, -3.6f + i * -0.25f, -0.5f + i * 1.1f, -1.5f, 0.2f, 0xFF5A3018);
        ln(-1, -2.5f, -3.5f, -3, 0.5f, IRON);
        window(5, -8.5f, 2.4f, 2.4f, 0, false);
        archWindow(5, -3.5f, 2.4f, 3);
        sideBegin(10, 0);
        window(6, -8, 2.2f, 2.4f, 0, false);
        sideEnd();
        // hoher Schornstein
        chimney(11, -12, 4, 41, BRICK);
        for (float yy = -20; yy > -50; yy -= 6) rect(9, yy, 13, yy + 0.6f, IRON);
        // Eisenbarren
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3 - i; j++)
            box(16 + j * 2.4f + i * 1.2f, 4 - i * 1.1f, 18 + j * 2.4f + i * 1.2f, 5 - i * 1.1f, 0.8f, jit(0xFF7A8088, 0.05f));
        trough(23, 2, 4, 0xFF2D5D8A);
        wheelbarrow(-8, 8, World.WARE_COAL);
        tool(25, 7, World.T_SHOVEL, 0.55f);
    }

    private void tools() {
        ground(0, 1, 28, 7, 0xFF8E7A5A);
        // offene Werkstatt links (Vordach)
        for (float xx : new float[]{-24, -12}) rect(xx - 0.45f, -10, xx + 0.45f, 0, 0xFF5A3C26);
        // Werkzeugwand
        tex(-23.6f, -9.5f, -12.4f, 0, PLANK, 0xFF7A5232);
        int[] tl = {World.T_HAMMER, World.T_SAW, World.T_AXE, World.T_PICK, World.T_SCYTHE};
        for (int i = 0; i < tl.length; i++) tool(-22 + i * 2.2f, -5.5f, tl[i], 0.5f);
        box(-23, -3, -13, -2.2f, 2, 0xFFA07A50);
        rect(-22.5f, -2.2f, -22, 0, 0xFF6E4C30);
        rect(-14, -2.2f, -13.5f, 0, 0xFF6E4C30);
        quad(-25.5f, -10, -10.5f, -10, -7.5f, -14, -22.5f, -14, 0xFF5A6070);
        for (float xx = -25; xx < -10; xx += 1.4f) ln(xx, -10, xx + 3, -14, 0.2f, 0xFF404650);
        house(3, 0, 24, 14, 11, 12, TIMBER, 0xFFE6D8C0, TIMBER, SLATE, 0xFF4E5A6E);
        window(-3, -8, 2.6f, 2.6f, 0xFF3E4E6E, false);
        window(9, -8, 2.6f, 2.6f, 0xFF3E4E6E, false);
        // Esse mit Glut
        rect(-4.5f, -4.5f, -0.5f, -1, 0xFF2A1410);
        rect(-4.5f, -2.5f, -0.5f, -1, 0xFFE0602A);
        rect(-5, -5, 0, -4.4f, 0xFF6E6860);
        door(6, 0, 3.6f, 7.5f, false, 0xFF3E4E6E);
        gableWindow(true);
        sideBegin(15, 0);
        window(5.5f, -8, 2.4f, 2.4f, 0xFF3E4E6E, false);
        sideEnd();
        chimney(13, -26.5f, 2.4f, 6, BRICK);
        hangSign(10, -11.5f, World.T_HAMMER, 0);
        anvil(17, 5);
        grindstone(24, 4);
        barrel(-6, 6, 0.8f);
        crate(-17, 7, 0.75f);
        for (int i = 0; i < 2; i++) box(10 + i * 2.2f, 6.2f, 12 + i * 2.2f, 7, 0.8f, 0xFF7A8088);
    }

    private void store() {
        ground(0, 1, 30, 7, 0xFF907656);
        house(2, 0, 32, 17, 13, 14, PLANK, 0xFF9A6E3A, PLANK, TILE, 0xFF8A4A2A);
        // großes Ladetor
        rect(-6, -11, 6, 0, 0xFF2A1E16);
        rect(-6.6f, -11.6f, 6.6f, -11, 0xFF5A3C26);
        rect(-6.6f, -11, -6, 0, 0xFF5A3C26);
        rect(6, -11, 6.6f, 0, 0xFF5A3C26);
        // ein Torflügel offen, einer zu
        for (float xx = 0; xx < 6; xx += 1.5f) {
            rect(xx, -11, xx + 1.5f, 0, jit(0xFF7A4A2A, 0.06f));
            ln(xx + 1.5f, -11, xx + 1.5f, 0, 0.15f, 0xFF4A2A16);
        }
        ln(0, -11, 6, 0, 0.5f, 0xFF5A3018);
        rect(0, -6, 6, -5.4f, IRON);
        // Kisten im Tor
        crate(-3.5f, 0, 0.7f);
        barrel(-1, 0, 0.6f);
        // Ladeluke + Kranbalken
        rect(0, -22, 4, -17.5f, 0xFF2A1E16);
        rect(-0.5f, -22.5f, 4.5f, -22, 0xFF5A3C26);
        box(1.3f, -25.6f, 2.7f, -24.2f, 0, 0xFF6E4C30);
        quad(1.3f, -25.6f, 2.7f, -25.6f, 2.7f, -24.2f, 1.3f, -24.2f, 0xFF6E4C30);
        ln(2, -24.6f, 2, -23.2f, 0.3f, IRON);
        circ(2, -23.2f, 0.7f, IRON);
        circ(2, -23.2f, 0.3f, 0xFF888888);
        ln(2.6f, -23.2f, 2.6f, -14.5f, 0.15f, 0xFFB09A70);
        ln(1.4f, -23.2f, 1.4f, -17.5f, 0.15f, 0xFFB09A70);
        crate(2.6f, -11.8f, 0.6f);
        window(-11, -7, 2.6f, 2.6f, 0, false);
        window(12.5f, -7, 2.6f, 2.6f, 0, false);
        window(-11, -14, 2.4f, 2.4f, 0, false);
        window(12.5f, -14, 2.4f, 2.4f, 0, false);
        sideBegin(18, 0);
        window(5, -7, 2.4f, 2.6f, 0, false);
        window(10, -7, 2.4f, 2.6f, 0, false);
        sideEnd();
        // Rampe
        quad(-7, 0, 7, 0, 8.5f, 3, -8.5f, 3, 0xFF8A6440);
        for (float yy = 0.6f; yy < 3; yy += 0.8f) ln(-7.5f, yy, 7.5f, yy, 0.15f, 0xFF5A3C26);
        // Vorräte draußen
        crate(-22, 4, 0.9f);
        crate(-18, 6, 0.85f);
        crate(-21, 0, 0.75f);
        barrel(-27, 6, 0.9f);
        barrel(-25.5f, 2.5f, 0.85f);
        sack(15, 6, 0.85f, 0xFFD8C8A0);
        sack(17.5f, 7, 0.8f, 0xFFE0D0A8);
        sack(20, 5.5f, 0.85f, 0xFFD8C8A0);
        cart(28, 5, World.WARE_STONE);
        lantern(-14, 3, 9);
    }

    private void scout() {
        ground(0, 1, 16, 5.5f, 0xFF8A7252);
        // Zelt
        tri(-17, 5, -9, 5, -13, -3, 0xFFC8B890);
        tri(-13, -3, -9, 5, -7, 3.5f, 0xFFA89870);
        tri(-14, 5, -12, 5, -13, 0, 0xFF3A3020);
        ln(-13, -3, -13, -4.5f, 0.3f, 0xFF6E4C30);
        // Lagerfeuer
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            circ(-4 + (float) Math.cos(a) * 1.6f, 6.5f + (float) Math.sin(a) * 0.7f, 0.5f, 0xFF8A847A);
        }
        ln(-5.2f, 6.8f, -2.8f, 6.2f, 0.4f, 0xFF5A3C26);
        ln(-5.2f, 6.2f, -2.8f, 6.8f, 0.4f, 0xFF5A3C26);
        // Turm: hintere Beine zuerst
        float h = 30, base = 6.5f, top = 4.5f, d = 5;
        float dy = d * SK;
        int wc = 0xFF6D4C41, wl = 0xFF8D6C51;
        ln(-base + d, -dy, -top + d * 0.8f, -h - dy * 0.8f, 0.9f, sh(wc, 0.8f));
        ln(base + d, -dy, top + d * 0.8f, -h - dy * 0.8f, 0.9f, sh(wc, 0.8f));
        for (float f : new float[]{0.3f, 0.65f}) {
            float yy = -h * f;
            float xl = -base + (base - top) * f, xr = base - (base - top) * f;
            ln(xr, yy, xr + d * 0.9f, yy - dy * 0.9f, 0.5f, sh(wc, 0.75f));
        }
        // Leiter
        ladder(-2.5f, 2, -1.5f, -h);
        ln(-base, 0, -top, -h, 1, wc);
        ln(base, 0, top, -h, 1, wc);
        ln(-base + 0.3f, 0, -top + 0.3f, -h, 0.25f, wl);
        ln(base + 0.3f, 0, top + 0.3f, -h, 0.25f, wl);
        float[] fs = {0.08f, 0.38f, 0.68f};
        for (int i = 0; i < 2; i++) {
            float f0 = fs[i], f1 = fs[i + 1];
            float y0 = -h * f0, y1 = -h * f1;
            float xl0 = -base + (base - top) * f0, xr0 = base - (base - top) * f0;
            float xl1 = -base + (base - top) * f1, xr1 = base - (base - top) * f1;
            ln(xl0, y0, xr1, y1, 0.5f, wc);
            ln(xr0, y0, xl1, y1, 0.5f, wc);
            ln(xl1, y1, xr1, y1, 0.6f, wl);
        }
        // Plattform
        float pl = -top - 2, pr = top + 2, pt = -h;
        box(pl, pt, pr, pt + 1.2f, d + 1, 0xFF8A6440);
        // Geländer
        for (float xx = pl + 0.3f; xx <= pr; xx += 1.6f) rect(xx, pt - 3.5f, xx + 0.35f, pt, 0xFF6D4C41);
        rect(pl, pt - 3.8f, pr, pt - 3.2f, wl);
        ln(pr, pt - 3.5f, pr + d + 1, pt - 3.5f - (d + 1) * SK, 0.5f, wl);
        // Dach
        float rt = pt - 11;
        quad(0.5f, rt, pr + 1.5f, pt - 5, pr + d + 2.5f, pt - 5 - (d + 1) * SK, 0.5f + d + 1, rt - (d + 1) * SK, 0xFF4A5E36);
        for (int s = -1; s <= 1; s += 2) rect(s * (top + 1.5f) - 0.3f, pt - 5, s * (top + 1.5f) + 0.3f, pt - 3.5f, wc);
        roofPlane(0.5f, rt, pr + 1.5f, pt - 5, d + 1, SHINGLE, 0xFF5A7A3E);
        tri(pl - 1.5f, pt - 5, pr + 1.5f, pt - 5, 0.5f, rt, 0xFF4E6A34);
        ln(pl - 1.5f, pt - 5, 0.5f, rt, 0.7f, 0xFF3A2A1C);
        ln(0.5f, rt, pr + 1.5f, pt - 5, 0.7f, 0xFF3A2A1C);
        // Fernrohr auf dem Geländer
        ln(pl + 1.5f, pt - 4, pl - 1.5f, pt - 5.8f, 0.6f, 0xFFB09040);
        bush(12, 4, 0.9f, false);
        sack(9, 7, 0.7f, 0xFFA89870);
    }

    private void shieldProp(float x, float y, float sc, int col) {
        pa.reset();
        pa.moveTo(x - 2.2f * sc, y - 3 * sc);
        pa.lineTo(x + 2.2f * sc, y - 3 * sc);
        pa.lineTo(x + 2.2f * sc, y - 0.6f * sc);
        pa.quadTo(x + 2f * sc, y + 1.8f * sc, x, y + 2.8f * sc);
        pa.quadTo(x - 2f * sc, y + 1.8f * sc, x - 2.2f * sc, y - 0.6f * sc);
        pa.close();
        p.setColor(0xFF5E646A);
        c.drawPath(pa, p);
        c.save();
        c.translate(x, y);
        c.scale(0.8f, 0.8f);
        c.translate(-x, -y);
        p.setColor(col);
        c.drawPath(pa, p);
        c.restore();
        ln(x, y - 2.5f * sc, x, y + 2.2f * sc, 0.3f * sc, 0xFFE8DCC0);
        ln(x - 1.7f * sc, y - 0.8f * sc, x + 1.7f * sc, y - 0.8f * sc, 0.3f * sc, 0xFFE8DCC0);
        circ(x, y - 0.8f * sc, 0.45f * sc, 0xFFC9A227);
    }

    private void swordProp(float x1, float y1, float x2, float y2) {
        ln(x1, y1, x2, y2, 0.55f, 0xFFDDE3E8);
        ln(x1, y1, x1 + (x2 - x1) * 0.9f, y1 + (y2 - y1) * 0.9f, 0.18f, 0xFF9AA3AA);
        float dx = x2 - x1, dy = y2 - y1, l = (float) Math.hypot(dx, dy);
        float nx = -dy / l, ny = dx / l;
        ln(x1 - nx * 1.3f, y1 - ny * 1.3f, x1 + nx * 1.3f, y1 + ny * 1.3f, 0.5f, 0xFFC9A227);
        ln(x1, y1, x1 - dx / l * 1.6f, y1 - dy / l * 1.6f, 0.5f, 0xFF6D4C41);
        circ(x1 - dx / l * 1.9f, y1 - dy / l * 1.9f, 0.4f, 0xFFC9A227);
    }

    private void armory() {
        ground(0, 1, 28, 7, 0xFF7E7466);
        // Waffenständer links
        rect(-27, -10, -26.4f, 2, 0xFF5A3C26);
        rect(-14.6f, -10, -14, 2, 0xFF5A3C26);
        rect(-27.2f, -10.6f, -13.8f, -9.8f, 0xFF6E4C30);
        rect(-27.2f, -1.2f, -13.8f, -0.5f, 0xFF6E4C30);
        for (int k = 0; k < 4; k++) swordProp(-25 + k * 3, -9.6f, -25.4f + k * 3, -1.4f);
        int[] cols = {0xFF8E2A2A, 0xFF1E5BC6, 0xFF2E7D32};
        for (int k = 0; k < 3; k++) shieldProp(-24 + k * 4.4f, 3.5f - (k % 2) * 0.6f, 1.05f, cols[k]);
        house(3, 0, 24, 14, 11, 12, STONE, 0xFF8E867C, STONE, SLATE, 0xFF3E4450);
        // offene Esse mit Glut
        pa.reset();
        pa.moveTo(-6.5f, -0.8f);
        pa.lineTo(-6.5f, -6);
        pa.quadTo(-6.5f, -9, -3.5f, -9);
        pa.quadTo(-0.5f, -9, -0.5f, -6);
        pa.lineTo(-0.5f, -0.8f);
        pa.close();
        p.setColor(0xFF2A1410);
        c.drawPath(pa, p);
        rect(-6, -3, -1, -0.8f, 0xFFE0602A);
        circ(-3.5f, -3.2f, 1.6f, 0x80FFB040);
        rect(-7.2f, -1, 0.2f, 0, 0xFF6E6860);
        window(9, -8.5f, 2.6f, 2.6f, 0xFF4A3020, false);
        archWindow(4, -9, 2.4f, 3.4f);
        door(9.5f, 0, 3.4f, 7.5f, true, 0xFF4A3020);
        gableWindow(true);
        sideBegin(15, 0);
        window(5.5f, -8, 2.4f, 2.4f, 0xFF4A3020, false);
        sideEnd();
        chimney(12, -26.5f, 2.6f, 7, BRICK);
        // Schild mit gekreuzten Schwertern
        ln(12.5f, -11.5f, 17.5f, -11.5f, 0.4f, IRON);
        ln(12.7f, -9.9f, 14.3f, -11.5f, 0.25f, IRON);
        shieldProp(16, -7.6f, 0.9f, 0xFFC62828);
        swordProp(14, -10.5f, 18.4f, -5.5f);
        swordProp(18, -10.5f, 13.6f, -5.5f);
        anvil(17, 5);
        barrel(22.5f, 6, 0.85f);
        oval(20.6f, 2.2f, 24.4f, 3.4f, 0xFF2D5D8A);
        heap(-8, 7, 3, 1.6f, 0xFF2A2A2E);
        for (int i = 0; i < 2; i++) box(25 + i * 2.2f, 6.5f, 27 + i * 2.2f, 7.3f, 0.8f, 0xFF7A8088);
    }

    private void fort() {
        ground(1, 2, 38, 9, 0xFF8E8068);
        // Burgmauer hinten
        tex(-24, -18, 26, -6, STONE, 0xFFA59B88);
        for (float xx = -24; xx < 26; xx += 2.4f) box(xx, -20.4f, xx + 1.3f, -18, 0.6f, 0xFFADA390);
        // Bergfried
        house(2, -2, 22, 20, 12, 12, STONE, 0xFFB8AE9A, STONE, SLATE, 0xFF4A5262);
        archWindow(-3.5f, -14, 2.4f, 4);
        archWindow(7.5f, -14, 2.4f, 4);
        slit(2, -26, 3);
        sideBegin(13, -2);
        slit(4, -14, 3.2f);
        slit(9, -14, 3.2f);
        sideEnd();
        // Türme
        tower(-26.5f, 2, 12, 30, 6, 0xFFB0A692, false, 0);
        slit(-26.5f, -18, 3.2f);
        slit(-26.5f, -8, 3.2f);
        tower(28.5f, 2, 12, 30, 6, 0xFFA89E8A, false, 0);
        slit(28.5f, -18, 3.2f);
        slit(28.5f, -8, 3.2f);
        // vordere Mauer mit Tor
        tex(-20.5f, -10, 22.5f, 3, STONE, 0xFFB5AB98);
        for (float xx = -20.5f; xx < 22.5f; xx += 2.6f) {
            box(xx, -12.6f, xx + 1.4f, -10, 0.7f, jit(0xFFBDB3A0, 0.05f));
            ln(xx, -12.6f, xx + 1.4f, -12.6f, 0.2f, 0xFFD8CFBE);
        }
        rect(-20.5f, -10.4f, 22.5f, -9.6f, 0xFF8E8676);
        // Torbogen mit Fallgitter
        pa.reset();
        pa.moveTo(-3.5f, 3);
        pa.lineTo(-3.5f, -3);
        pa.quadTo(-3.5f, -7.5f, 1, -7.5f);
        pa.quadTo(5.5f, -7.5f, 5.5f, -3);
        pa.lineTo(5.5f, 3);
        pa.close();
        p.setColor(0xFF1E1A16);
        c.drawPath(pa, p);
        c.save();
        c.clipPath(pa);
        for (float xx = -3; xx < 5.5f; xx += 1.3f) ln(xx, -8, xx, -1.5f, 0.3f, IRON);
        for (float yy = -7; yy < -1.5f; yy += 1.3f) ln(-3.5f, yy, 5.5f, yy, 0.3f, IRON);
        c.restore();
        for (int k = 0; k < 7; k++) {
            double a = Math.PI * (k / 6.0);
            float px1 = 1 - (float) Math.cos(a) * 5.4f, py1 = -3 - (float) Math.sin(a) * 5.4f;
            circ(px1, py1, 0.9f, 0xFFCFC6B4);
        }
        // Wappenschild über dem Tor
        shieldProp(1, -12.5f, 1.2f, 0xFFD8C060);
        torch(-6, 1);
        torch(8, 1);
        // Vorplatz
        barrel(-16, 7, 0.9f);
        crate(-12, 8, 0.8f);
        for (int k = 0; k < 3; k++) {
            ln(13 + k * 1.3f, 8, 14 + k * 0.9f, -2, 0.3f, 0xFF8D6440);
            tri(13.7f + k * 0.9f, -2, 14.3f + k * 0.9f, -2, 14 + k * 0.9f, -3.8f, 0xFFB0B4B8);
        }
        rect(12, 5, 17, 5.6f, 0xFF6E4C30);
        shieldProp(20, 6.5f, 1, 0xFF8E2A2A);
        bush(-34, 6, 1, false);
        bush(36, 6, 0.9f, true);
    }
}
