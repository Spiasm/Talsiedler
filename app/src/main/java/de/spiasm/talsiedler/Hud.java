package de.spiasm.talsiedler;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;

/** Bedienoberfläche: obere Leiste, Seitenfenster, Tipps, Menü. Wird direkt auf die Karte gezeichnet. */
public class Hud {
    public interface Host {
        void newGame();

        void save();
    }

    static class Btn {
        final RectF r;
        final Runnable a;
        final String label;

        Btn(RectF r, Runnable a, String label) {
            this.r = r;
            this.a = a;
            this.label = label;
        }
    }

    private final Renderer ren;
    private final Host host;
    public World w;
    public int speed = 1;
    public boolean paused;
    private boolean menu, help, confirmNew, confirmDemolish;
    private String toast;
    private float toastUntil, now;
    private final ArrayList<Btn> btns = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float u = 1f;
    private int panelNode = -1;

    public Hud(Renderer ren, Host host) {
        this.ren = ren;
        this.host = host;
    }

    public void say(String s) {
        toast = s;
        toastUntil = now + 3.2f;
    }

    // ---------- Eingabe ----------

    /** true, wenn ein Knopf getroffen wurde. */
    public boolean tap(float x, float y) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                b.a.run();
                return true;
            }
        }
        if (menu || help) return true;
        if (y < 62 * u) return true;
        if (panelRect != null && panelRect.contains(x, y)) return true;
        int n = ren.nodeAt(w, x, y);
        mapTap(n);
        return false;
    }

    /** Knopf mit dieser Aufschrift drücken (für Tests). */
    public boolean press(String label) {
        for (Btn b : btns)
            if (label.equals(b.label)) {
                b.a.run();
                return true;
            }
        return false;
    }

    /** Liegt die Stelle über einem Fenster/Knopf? (dann nicht die Karte verschieben) */
    public boolean overUi(float x, float y) {
        if (menu || help) return true;
        for (Btn b : btns) if (b.r.contains(x, y)) return true;
        if (y < 62 * u) return true;
        return panelRect != null && panelRect.contains(x, y);
    }

    private RectF panelRect;

    private void mapTap(int n) {
        confirmDemolish = false;
        if (n < 0) {
            select(-1);
            return;
        }
        if (ren.roadFrom >= 0) {
            roadTap(n);
            return;
        }
        if (n == ren.selected) select(-1);
        else select(n);
    }

    private void select(int n) {
        ren.selected = n;
        panelNode = n;
        confirmDemolish = false;
    }

    private void startRoad(int flagId) {
        ren.roadFrom = flagId;
        select(-1);
    }

    private void roadTap(int n) {
        World.Flag from = w.flags.get(ren.roadFrom);
        if (from == null) {
            ren.roadFrom = -1;
            return;
        }
        if (n == from.node) {
            ren.roadFrom = -1;
            return;
        }
        int[] pth = w.planRoad(ren.roadFrom, n);
        if (pth == null) {
            say("Dahin geht kein Weg. Tippe eine Fahne oder freie Stelle in deinem Land an.");
            return;
        }
        int end = pth[pth.length - 1];
        boolean existing = w.obj[end] == World.O_FLAG;
        if (!w.buildRoad(pth)) {
            say("Weg konnte nicht gebaut werden.");
            return;
        }
        World.Flag ef = w.flagAt(end);
        boolean linked = existing && w.connected(ef.id, w.hqB().flag);
        if (existing || ef == null) {
            ren.roadFrom = -1;
            say(linked ? "Weg gebaut – jetzt kommt ein Träger." : "Weg gebaut.");
        } else {
            ren.roadFrom = ef.id;
            say("Weg gebaut. Weiter antippen oder „Fertig“.");
        }
    }

    // ---------- Zeichnen ----------

    private RectF rect(float l, float t, float r, float b) {
        return new RectF(l, t, r, b);
    }

    private void box(Canvas c, RectF r, int col, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawRoundRect(r, rad, rad, p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int col, boolean bold, Paint.Align al) {
        p.setTextSize(size);
        p.setColor(col);
        p.setFakeBoldText(bold);
        p.setTextAlign(al);
        c.drawText(s, x, y, p);
        p.setFakeBoldText(false);
    }

    /** Text mit Zeilenumbruch, liefert die neue y-Position. */
    private float wrap(Canvas c, String s, float x, float y, float maxW, float size, int col) {
        p.setTextSize(size);
        p.setColor(col);
        p.setTextAlign(Paint.Align.LEFT);
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        for (String wd : words) {
            String test = line.length() == 0 ? wd : line + " " + wd;
            if (p.measureText(test) > maxW && line.length() > 0) {
                c.drawText(line.toString(), x, y, p);
                y += size * 1.3f;
                line = new StringBuilder(wd);
            } else line = new StringBuilder(test);
        }
        if (line.length() > 0) {
            c.drawText(line.toString(), x, y, p);
            y += size * 1.3f;
        }
        return y;
    }

    private RectF button(Canvas c, float l, float t, float wd, float h, String label, int col, boolean enabled, Runnable a) {
        RectF r = rect(l, t, l + wd, t + h);
        box(c, rect(l, t + 3 * u, l + wd, t + h + 3 * u), 0x66000000, 10 * u);
        box(c, r, enabled ? col : 0xFF55504A, 10 * u);
        text(c, label, l + wd / 2, t + h / 2 + 7 * u, 20 * u, enabled ? 0xFFFFFFFF : 0xFFAAA49C, true, Paint.Align.CENTER);
        if (enabled && a != null) btns.add(new Btn(r, a, label));
        return r;
    }

    public void draw(Canvas c, float t) {
        now = t;
        btns.clear();
        panelRect = null;
        int vw = c.getWidth(), vh = c.getHeight();
        u = Math.max(0.7f, Math.min(vw / 1280f, vh / 800f));
        topBar(c, vw);
        if (ren.roadFrom >= 0) roadBanner(c, vw);
        else if (panelNode >= 0) panel(c, vw, vh);
        else hints(c, vh);
        zoomButtons(c, vw, vh);
        if (toast != null && now < toastUntil) {
            p.setTextSize(20 * u);
            float tw = Math.min(vw - 40 * u, p.measureText(toast) + 40 * u);
            RectF r = rect(vw / 2f - tw / 2, vh - 90 * u, vw / 2f + tw / 2, vh - 46 * u);
            box(c, r, 0xE0201A14, 12 * u);
            text(c, toast, vw / 2f, vh - 61 * u, 20 * u, 0xFFFFF3C4, false, Paint.Align.CENTER);
        }
        if (menu) menu(c, vw, vh);
        if (help) help(c, vw, vh);
    }

    private void topBar(Canvas c, int vw) {
        float h = 60 * u;
        box(c, rect(0, 0, vw, h), 0xE8302318, 0);
        p.setColor(0xFF6B4E2E);
        c.drawRect(0, h - 3 * u, vw, h, p);
        World.Building hq = w.hqB();
        float x = 18 * u;
        for (int t = 0; t < World.NWARES; t++) {
            c.save();
            c.translate(x + 14 * u, h / 2);
            c.scale(2.4f * u, 2.4f * u);
            ren.ware(c, t, 0, 0, 1f);
            c.restore();
            text(c, String.valueOf(hq.stock[t]), x + 34 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
            text(c, t == 0 ? "Stämme" : t == 1 ? "Bretter" : "Steine", x + 34 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
            x += 120 * u;
        }
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFFD84315, 2.2f * u, false);
        text(c, String.valueOf(w.pool), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Siedler", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
        x += 110 * u;
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFF1E5BC6, 2.2f * u, true);
        text(c, String.valueOf(w.soldiers), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Soldaten", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);

        float bw = 128 * u, bh = 44 * u, bt = 8 * u;
        float r = vw - 12 * u - 96 * u;
        button(c, r, bt, 96 * u, bh, "Menü", 0xFF6D4C41, true, () -> { menu = true; confirmNew = false; });
        r -= bw + 10 * u;
        button(c, r, bt, bw, bh, paused ? "Weiter ▶" : "Pause", paused ? 0xFFC62828 : 0xFF5D6B3A, true, () -> paused = !paused);
        r -= bw + 10 * u;
        button(c, r, bt, bw, bh, "Tempo x" + speed, 0xFF5D6B3A, true, () -> speed = speed == 1 ? 2 : speed == 2 ? 4 : 1);
        r -= bw + 30 * u + 10 * u;
        button(c, r, bt, bw + 30 * u, bh, ren.showSpots ? "Bauplätze an" : "Bauplätze", ren.showSpots ? 0xFFEF8F00 : 0xFF5D6B3A, true,
                () -> ren.showSpots = !ren.showSpots);
    }

    private void person(Canvas c, float x, float y, int shirt, float s, boolean helmet) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3B2F2A);
        c.drawRect(x - 2 * s, y - 5 * s, x - 0.6f * s, y, p);
        c.drawRect(x + 0.6f * s, y - 5 * s, x + 2 * s, y, p);
        p.setColor(shirt);
        c.drawRoundRect(rect(x - 2.8f * s, y - 11 * s, x + 2.8f * s, y - 4.5f * s), 1.8f * s, 1.8f * s, p);
        p.setColor(0xFFF0C495);
        c.drawCircle(x, y - 13 * s, 2.3f * s, p);
        p.setColor(helmet ? 0xFF9E9E9E : 0xFF5D4037);
        c.drawOval(rect(x - 2.5f * s, y - 16 * s, x + 2.5f * s, y - 13.3f * s), p);
    }

    private void zoomButtons(Canvas c, int vw, int vh) {
        if (panelNode >= 0 && ren.roadFrom < 0) return;
        float s = 58 * u;
        button(c, vw - s - 16 * u, vh - 2 * s - 30 * u, s, s, "+", 0xCC5D4037, true, () -> zoomBy(1.25f));
        button(c, vw - s - 16 * u, vh - s - 20 * u, s, s, "–", 0xCC5D4037, true, () -> zoomBy(0.8f));
    }

    public void zoomBy(float f) {
        float cx = ren.camX + ren.viewW / 2f / ren.zoom, cy = ren.camY + ren.viewH / 2f / ren.zoom;
        ren.zoom = Math.max(0.6f, Math.min(4.5f, ren.zoom * f));
        ren.camX = cx - ren.viewW / 2f / ren.zoom;
        ren.camY = cy - ren.viewH / 2f / ren.zoom;
        ren.clampCam();
    }

    private void roadBanner(Canvas c, int vw) {
        float bw = Math.min(vw - 40 * u, 760 * u);
        RectF r = rect(vw / 2f - bw / 2, 72 * u, vw / 2f + bw / 2, 150 * u);
        box(c, r, 0xE81B4F72, 14 * u);
        panelRect = r;
        text(c, "Weg bauen", r.left + 20 * u, r.top + 30 * u, 22 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Tippe auf eine Fahne, ein Gebäude oder eine freie Stelle.", r.left + 20 * u, r.top + 58 * u, 17 * u, 0xFFD6ECFF, false, Paint.Align.LEFT);
        button(c, r.right - 150 * u, r.top + 16 * u, 134 * u, 46 * u, "Fertig", 0xFF2E7D32, true, () -> ren.roadFrom = -1);
    }

    private void hints(Canvas c, int vh) {
        String tip = tip();
        if (tip == null) return;
        float wd = 430 * u;
        p.setTextSize(17 * u);
        RectF r = rect(14 * u, vh - 150 * u, 14 * u + wd, vh - 14 * u);
        box(c, r, 0xD8302318, 14 * u);
        text(c, "Tipp", r.left + 18 * u, r.top + 30 * u, 19 * u, 0xFFFFD54F, true, Paint.Align.LEFT);
        wrap(c, tip, r.left + 18 * u, r.top + 58 * u, wd - 36 * u, 17 * u, 0xFFFFFFFF);
    }

    private String tip() {
        for (World.Building b : w.buildings.values())
            if (b.status.startsWith("Kein Weg"))
                return "Ein Gebäude hat keinen Weg zum Hauptquartier. Tippe seine Fahne an, dann „Weg bauen“.";
        if (w.countBuildings(World.B_WOOD, false) == 0)
            return "Tippe auf freies Land in der Nähe von Bäumen und baue einen Holzfäller. Gelbe Häuschen zeigen Bauplätze („Bauplätze“ oben).";
        if (w.countBuildings(World.B_QUARRY, false) == 0)
            return "Baue einen Steinbruch neben grauen Steinen.";
        if (w.countBuildings(World.B_SAW, false) == 0)
            return "Ein Sägewerk macht aus Baumstämmen Bretter. Es braucht einen großen Bauplatz (orange).";
        if (w.countBuildings(World.B_FOREST, false) == 0)
            return "Ein Förster pflanzt neue Bäume, damit dem Holzfäller das Holz nicht ausgeht.";
        if (w.countBuildings(World.B_GUARD, false) + w.countBuildings(World.B_TOWER, false) == 0)
            return "Baue eine Wachstube nahe der blauen Grenze. Sobald ein Soldat drin ist, wird dein Land größer.";
        return null;
    }

    private void panel(Canvas c, int vw, int vh) {
        float pw = 400 * u, l = vw - pw - 12 * u, top = 72 * u;
        RectF r = rect(l, top, vw - 12 * u, vh - 12 * u);
        panelRect = r;
        box(c, rect(r.left + 4 * u, r.top + 5 * u, r.right + 4 * u, r.bottom + 5 * u), 0x55000000, 16 * u);
        box(c, r, 0xF0E9DCC0, 16 * u);
        p.setColor(0xFF8D6E4A);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * u);
        c.drawRoundRect(r, 16 * u, 16 * u, p);
        p.setStyle(Paint.Style.FILL);
        button(c, r.right - 54 * u, r.top + 12 * u, 42 * u, 42 * u, "X", 0xFF8D6E4A, true, () -> select(-1));

        int n = panelNode;
        float x = l + 20 * u, y = top + 42 * u;
        float in = pw - 40 * u;
        World.Building b = w.buildingAt(n);
        World.Flag f = w.flagAt(n);
        World.Road road = w.roadAt(n);
        int dark = 0xFF3B2A1A;
        if (b != null) {
            text(c, World.B_NAMES[b.type] + (b.done ? "" : " (Baustelle)"), x, y, 24 * u, dark, true, Paint.Align.LEFT);
            y += 14 * u;
            c.save();
            c.translate(x + in / 2, y + 92 * u);
            c.scale(2.6f * u, 2.6f * u);
            ren.buildingSprite(c, b.type, 0, 0, now);
            c.restore();
            y += 120 * u;
            if (b.id != w.hq) y = wrap(c, b.status, x, y, in, 19 * u, 0xFF5D4037) + 6 * u;
            if (!b.done) {
                for (int t = 1; t < World.NWARES; t++) {
                    int need = World.B_COST[b.type][t];
                    if (need == 0) continue;
                    y = wrap(c, World.WARE_NAMES[t] + (t == 1 ? "er" : "e") + ": " + b.got[t] + " von " + need + " da", x, y, in, 18 * u, dark);
                }
                y = wrap(c, "Fortschritt: " + Math.round(b.progress() * 100) + " %", x, y, in, 18 * u, dark);
            } else if (b.id == w.hq) {
                y = wrap(c, "Lager: " + b.stock[0] + " Stämme, " + b.stock[1] + " Bretter, " + b.stock[2] + " Steine", x, y, in, 18 * u, dark);
                y = wrap(c, "Freie Siedler: " + w.pool + " (es kommen langsam neue dazu)", x, y, in, 18 * u, dark);
                y = wrap(c, "Soldaten: " + w.soldiers, x, y, in, 18 * u, dark);
            } else {
                if (b.type == World.B_SAW) y = wrap(c, "Baumstämme im Lager: " + b.stock[World.WARE_LOG], x, y, in, 18 * u, dark);
                if (b.type == World.B_WOOD || b.type == World.B_QUARRY || b.type == World.B_SAW)
                    y = wrap(c, "Schon hergestellt: " + b.produced, x, y, in, 18 * u, dark);
                if (b.type == World.B_FOREST) y = wrap(c, "Gepflanzte Bäume: " + b.produced, x, y, in, 18 * u, dark);
                if (b.type >= World.B_GUARD) y = wrap(c, b.occupied ? "Besetzt. Hält das Land im Umkreis." : "Wartet auf einen Soldaten.", x, y, in, 18 * u, dark);
            }
            float by = r.bottom - 128 * u;
            final World.Building fb = b;
            button(c, x, by, in, 52 * u, "Weg bauen", 0xFF1565C0, true, () -> startRoad(fb.flag));
            if (b.id != w.hq)
                demolishButton(c, x, by + 62 * u, in, () -> w.removeBuilding(fb));
        } else if (f != null) {
            text(c, "Fahne", x, y, 24 * u, dark, true, Paint.Align.LEFT);
            y += 22 * u;
            y = wrap(c, "An Fahnen geben die Träger die Waren weiter. Liegen hier: " + f.wares.size() + " von " + World.FLAG_CAP, x, y, in, 18 * u, dark);
            int nr = 0;
            for (int rid : f.road) if (rid >= 0) nr++;
            y = wrap(c, "Wege an dieser Fahne: " + nr, x, y, in, 18 * u, dark);
            if (f.id != w.hqB().flag && !w.connected(f.id, w.hqB().flag))
                y = wrap(c, "Diese Fahne ist noch nicht mit dem Hauptquartier verbunden.", x, y + 6 * u, in, 18 * u, 0xFFC62828);
            float by = r.bottom - 128 * u;
            final World.Flag ff = f;
            button(c, x, by, in, 52 * u, "Weg bauen", 0xFF1565C0, true, () -> startRoad(ff.id));
            if (f.id != w.hqB().flag) demolishButton(c, x, by + 62 * u, in, () -> w.removeFlag(ff));
        } else if (road != null) {
            text(c, "Weg", x, y, 24 * u, dark, true, Paint.Align.LEFT);
            y += 22 * u;
            World.Settler cs = w.settlers.get(road.carrier);
            String st = cs == null ? "Noch kein Träger – es fehlt ein Weg zum Hauptquartier oder freie Siedler."
                    : cs.carry != null ? "Der Träger bringt gerade " + (cs.carry.type == 0 ? "einen Baumstamm" : cs.carry.type == 1 ? "ein Brett" : "einen Stein") + "."
                    : "Der Träger wartet auf Waren.";
            y = wrap(c, st, x, y, in, 18 * u, dark);
            y = wrap(c, "Länge: " + (road.path.length - 1) + " Schritte. Kurze Wege mit vielen Fahnen sind schneller.", x, y + 6 * u, in, 18 * u, dark);
            float by = r.bottom - 128 * u;
            final World.Road rr = road;
            final int node = n;
            button(c, x, by, in, 52 * u, "Fahne hier setzen", 0xFF1565C0, w.canFlag(n), () -> {
                if (w.placeFlag(node) != null) say("Fahne gesetzt – der Weg hat jetzt zwei Träger.");
                select(-1);
            });
            demolishButton(c, x, by + 62 * u, in, () -> w.removeRoad(rr));
        } else {
            buildMenu(c, n, x, y, in, r);
        }
    }

    private void demolishButton(Canvas c, float x, float y, float wd, Runnable act) {
        button(c, x, y, wd, 52 * u, confirmDemolish ? "Wirklich abreißen?" : "Abreißen", confirmDemolish ? 0xFFB71C1C : 0xFF8D3A2A, true, () -> {
            if (!confirmDemolish) confirmDemolish = true;
            else {
                act.run();
                select(-1);
                say("Abgerissen.");
            }
        });
    }

    private void buildMenu(Canvas c, int n, float x, float y, float in, RectF r) {
        int dark = 0xFF3B2A1A;
        if (!w.owned[n]) {
            text(c, "Fremdes Land", x, y, 24 * u, dark, true, Paint.Align.LEFT);
            wrap(c, "Hier kannst du (noch) nicht bauen. Baue eine Wachstube oder einen Wachturm nahe der Grenze, dann wächst dein Land.", x, y + 34 * u, in, 18 * u, dark);
            return;
        }
        int size = w.buildSize(n);
        boolean flagOk = w.canFlag(n);
        text(c, "Bauen", x, y, 24 * u, dark, true, Paint.Align.LEFT);
        y += 14 * u;
        if (size == 0) {
            String why = w.obj[n] == World.O_TREE ? "Hier steht ein Baum."
                    : w.obj[n] == World.O_STONE ? "Hier liegen Steine."
                    : w.terrain[n] == World.T_WATER ? "Das ist Wasser."
                    : w.terrain[n] == World.T_MOUNTAIN ? "Auf dem Berg kann man (noch) nicht bauen."
                    : w.terrain[n] == World.T_SAND ? "Auf Sand kann man keine Häuser bauen."
                    : "Für ein Haus ist hier kein Platz.";
            y = wrap(c, why, x, y + 20 * u, in, 18 * u, dark);
        }
        float cw = (in - 12 * u) / 2, ch = 118 * u;
        int col = 0;
        float yy = y + 6 * u;
        int[] types = {World.B_WOOD, World.B_QUARRY, World.B_FOREST, World.B_SAW, World.B_GUARD, World.B_TOWER};
        if (size > 0) {
            for (int type : types) {
                boolean ok = size >= World.B_SIZE[type];
                float bx = x + col * (cw + 12 * u);
                RectF br = rect(bx, yy, bx + cw, yy + ch);
                box(c, br, ok ? 0xFFF7EEDA : 0xFFCFC3AA, 12 * u);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * u);
                p.setColor(ok ? 0xFF8D6E4A : 0xFFB0A288);
                c.drawRoundRect(br, 12 * u, 12 * u, p);
                p.setStyle(Paint.Style.FILL);
                c.save();
                c.translate(bx + cw / 2, yy + 62 * u);
                float sc = (type == World.B_TOWER ? 1.25f : 1.6f) * u;
                c.scale(sc, sc);
                ren.buildingSprite(c, type, 0, 0, now);
                c.restore();
                text(c, World.B_NAMES[type], bx + cw / 2, yy + 86 * u, 16 * u, ok ? dark : 0xFF8A7D66, true, Paint.Align.CENTER);
                int[] cost = World.B_COST[type];
                String cs = cost[1] + " Bretter" + (cost[2] > 0 ? ", " + cost[2] + (cost[2] == 1 ? " Stein" : " Steine") : "");
                text(c, ok ? cs : "zu wenig Platz", bx + cw / 2, yy + 106 * u, 13 * u, ok ? 0xFF6D5A44 : 0xFF8A7D66, false, Paint.Align.CENTER);
                if (ok) {
                    final int tt = type;
                    btns.add(new Btn(br, () -> {
                        int id = w.placeBuilding(tt, n);
                        if (id < 0) {
                            say("Hier geht das nicht.");
                            return;
                        }
                        World.Building nb = w.buildings.get(id);
                        World.Flag nf = w.flags.get(nb.flag);
                        int roadsAt = 0;
                        for (int rid : nf.road) if (rid >= 0) roadsAt++;
                        if (roadsAt == 0) {
                            startRoad(nb.flag);
                            say("Baustelle gesetzt. Jetzt einen Weg bauen: tippe eine Fahne an.");
                        } else {
                            select(-1);
                            say("Baustelle gesetzt.");
                        }
                    }, World.B_NAMES[tt]));
                }
                col++;
                if (col == 2) {
                    col = 0;
                    yy += ch + 10 * u;
                }
            }
        }
        if (flagOk) {
            float by = Math.max(yy + 6 * u, r.bottom - 66 * u);
            button(c, x, by, in, 52 * u, "Fahne setzen", 0xFF1565C0, true, () -> {
                World.Flag nf = w.placeFlag(n);
                if (nf != null) startRoad(nf.id);
                say("Fahne gesetzt. Tippe eine andere Fahne an, um sie zu verbinden.");
            });
        }
    }

    private void menu(Canvas c, int vw, int vh) {
        box(c, rect(0, 0, vw, vh), 0x99000000, 0);
        float mw = 460 * u, mh = 400 * u;
        RectF r = rect(vw / 2f - mw / 2, vh / 2f - mh / 2, vw / 2f + mw / 2, vh / 2f + mh / 2);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, "Talsiedler", vw / 2f, r.top + 54 * u, 34 * u, 0xFF3B2A1A, true, Paint.Align.CENTER);
        text(c, "Stufe 1 – Holz, Steine, Bretter, Wege", vw / 2f, r.top + 84 * u, 16 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
        float bx = r.left + 40 * u, bw = mw - 80 * u;
        button(c, bx, r.top + 110 * u, bw, 58 * u, "Weiterspielen", 0xFF2E7D32, true, () -> menu = false);
        button(c, bx, r.top + 180 * u, bw, 58 * u, "So geht's", 0xFF1565C0, true, () -> { help = true; menu = false; });
        button(c, bx, r.top + 250 * u, bw, 58 * u, confirmNew ? "Wirklich neu anfangen?" : "Neues Spiel", confirmNew ? 0xFFB71C1C : 0xFF8D3A2A, true, () -> {
            if (!confirmNew) confirmNew = true;
            else {
                confirmNew = false;
                menu = false;
                select(-1);
                ren.roadFrom = -1;
                host.newGame();
            }
        });
        text(c, "Das Spiel wird automatisch gespeichert.", vw / 2f, r.bottom - 34 * u, 15 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
    }

    private static final String[] HELP = {
            "Ziel: Baue eine Siedlung, die sich selbst mit Holz, Brettern und Steinen versorgt, und vergrößere dein Land.",
            "1. Auf freies Land tippen → Gebäude aussuchen. Es entsteht eine Baustelle mit einer Fahne davor.",
            "2. Jede Fahne braucht einen Weg zum Hauptquartier: Fahne antippen → „Weg bauen“ → andere Fahne antippen. Fahnen werden automatisch gesetzt.",
            "3. Auf jedem Wegstück zwischen zwei Fahnen arbeitet ein Träger. Er reicht die Waren von Fahne zu Fahne weiter.",
            "4. Ein Bauarbeiter kommt, sobald Bretter und Steine da sind, wird gebaut. Danach zieht ein Arbeiter ein.",
            "5. Holzfäller fällt Bäume → Sägewerk macht Bretter. Förster pflanzt neue Bäume. Steinbruch holt Steine.",
            "6. Wachstube/Wachturm an der Grenze: ein Soldat zieht ein und dein Land wächst.",
            "Bedienen: Mit einem Finger schieben, mit zwei Fingern zoomen. „Bauplätze“ zeigt, wo was hinpasst: gelb = klein, orange = groß.",
    };

    private void help(Canvas c, int vw, int vh) {
        box(c, rect(0, 0, vw, vh), 0xAA000000, 0);
        float mw = Math.min(vw - 60 * u, 820 * u), mh = vh - 80 * u;
        RectF r = rect(vw / 2f - mw / 2, 40 * u, vw / 2f + mw / 2, 40 * u + mh);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, "So geht's", r.left + 30 * u, r.top + 50 * u, 30 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
        float y = r.top + 92 * u;
        for (String s : HELP) y = wrap(c, s, r.left + 30 * u, y, mw - 60 * u, 19 * u, 0xFF3B2A1A) + 8 * u;
        button(c, r.right - 200 * u, r.bottom - 76 * u, 170 * u, 56 * u, "Alles klar", 0xFF2E7D32, true, () -> help = false);
    }
}
