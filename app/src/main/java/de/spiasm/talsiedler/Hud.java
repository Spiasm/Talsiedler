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

        void play(int sound);

        void saveSlot(int i);

        boolean loadSlot(int i);

        String slotInfo(int i);

        void volumes(int fx, int amb);

        void frame(World w, Renderer r, float dt, boolean paused);
    }

    /** Neue Welt übernommen (neues Spiel oder geladen). */
    public void reset() {
        lastMsgCount = -1;
        live.clear();
        liveUntil.clear();
        closeAll();
        panelNode = -1;
    }

    static final int WIN_NONE = 0, WIN_STORE = 1, WIN_STATS = 2, WIN_MSGS = 3, WIN_SLOTS = 4, WIN_SOUND = 5;

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
    public boolean intro = true;
    private int win;
    public int fxVol = 7, ambVol = 5;
    private int lastMsgCount = -1;
    private final ArrayList<World.Msg> live = new ArrayList<>();
    private final ArrayList<Float> liveUntil = new ArrayList<>();
    private RectF miniRect;
    private String slotNote;
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

    private void err(String s) {
        say(s);
        host.play(Synth.S_ERROR);
    }

    private boolean modal() {
        return intro || menu || help || win != WIN_NONE;
    }

    private void closeAll() {
        menu = false;
        help = false;
        win = WIN_NONE;
    }

    // ---------- Eingabe ----------

    /** true, wenn ein Knopf getroffen wurde. */
    public boolean tap(float x, float y) {
        if (intro) {
            intro = false;
            host.play(Synth.S_CLICK);
            return true;
        }
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                host.play(Synth.S_CLICK);
                b.a.run();
                return true;
            }
        }
        if (modal()) return true;
        if (miniRect != null && miniB != null && miniRect.contains(x, y)) {
            float sc = miniRect.width() / (miniB[2] - miniB[0]);
            ren.centerOnMini(miniB[0] + (x - miniRect.left) / sc, miniB[1] + (y - miniRect.top) / sc);
            return true;
        }
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
        if (modal()) return true;
        if (miniRect != null && miniRect.contains(x, y)) return true;
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
            err("Dahin geht kein Weg. Tippe eine Fahne oder freie Stelle in deinem Land an.");
            return;
        }
        int end = pth[pth.length - 1];
        boolean existing = w.obj[end] == World.O_FLAG;
        if (!w.buildRoad(pth)) {
            err("Weg konnte nicht gebaut werden.");
            return;
        }
        host.play(Synth.S_PLACE);
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
        if (intro) {
            intro(c, vw, vh);
            return;
        }
        if (lastMsgCount < 0) lastMsgCount = w.msgCount;
        if (w.msgCount != lastMsgCount) {
            int add = Math.min(w.msgCount - lastMsgCount, w.msgs.size());
            for (int i = w.msgs.size() - add; i < w.msgs.size(); i++) {
                if (i < 0) continue;
                live.add(w.msgs.get(i));
                liveUntil.add(now + 9f);
            }
            lastMsgCount = w.msgCount;
            host.play(Synth.S_MSG);
        }
        while (!live.isEmpty() && (liveUntil.get(0) < now || live.size() > 3)) {
            live.remove(0);
            liveUntil.remove(0);
        }
        topBar(c, vw);
        minimap(c, vh);
        if (ren.roadFrom >= 0) roadBanner(c, vw);
        else if (panelNode >= 0) panel(c, vw, vh);
        else hints(c, vh);
        if (ren.roadFrom < 0) messages(c);
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
        if (win == WIN_STORE) store(c, vw, vh);
        if (win == WIN_STATS) stats(c, vw, vh);
        if (win == WIN_MSGS) msgWindow(c, vw, vh);
        if (win == WIN_SLOTS) slots(c, vw, vh);
        if (win == WIN_SOUND) soundWin(c, vw, vh);
    }

    private void topBar(Canvas c, int vw) {
        float h = 60 * u;
        box(c, rect(0, 0, vw, h), 0xE8302318, 0);
        p.setColor(0xFF6B4E2E);
        c.drawRect(0, h - 3 * u, vw, h, p);
        World.Building hq = w.hqB();
        float x = 18 * u;
        btns.add(new Btn(rect(0, 0, 560 * u, h), () -> { win = WIN_STORE; }, "Lager"));
        for (int t = 0; t < World.NWARES; t++) {
            c.save();
            c.translate(x + 14 * u, h / 2);
            c.scale(2.4f * u, 2.4f * u);
            ren.ware(c, t, 0, 0, 1f);
            c.restore();
            text(c, String.valueOf(hq.stock[t]), x + 34 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
            text(c, t == 0 ? "Stämme" : t == 1 ? "Bretter" : "Steine", x + 34 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
            x += 106 * u;
        }
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFFD84315, 2.2f * u, false);
        text(c, String.valueOf(w.pool), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Siedler", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
        x += 100 * u;
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFF1E5BC6, 2.2f * u, true);
        text(c, String.valueOf(w.soldiers), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Soldaten", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);

        float bw = 112 * u, bh = 44 * u, bt = 8 * u;
        float r = vw - 12 * u - 90 * u;
        button(c, r, bt, 90 * u, bh, "Menü", 0xFF6D4C41, true, () -> { menu = true; confirmNew = false; });
        r -= bw + 8 * u;
        button(c, r, bt, bw, bh, paused ? "Weiter" : "Pause", paused ? 0xFFC62828 : 0xFF5D6B3A, true, () -> paused = !paused);
        r -= bw + 8 * u;
        button(c, r, bt, bw, bh, "Tempo x" + speed, 0xFF5D6B3A, true, () -> speed = speed == 1 ? 2 : speed == 2 ? 4 : 1);
        r -= bw + 22 * u + 8 * u;
        button(c, r, bt, bw + 22 * u, bh, "Bauplätze", ren.showSpots ? 0xFFEF8F00 : 0xFF5D6B3A, true,
                () -> ren.showSpots = !ren.showSpots);
        r -= bw + 22 * u + 8 * u;
        button(c, r, bt, bw + 22 * u, bh, "Auslastung", ren.showUtil ? 0xFFEF8F00 : 0xFF5D6B3A, true,
                () -> ren.showUtil = !ren.showUtil);
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
        float bottom = vh - 14 * u - 150 * u - 10 * u;
        RectF r = rect(14 * u, bottom - 136 * u, 14 * u + wd, bottom);
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
                if (b.type < World.B_GUARD)
                    y = wrap(c, "Auslastung: " + Math.round(b.util * 100) + " %" + (b.problem ? " – wartet gerade" : ""), x, y, in, 18 * u,
                            b.util >= 0.7f ? 0xFF2E7D32 : b.util >= 0.35f ? 0xFFB26A00 : 0xFFC62828);
                if (b.type == World.B_SAW) y = wrap(c, "Baumstämme im Lager: " + b.stock[World.WARE_LOG], x, y, in, 18 * u, dark);
                if (b.type == World.B_WOOD || b.type == World.B_QUARRY || b.type == World.B_SAW)
                    y = wrap(c, "Schon hergestellt: " + b.produced, x, y, in, 18 * u, dark);
                if (b.type == World.B_FOREST) y = wrap(c, "Gepflanzte Bäume: " + b.produced, x, y, in, 18 * u, dark);
                if (b.type >= World.B_GUARD) y = wrap(c, b.occupied ? "Besetzt. Hält das Land im Umkreis." : "Wartet auf einen Soldaten.", x, y, in, 18 * u, dark);
            }
            final World.Building fb = b;
            boolean canStop = b.done && b.id != w.hq && b.type < World.B_GUARD;
            float by = r.bottom - (canStop ? 190 : 128) * u;
            button(c, x, by, in, 52 * u, "Weg bauen", 0xFF1565C0, true, () -> startRoad(fb.flag));
            if (canStop) {
                by += 62 * u;
                button(c, x, by, in, 52 * u, b.stopped ? "Weiter arbeiten" : "Arbeit stoppen", b.stopped ? 0xFF2E7D32 : 0xFF6D4C41, true,
                        () -> {
                            w.setStopped(fb, !fb.stopped);
                            say(fb.stopped ? World.B_NAMES[fb.type] + " macht Pause." : World.B_NAMES[fb.type] + " arbeitet wieder.");
                        });
            }
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
                            err("Hier geht das nicht.");
                            return;
                        }
                        host.play(Synth.S_PLACE);
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
        float mw = 560 * u, mh = 560 * u;
        RectF r = rect(vw / 2f - mw / 2, vh / 2f - mh / 2, vw / 2f + mw / 2, vh / 2f + mh / 2);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, "Talsiedler", vw / 2f, r.top + 54 * u, 34 * u, 0xFF3B2A1A, true, Paint.Align.CENTER);
        text(c, "Stufe 1 – Holz, Steine, Bretter, Wege", vw / 2f, r.top + 84 * u, 16 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
        float bx = r.left + 36 * u, bw = (mw - 84 * u) / 2, gap = 12 * u, bh = 58 * u;
        float bx2 = bx + bw + gap;
        float y0 = r.top + 108 * u;
        button(c, bx, y0, bw * 2 + gap, bh, "Weiterspielen", 0xFF2E7D32, true, () -> menu = false);
        y0 += bh + gap;
        button(c, bx, y0, bw, bh, "So geht's", 0xFF1565C0, true, () -> { help = true; menu = false; });
        button(c, bx2, y0, bw, bh, "Spielstände", 0xFF1565C0, true, () -> { win = WIN_SLOTS; menu = false; slotNote = null; });
        y0 += bh + gap;
        button(c, bx, y0, bw, bh, "Statistik", 0xFF5D6B3A, true, () -> { win = WIN_STATS; menu = false; });
        button(c, bx2, y0, bw, bh, "Meldungen", 0xFF5D6B3A, true, () -> { win = WIN_MSGS; menu = false; });
        y0 += bh + gap;
        button(c, bx, y0, bw, bh, "Ton", 0xFF5D6B3A, true, () -> { win = WIN_SOUND; menu = false; });
        button(c, bx2, y0, bw, bh, "Titelbild", 0xFF5D6B3A, true, () -> { intro = true; menu = false; });
        y0 += bh + gap;
        button(c, bx, y0, bw * 2 + gap, bh, confirmNew ? "Wirklich neu anfangen?" : "Neues Spiel", confirmNew ? 0xFFB71C1C : 0xFF8D3A2A, true, () -> {
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
            "7. Viel benutzte Wege werden gepflastert (schneller) und bekommen bei noch mehr Verkehr einen zweiten Träger.",
            "Bedienen: Mit einem Finger schieben, mit zwei Fingern zoomen. „Bauplätze“ zeigt, wo was hinpasst: gelb = klein, orange = groß. „Auslastung“ zeigt, wie fleißig jedes Haus ist. Oben auf die Waren tippen öffnet das Lager. Die kleine Karte unten links bringt dich mit einem Tipp überall hin.",
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

    // ---------- Titelbild ----------

    private void intro(Canvas c, int vw, int vh) {
        box(c, rect(0, 0, vw, vh), 0xCC0E0A06, 0);
        float cx = vw / 2f;
        p.setTextSize(110 * u);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFF000000);
        c.drawText("TALSIEDLER", cx + 5 * u, vh * 0.30f + 6 * u, p);
        p.setColor(0xFFE8B84A);
        c.drawText("TALSIEDLER", cx, vh * 0.30f, p);
        p.setColor(0xFFFFE9A8);
        p.setTextSize(110 * u);
        c.save();
        c.clipRect(0, vh * 0.30f - 105 * u, vw, vh * 0.30f - 62 * u);
        c.drawText("TALSIEDLER", cx, vh * 0.30f, p);
        c.restore();
        p.setFakeBoldText(false);
        p.setColor(0xFF8D6E4A);
        c.drawRect(cx - 330 * u, vh * 0.30f + 24 * u, cx + 330 * u, vh * 0.30f + 28 * u, p);
        text(c, "Er kam, sah und vergaß ...", cx, vh * 0.30f + 74 * u, 36 * u, 0xFFF3E3C3, false, Paint.Align.CENTER);
        // Handwerker
        float gy = vh * 0.80f;
        p.setColor(0xFF4E7A2E);
        c.drawOval(rect(cx - 170 * u, gy - 22 * u, cx + 170 * u, gy + 22 * u), p);
        c.save();
        c.translate(cx, gy);
        c.scale(7.5f * u, 7.5f * u);
        ren.tree(c, -15, 0.5f, 1, 3, now, 3);
        ren.stones(c, 14, 1, 3, 1);
        ren.scratcher(c, 0, 0, now);
        c.restore();
        if ((int) (now * 1.4f) % 2 == 0)
            text(c, "Zum Starten antippen", cx, vh - 26 * u, 20 * u, 0xFFCDBB9A, false, Paint.Align.CENTER);
    }

    // ---------- Übersichtskarte ----------

    private float[] miniB;

    private void minimap(Canvas c, int vh) {
        float mw = 290 * u, mh = 150 * u;
        RectF r = rect(14 * u, vh - 14 * u - mh, 14 * u + mw, vh - 14 * u);
        miniRect = r;
        box(c, rect(r.left - 5 * u, r.top - 5 * u, r.right + 5 * u, r.bottom + 5 * u), 0xE8302318, 10 * u);
        android.graphics.Bitmap bm = ren.minimap(w, now);
        float[] b = ren.miniBounds(w, mw / mh);
        miniB = b;
        float sc = mw / (b[2] - b[0]);
        c.save();
        c.clipRect(r.left, r.top, r.right, r.bottom);
        p.setColor(0xFF000000);
        c.drawRect(r.left, r.top, r.right, r.bottom, p);
        c.save();
        c.translate(r.left, r.top);
        c.scale(sc, sc);
        c.translate(-b[0], -b[1]);
        c.drawBitmap(bm, 0, 0, null);
        c.restore();
        float[] v = ren.viewMini();
        float vx = r.left + (v[0] - b[0]) * sc, vy = r.top + (v[1] - b[1]) * sc, hw = v[2] * sc, hh = v[3] * sc;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * u);
        p.setColor(0xFFFFFFFF);
        c.drawRect(vx - hw, vy - hh, vx + hw, vy + hh, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    // ---------- Meldungen ----------

    private void messages(Canvas c) {
        float y = 72 * u;
        if (panelNode >= 0 && ren.roadFrom < 0) y = 72 * u;
        for (int i = live.size() - 1; i >= 0; i--) {
            final World.Msg m = live.get(i);
            p.setTextSize(17 * u);
            float tw = Math.min(560 * u, p.measureText(m.text) + 70 * u);
            RectF r = rect(14 * u, y, 14 * u + tw, y + 44 * u);
            box(c, r, 0xE0302318, 10 * u);
            p.setColor(0xFFFFD54F);
            c.drawCircle(r.left + 22 * u, r.centerY(), 7 * u, p);
            text(c, "!", r.left + 22 * u, r.centerY() + 5 * u, 14 * u, 0xFF3B2A1A, true, Paint.Align.CENTER);
            text(c, m.text, r.left + 40 * u, r.centerY() + 6 * u, 17 * u, 0xFFFFFFFF, false, Paint.Align.LEFT);
            btns.add(new Btn(r, () -> jump(m.node), null));
            y += 52 * u;
        }
    }

    private void jump(int node) {
        if (node < 0) return;
        ren.centerOn(w, node);
        closeAll();
    }

    private String ago(float t) {
        int s = (int) (w.time - t);
        if (s < 60) return "gerade eben";
        if (s < 3600) return "vor " + (s / 60) + " min";
        return "vor " + (s / 3600) + " h " + (s % 3600) / 60 + " min";
    }

    // ---------- Fenster ----------

    private RectF window(Canvas c, int vw, int vh, float mw, float mh, String title) {
        box(c, rect(0, 0, vw, vh), 0x99000000, 0);
        mw = Math.min(mw, vw - 40 * u);
        mh = Math.min(mh, vh - 40 * u);
        RectF r = rect(vw / 2f - mw / 2, vh / 2f - mh / 2, vw / 2f + mw / 2, vh / 2f + mh / 2);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, title, r.left + 28 * u, r.top + 46 * u, 28 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
        button(c, r.right - 62 * u, r.top + 14 * u, 48 * u, 48 * u, "X", 0xFF8D6E4A, true, () -> win = WIN_NONE);
        return r;
    }

    private void store(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 820 * u, 600 * u, "Lager & Leute");
        World.Building hq = w.hqB();
        int dark = 0xFF3B2A1A;
        int[] onWay = new int[World.NWARES];
        for (World.Flag f : w.flags.values()) for (World.Ware x : f.wares) onWay[x.type]++;
        int carriers = 0, builders = 0, workers = 0, soldiersOut = 0;
        for (World.Settler s : w.settlers.values()) {
            if (s.carry != null) onWay[s.carry.type]++;
            if (s.job == World.J_CARRIER) carriers++;
            else if (s.job == World.J_BUILDER) builders++;
            else if (s.job == World.J_SOLDIER) soldiersOut++;
            else workers++;
        }
        float x = r.left + 30 * u, y = r.top + 96 * u;
        text(c, "Baustoffe", x, y, 21 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 16 * u;
        String[] names = {"Baumstämme", "Bretter", "Steine"};
        float cw = (r.width() - 60 * u) / 3;
        for (int t = 0; t < World.NWARES; t++) {
            float bx = x + t * cw;
            box(c, rect(bx, y, bx + cw - 14 * u, y + 120 * u), 0xFFF7EEDA, 12 * u);
            c.save();
            c.translate(bx + 40 * u, y + 52 * u);
            c.scale(4f * u, 4f * u);
            ren.ware(c, t, 0, 0, 1f);
            c.restore();
            text(c, String.valueOf(hq.stock[t]), bx + 84 * u, y + 64 * u, 40 * u, dark, true, Paint.Align.LEFT);
            text(c, names[t], bx + 18 * u, y + 96 * u, 17 * u, dark, true, Paint.Align.LEFT);
            text(c, onWay[t] + " unterwegs", bx + 18 * u, y + 113 * u, 14 * u, 0xFF6D5A44, false, Paint.Align.LEFT);
        }
        y += 160 * u;
        text(c, "Leute", x, y, 21 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 34 * u;
        String[][] rows = {
                {"Freie Siedler im Hauptquartier", String.valueOf(w.pool)},
                {"Träger auf den Wegen", String.valueOf(carriers)},
                {"Bauarbeiter", String.valueOf(builders)},
                {"Arbeiter in Gebäuden", String.valueOf(workers)},
                {"Soldaten im Hauptquartier", String.valueOf(w.soldiers)},
                {"Soldaten in Wachgebäuden", String.valueOf(soldiersOut)}};
        float colW = (r.width() - 60 * u) / 2;
        for (int i = 0; i < rows.length; i++) {
            float rx = x + (i % 2) * colW, ry = y + (i / 2) * 34 * u;
            text(c, rows[i][0], rx, ry, 17 * u, dark, false, Paint.Align.LEFT);
            text(c, rows[i][1], rx + colW - 30 * u, ry, 19 * u, dark, true, Paint.Align.RIGHT);
        }
        y += 3 * 34 * u + 20 * u;
        text(c, "Gebäude", x, y, 21 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 34 * u;
        int[] types = {World.B_WOOD, World.B_FOREST, World.B_QUARRY, World.B_SAW, World.B_GUARD, World.B_TOWER};
        for (int i = 0; i < types.length; i++) {
            int tt = types[i];
            int done = w.countBuildings(tt, true), all = w.countBuildings(tt, false);
            float rx = x + (i % 2) * colW, ry = y + (i / 2) * 34 * u;
            text(c, World.B_NAMES[tt], rx, ry, 17 * u, dark, false, Paint.Align.LEFT);
            text(c, done + (all > done ? "  (+" + (all - done) + " im Bau)" : ""), rx + colW - 30 * u, ry, 17 * u, dark, true, Paint.Align.RIGHT);
        }
    }

    private void stats(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 900 * u, 640 * u, "Statistik");
        java.util.ArrayList<int[]> st = w.stats;
        if (st.size() < 2) {
            wrap(c, "Noch zu wenig Daten – schau in ein paar Minuten wieder rein.", r.left + 30 * u, r.top + 100 * u, r.width() - 60 * u, 19 * u, 0xFF3B2A1A);
            return;
        }
        float gx = r.left + 30 * u, gw = r.width() - 60 * u;
        float gh = (r.height() - 170 * u) / 2;
        float g1 = r.top + 90 * u, g2 = g1 + gh + 50 * u;
        chart(c, st, gx, g1, gw, gh, new int[]{1, 2, 3}, new int[]{0xFF8D5A2B, 0xFFD39B3A, 0xFF7E7E7E},
                new String[]{"Baumstämme", "Bretter", "Steine"}, "Waren im Lager");
        chart(c, st, gx, g2, gw, gh, new int[]{4, 5}, new int[]{0xFF2F64D0, 0xFFD84315},
                new String[]{"Land (Felder)", "Leute"}, "Land und Leute");
        int mins = (st.get(st.size() - 1)[0] - st.get(0)[0]) / 60;
        text(c, "Zeitraum: die letzten " + mins + " Minuten Spielzeit", r.left + 30 * u, r.bottom - 20 * u, 15 * u, 0xFF6D5A44, false, Paint.Align.LEFT);
    }

    private void chart(Canvas c, java.util.ArrayList<int[]> st, float x, float y, float wd, float h, int[] idx, int[] cols, String[] names, String title) {
        text(c, title, x, y - 10 * u, 19 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
        float lx = x + 230 * u;
        for (int i = 0; i < idx.length; i++) {
            p.setColor(cols[i]);
            c.drawRect(lx, y - 20 * u, lx + 18 * u, y - 14 * u, p);
            text(c, names[i], lx + 24 * u, y - 10 * u, 15 * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            lx += 170 * u;
        }
        box(c, rect(x, y, x + wd, y + h), 0xFFF7EEDA, 8 * u);
        int max = 1;
        for (int[] s : st) for (int i : idx) max = Math.max(max, s[i]);
        p.setColor(0xFFE0D2B4);
        for (int k = 1; k < 4; k++) c.drawRect(x, y + h * k / 4f, x + wd, y + h * k / 4f + 1, p);
        text(c, String.valueOf(max), x + 6 * u, y + 16 * u, 13 * u, 0xFF8D7A60, false, Paint.Align.LEFT);
        int n = st.size();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * u);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < idx.length; i++) {
            path.reset();
            for (int k = 0; k < n; k++) {
                float px = x + 6 * u + (wd - 12 * u) * k / (float) (n - 1);
                float py = y + h - 6 * u - (h - 24 * u) * st.get(k)[idx[i]] / (float) max;
                if (k == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            p.setColor(cols[i]);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void msgWindow(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 820 * u, 640 * u, "Meldungen");
        if (w.msgs.isEmpty()) {
            text(c, "Noch keine Meldungen.", r.left + 30 * u, r.top + 100 * u, 19 * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            return;
        }
        float y = r.top + 80 * u;
        int rows = (int) ((r.bottom - y - 20 * u) / (54 * u));
        for (int i = w.msgs.size() - 1, k = 0; i >= 0 && k < rows; i--, k++) {
            final World.Msg m = w.msgs.get(i);
            RectF br = rect(r.left + 24 * u, y, r.right - 24 * u, y + 46 * u);
            box(c, br, 0xFFF7EEDA, 10 * u);
            text(c, m.text, br.left + 16 * u, br.centerY() + 6 * u, 17 * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            text(c, ago(m.time), br.right - 16 * u, br.centerY() + 6 * u, 14 * u, 0xFF8D7A60, false, Paint.Align.RIGHT);
            btns.add(new Btn(br, () -> jump(m.node), null));
            y += 54 * u;
        }
    }

    private void slots(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 760 * u, 470 * u, "Spielstände");
        float y = r.top + 86 * u;
        text(c, "Das laufende Spiel wird immer automatisch gespeichert. Hier kannst du zusätzlich sichern.", r.left + 30 * u, y, 16 * u, 0xFF6D5A44, false, Paint.Align.LEFT);
        y += 24 * u;
        for (int i = 1; i <= 3; i++) {
            final int slot = i;
            RectF br = rect(r.left + 24 * u, y, r.right - 24 * u, y + 84 * u);
            box(c, br, 0xFFF7EEDA, 12 * u);
            String info = host.slotInfo(i);
            String l1 = info, l2 = "";
            if (info != null && info.contains("  ·  ")) {
                int k = info.indexOf("  ·  ");
                l1 = info.substring(0, k);
                l2 = info.substring(k + 5);
            }
            text(c, "Platz " + i + (info == null ? "" : "  –  " + l1), br.left + 18 * u, br.top + 34 * u, 20 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
            text(c, info == null ? "leer" : l2, br.left + 18 * u, br.top + 64 * u, 14 * u, 0xFF6D5A44, false, Paint.Align.LEFT);
            button(c, br.right - 300 * u, br.top + 16 * u, 136 * u, 52 * u, "Speichern", 0xFF1565C0, true, () -> {
                host.saveSlot(slot);
                slotNote = "Auf Platz " + slot + " gespeichert.";
            });
            button(c, br.right - 152 * u, br.top + 16 * u, 136 * u, 52 * u, "Laden", 0xFF2E7D32, info != null, () -> {
                if (host.loadSlot(slot)) {
                    win = WIN_NONE;
                    say("Spielstand " + slot + " geladen.");
                } else slotNote = "Laden hat nicht geklappt.";
            });
            y += 96 * u;
        }
        if (slotNote != null) text(c, slotNote, r.left + 30 * u, r.bottom - 22 * u, 17 * u, 0xFF2E7D32, true, Paint.Align.LEFT);
    }

    private void soundWin(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 640 * u, 340 * u, "Ton");
        float y = r.top + 100 * u;
        y = slider(c, r, y, "Geräusche (Arbeit, Knöpfe)", fxVol, d -> fxVol = Math.max(0, Math.min(10, fxVol + d)));
        slider(c, r, y + 20 * u, "Umgebung (Vögel, Wind, Wasser)", ambVol, d -> ambVol = Math.max(0, Math.min(10, ambVol + d)));
    }

    interface Step {
        void by(int d);
    }

    private float slider(Canvas c, RectF r, float y, String label, int val, Step st) {
        text(c, label, r.left + 30 * u, y, 19 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
        y += 16 * u;
        float bx = r.left + 30 * u;
        button(c, bx, y, 60 * u, 52 * u, "–", 0xFF8D6E4A, true, () -> { st.by(-1); host.volumes(fxVol, ambVol); });
        float barL = bx + 76 * u, barR = r.right - 106 * u;
        box(c, rect(barL, y + 18 * u, barR, y + 34 * u), 0xFFCFC3AA, 8 * u);
        box(c, rect(barL, y + 18 * u, barL + (barR - barL) * val / 10f, y + 34 * u), 0xFF2E7D32, 8 * u);
        text(c, val == 0 ? "aus" : val * 10 + " %", (barL + barR) / 2, y + 60 * u, 14 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
        button(c, r.right - 90 * u, y, 60 * u, 52 * u, "+", 0xFF8D6E4A, true, () -> { st.by(1); host.volumes(fxVol, ambVol); });
        return y + 80 * u;
    }
}
