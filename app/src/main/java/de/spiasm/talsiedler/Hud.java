package de.spiasm.talsiedler;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;

/** Bedienoberfläche: obere Leiste, Seitenfenster, Tipps, Menü. Wird direkt auf die Karte gezeichnet. */
public class Hud {
    public interface Host {
        void newGame(int ai);

        void save();

        void play(int sound);

        void saveSlot(int i);

        boolean loadSlot(int i);

        String slotInfo(int i);

        void volumes(int fx, int amb);

        void frame(World w, Renderer r, float dt, boolean paused);

        /** Befehl ausführen (allein sofort, zu zweit über den Gleichlauf) */
        void exec(int[] cmd);

        void deleteSlot(int i);

        /** 0 = allein, 1 = Gastgeber, 2 = Gast */
        int role();

        /** 0 = nichts, 1 = wartet auf Mitspieler, 2 = verbindet, 3 = verbunden */
        int mpState();

        String mpText();

        /** gekoppelte Bluetooth-Geräte, null wenn Bluetooth aus oder keine Erlaubnis */
        String[] mpDevices();

        void mpHost(int mode, int goalType, int goalValue);

        void mpJoin(int index);

        void mpLeave();
    }

    /** Neue Welt übernommen (neues Spiel oder geladen). */
    public void reset() {
        pendBuild = pendFlag = pendRoadEnd = -1;
        lastMsgCount = -1;
        live.clear();
        liveUntil.clear();
        closeAll();
        panelNode = -1;
    }

    static final int WIN_NONE = 0, WIN_STORE = 1, WIN_STATS = 2, WIN_MSGS = 3, WIN_SLOTS = 4, WIN_SOUND = 5, WIN_MP = 6, WIN_TOOLS = 7;
    private int buildTab;
    /** eigener Spieler */
    public int me;
    private int pendBuild = -1, pendFlag = -1, pendRoadEnd = -1, confirmSlot, confirmSlotKind;
    private float pendT;
    private int winnerSeen;

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
    private final RectF rf = new RectF();
    private int helpPage;
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
        World.Flag ef0 = w.flagAt(end);
        boolean linked = existing && ef0 != null && w.connected(ef0.id, w.hqB(me).flag);
        host.exec(new int[]{World.C_ROAD, me, ren.roadFrom, n});
        host.play(Synth.S_PLACE);
        ren.roadFrom = -1;
        if (existing) {
            say(linked ? "Weg gebaut – jetzt kommt ein Träger." : "Weg gebaut.");
        } else {
            pendRoadEnd = end;
            pendT = now;
            say("Weg gebaut. Weiter antippen oder „Fertig“.");
        }
        resolvePending();
    }

    /** Wartende Aktionen (z. B. Weg weiterbauen) übernehmen, sobald die Welt sie zeigt. */
    private void resolvePending() {
        if (now - pendT > 4f) pendBuild = pendFlag = pendRoadEnd = -1;
        if (pendRoadEnd >= 0) {
            World.Flag f = w.flagAt(pendRoadEnd);
            if (f != null && f.player == me) {
                ren.roadFrom = f.id;
                pendRoadEnd = -1;
            }
        }
        if (pendFlag >= 0) {
            World.Flag f = w.flagAt(pendFlag);
            if (f != null && f.player == me) {
                startRoad(f.id);
                pendFlag = -1;
            }
        }
        if (pendBuild >= 0) {
            World.Building b = w.buildingAt(pendBuild);
            if (b != null && b.player == me) {
                World.Flag nf = w.flags.get(b.flag);
                int roadsAt = 0;
                for (int rid : nf.road) if (rid >= 0) roadsAt++;
                if (roadsAt == 0) {
                    startRoad(b.flag);
                    say("Baustelle gesetzt. Jetzt einen Weg bauen: tippe eine Fahne an.");
                } else say("Baustelle gesetzt.");
                pendBuild = -1;
            }
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
        ren.me = me;
        resolvePending();
        if (lastMsgCount < 0 || lastMsgCount > w.msgCount) lastMsgCount = w.msgCount;
        if (w.msgCount != lastMsgCount) {
            int add = Math.min(w.msgCount - lastMsgCount, w.msgs.size());
            boolean any = false;
            for (int i = w.msgs.size() - add; i < w.msgs.size(); i++) {
                if (i < 0) continue;
                World.Msg m = w.msgs.get(i);
                if (!mine(m)) continue;
                live.add(m);
                liveUntil.add(now + 9f);
                any = true;
            }
            lastMsgCount = w.msgCount;
            if (any) host.play(Synth.S_MSG);
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
        if (w.mode == 2) raceBar(c, vw);
        if (w.winner > 0 && w.winner != winnerSeen && win == WIN_NONE && !menu && !help) winner(c, vw, vh);
        if (menu) menu(c, vw, vh);
        if (help) help(c, vw, vh);
        if (win == WIN_STORE) store(c, vw, vh);
        if (win == WIN_STATS) stats(c, vw, vh);
        if (win == WIN_MSGS) msgWindow(c, vw, vh);
        if (win == WIN_SLOTS) slots(c, vw, vh);
        if (win == WIN_SOUND) soundWin(c, vw, vh);
        if (win == WIN_MP) mpWin(c, vw, vh);
        if (win == WIN_TOOLS) toolsWin(c, vw, vh);
    }

    private boolean mine(World.Msg m) {
        return m.player < 0 || m.player == me || w.mode == 1;
    }

    private void topBar(Canvas c, int vw) {
        float h = 60 * u;
        box(c, rect(0, 0, vw, h), 0xE8302318, 0);
        p.setColor(0xFF6B4E2E);
        c.drawRect(0, h - 3 * u, vw, h, p);
        World.Building hq = w.hqB(me);
        float x = 18 * u;
        btns.add(new Btn(rect(0, 0, 590 * u, h), () -> { win = WIN_STORE; }, "Lager"));
        int[] show = {World.WARE_LOG, World.WARE_BOARD, World.WARE_STONE, World.WARE_BREAD};
        String[] lbl = {"Stämme", "Bretter", "Steine", "Nahrung"};
        for (int i = 0; i < show.length; i++) {
            int t = show[i];
            c.save();
            c.translate(x + 14 * u, h / 2);
            c.scale(2.4f * u, 2.4f * u);
            ren.ware(c, t, 0, 0, 1f);
            c.restore();
            int val = i == 3 ? w.stockTotal(me, World.WARE_FISH) + w.stockTotal(me, World.WARE_MEAT) + w.stockTotal(me, World.WARE_BREAD)
                    : w.stockTotal(me, t);
            text(c, String.valueOf(val), x + 34 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
            text(c, lbl[i], x + 34 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
            x += 96 * u;
        }
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFFD84315, 2.2f * u, false);
        text(c, String.valueOf(w.pool(me)), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Siedler", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);
        x += 92 * u;
        person(c, x + 12 * u, h / 2 + 16 * u, 0xFF1E5BC6, 2.2f * u, true);
        text(c, String.valueOf(w.soldiers(me)), x + 30 * u, h / 2 + 8 * u, 24 * u, 0xFFFFFFFF, true, Paint.Align.LEFT);
        text(c, "Soldaten", x + 30 * u, h / 2 + 24 * u, 11 * u, 0xFFCDBB9A, false, Paint.Align.LEFT);

        float bw = 112 * u, bh = 44 * u, bt = 8 * u;
        float r = vw - 12 * u - 90 * u;
        button(c, r, bt, 90 * u, bh, "Menü", 0xFF6D4C41, true, () -> { menu = true; confirmNew = false; });
        r -= bw + 8 * u;
        boolean guest = host.role() == 2;
        button(c, r, bt, bw, bh, paused ? "Weiter" : "Pause", paused ? 0xFFC62828 : 0xFF5D6B3A, !guest, () -> paused = !paused);
        r -= bw + 8 * u;
        button(c, r, bt, bw, bh, guest ? "Gast" : "Tempo x" + speed, 0xFF5D6B3A, !guest, () -> speed = speed == 1 ? 2 : speed == 2 ? 4 : 1);
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
            if (b.player == me && b.status.startsWith("Kein Weg"))
                return "Ein Gebäude hat keinen Weg zum Hauptquartier. Tippe seine Fahne an, dann „Weg bauen“.";
        if (w.countBuildings(World.B_WOOD, false, me) == 0)
            return "Tippe auf freies Land in der Nähe von Bäumen und baue einen Holzfäller. Gelbe Häuschen zeigen Bauplätze („Bauplätze“ oben).";
        if (w.countBuildings(World.B_QUARRY, false, me) == 0)
            return "Baue einen Steinbruch neben grauen Steinen.";
        if (w.countBuildings(World.B_SAW, false, me) == 0)
            return "Ein Sägewerk macht aus Baumstämmen Bretter. Es braucht einen großen Bauplatz (orange).";
        if (w.countBuildings(World.B_FOREST, false, me) == 0)
            return "Ein Förster pflanzt neue Bäume, damit dem Holzfäller das Holz nicht ausgeht.";
        if (w.countBuildings(World.B_GUARD, false, me) + w.countBuildings(World.B_TOWER, false, me) == 0)
            return "Baue eine Wachstube nahe der blauen Grenze. Sobald ein Soldat drin ist, wird dein Land größer.";
        if (w.countBuildings(World.B_FISH, false, me) + w.countBuildings(World.B_HUNT, false, me) == 0)
            return "Stufe 2: Bergleute brauchen Nahrung. Baue einen Fischer nah am Wasser oder einen Jäger im Wald („Nahrung“ im Bau-Menü).";
        boolean geo = false;
        for (byte sg : w.sign) if (sg != 0) geo = true;
        int mines = 0;
        for (int t = World.B_COAL; t <= World.B_GRANITE; t++) mines += w.countBuildings(t, false, me);
        if (!geo && mines == 0)
            return "Tippe eine Fahne nahe am Berg an und dann „Geologen schicken“. Er stellt Schilder auf: schwarz = Kohle, rostrot = Eisen, gelb = Gold, grau = Granit.";
        if (mines == 0)
            return "Tippe auf den Berg neben ein Schild und baue die passende Mine. Kleine dunkle Eingänge zeigen, wo Minen hinpassen.";
        if (w.countBuildings(World.B_FARM, false, me) == 0 || w.countBuildings(World.B_WELL, false, me) == 0)
            return "Brot ist die beste Nahrung: Bauernhof (Getreide) → Mühle (Mehl) → Bäckerei (mit Wasser vom Brunnen).";
        if (w.countBuildings(World.B_SMELTER, false, me) == 0)
            return "Eisenschmelze: macht aus Eisenerz und Kohle Eisen.";
        if (w.countBuildings(World.B_TOOLS, false, me) == 0)
            return "Werkzeugmacher: macht aus Eisen und Brettern neues Werkzeug. Was zuerst, stellst du im Menü unter „Werkzeug“ ein.";
        if (w.countBuildings(World.B_SCOUT, false, me) == 0)
            return "Eine Späherhütte schickt einen Späher in den Nebel – so findest du neues Land, Berge und Wasser.";
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
        int ownerOf = b != null ? b.player : f != null ? f.player : road != null ? road.player : me;
        if (ownerOf != me) {
            String what = b != null ? World.B_NAMES[b.type] : f != null ? "Fahne" : "Weg";
            text(c, what, x, y, 24 * u, dark, true, Paint.Align.LEFT);
            float ey = wrap(c, "Gehört Spieler " + World.P_NAMES[ownerOf] + "." + (w.combat() ? "" : " Hier kannst du nichts ändern."), x, y + 34 * u, in, 18 * u, World.P_COLORS[ownerOf]);
            if (b != null && w.combat() && b.done && (World.isMilitary(b.type) || World.isHq(b))) enemyPanel(c, b, x, ey + 8 * u, in, r);
            return;
        }
        if (b != null) {
            text(c, World.B_NAMES[b.type] + (b.done ? "" : " (Baustelle)"), x, y, 24 * u, dark, true, Paint.Align.LEFT);
            y += 14 * u;
            c.save();
            float[] ib = Art.BOX[b.type];
            float isc = Math.min(2.6f * u, Math.min(94 * u / (ib[3] - ib[1]), in * 0.8f / (ib[2] - ib[0])));
            c.translate(x + in / 2, y + 4 * u - ib[1] * isc);
            c.scale(isc, isc);
            ren.bannerFor(me);
            ren.scaleHint = isc;
            ren.buildingSprite(c, b.type, 0, 0, now);
            ren.scaleHint = 0;
            c.restore();
            y += 112 * u;
            if (!World.isHq(b)) y = wrap(c, b.status, x, y, in, 19 * u, 0xFF5D4037) + 6 * u;
            if (!b.done) {
                for (int t = 1; t < 3; t++) {
                    int need = World.B_COST[b.type][t];
                    if (need == 0) continue;
                    y = wrap(c, World.WARE_PLURAL[t] + ": " + b.got[t] + " von " + need + " da", x, y, in, 18 * u, dark);
                }
                y = wrap(c, "Fortschritt: " + Math.round(b.progress() * 100) + " %", x, y, in, 18 * u, dark);
            } else if (World.isHq(b)) {
                y = wrap(c, "Lager: " + b.stock[0] + " Stämme, " + b.stock[1] + " Bretter, " + b.stock[2] + " Steine", x, y, in, 18 * u, dark);
                y = wrap(c, "Freie Siedler: " + w.pool(me) + " von " + World.POOL_MAX + " (alle 20 Sekunden kommt einer dazu)", x, y, in, 18 * u, dark);
                y = wrap(c, "Soldaten: " + w.soldiers(me) + (w.combat() ? " (2 bleiben immer als Wache hier)" : ""), x, y, in, 18 * u, dark);
                y = rankRow(c, w.ranks(me), x, y + 4 * u, in);
                y = wrap(c, "Neue Soldaten: je 1 Schwert + 1 Schild + 1 freier Siedler (macht der Waffenschmied).", x, y, in, 16 * u, 0xFF5D4037);
            } else {
                if (World.hasUtil(b.type))
                    y = wrap(c, "Auslastung: " + Math.round(b.util * 100) + " %" + (b.problem ? " – wartet gerade" : ""), x, y, in, 18 * u,
                            b.util >= 0.7f ? 0xFF2E7D32 : b.util >= 0.35f ? 0xFFB26A00 : 0xFFC62828);
                int[] in2 = World.RECIPE_IN[b.type];
                if (in2.length > 0) {
                    StringBuilder sb = new StringBuilder("Vorrat: ");
                    for (int k = 0; k < in2.length; k++) {
                        if (k > 0) sb.append(", ");
                        sb.append(b.stock[in2[k]]).append(' ').append(b.stock[in2[k]] == 1 ? World.WARE_NAMES[in2[k]] : World.WARE_PLURAL[in2[k]]);
                    }
                    y = wrap(c, sb.toString(), x, y, in, 18 * u, dark);
                }
                if (World.isMine(b.type)) {
                    int left = 0;
                    for (int m : World.around(b.node, World.B_WORK_RADIUS[b.type]))
                        if (w.res[m] == b.type - World.B_COAL + 1) left += w.resAmt[m];
                    y = wrap(c, "Noch im Berg: etwa " + left + " " + World.RES_NAMES[b.type - World.B_COAL + 1], x, y, in, 18 * u, dark);
                }
                if (b.type == World.B_FOREST) y = wrap(c, "Gepflanzte Bäume: " + b.produced, x, y, in, 18 * u, dark);
                else if (b.type == World.B_SCOUT) y = wrap(c, "Erkundungen: " + b.produced, x, y, in, 18 * u, dark);
                else if (World.hasUtil(b.type)) y = wrap(c, "Schon hergestellt: " + b.produced, x, y, in, 18 * u, dark);
                if (b.type == World.B_STORE) {
                    int sum = 0;
                    for (int t2 = 0; t2 < World.NWARES; t2++) sum += b.stock[t2];
                    y = wrap(c, "Hier gelagert: " + sum + " Waren. Waren, die gerade niemand braucht, kommen ins nächste Lager.", x, y, in, 18 * u, dark);
                }
                if (World.isMilitary(b.type)) {
                    int inside = w.inside(b), cap = World.B_CAP[b.type];
                    y = wrap(c, "Soldaten: " + inside + " von " + cap + (b.occupied ? " – hält das Land im Umkreis." : " – wartet auf Soldaten."), x, y, in, 18 * u, dark);
                    y = rankRow(c, w.insideRanks(b), x, y + 4 * u, in);
                    y = wrap(c, b.stopped ? "Ausbildung ist aus." : "Gold hier: " + b.stock[World.WARE_GOLD] + " – jedes Gold macht einen Soldaten einen Rang stärker.", x, y, in, 16 * u, 0xFF5D4037);
                }
            }
            final World.Building fb = b;
            boolean canStop = b.done && (World.hasUtil(b.type) || World.isMilitary(b.type));
            boolean milB = World.isMilitary(b.type);
            boolean site = !b.done;
            boolean toolsBtn = b.done && b.type == World.B_TOOLS;
            float by = r.bottom - ((canStop || site ? 190 : 128) + (toolsBtn ? 62 : 0)) * u;
            if (toolsBtn) {
                button(c, x, by, in, 52 * u, "Werkzeug einstellen", 0xFF5D6B3A, true, () -> win = WIN_TOOLS);
                by += 62 * u;
            }
            button(c, x, by, in, 52 * u, "Weg bauen", 0xFF1565C0, true, () -> startRoad(fb.flag));
            if (site) {
                by += 62 * u;
                button(c, x, by, in, 52 * u, b.prio ? "Wird zuerst gebaut ✓" : "Zuerst bauen", b.prio ? 0xFFEF8F00 : 0xFF6D4C41, true, () -> {
                    host.exec(new int[]{World.C_PRIO, me, fb.id, fb.prio ? 0 : 1});
                    say(fb.prio ? "Normale Reihenfolge." : "Diese Baustelle bekommt das Material zuerst.");
                });
            }
            if (canStop) {
                by += 62 * u;
                button(c, x, by, in, 52 * u, milB ? (b.stopped ? "Ausbildung einschalten" : "Ausbildung ausschalten") : b.stopped ? "Weiter arbeiten" : "Arbeit stoppen",
                        b.stopped ? 0xFF2E7D32 : 0xFF6D4C41, true,
                        () -> {
                            boolean stop = !fb.stopped;
                            host.exec(new int[]{World.C_STOP, me, fb.id, stop ? 1 : 0});
                            if (World.isMilitary(fb.type)) say(stop ? "Hier wird kein Gold mehr gebraucht." : "Gold wird wieder hierher gebracht.");
                            else say(stop ? World.B_NAMES[fb.type] + " macht Pause." : World.B_NAMES[fb.type] + " arbeitet wieder.");
                        });
            }
            if (!World.isHq(b))
                demolishButton(c, x, by + 62 * u, in, true, () -> host.exec(new int[]{World.C_REMOVE, me, 0, fb.node}));
        } else if (f != null) {
            text(c, "Fahne", x, y, 24 * u, dark, true, Paint.Align.LEFT);
            y += 22 * u;
            y = wrap(c, "An Fahnen geben die Träger die Waren weiter. Liegen hier: " + f.wares.size() + " von " + World.FLAG_CAP, x, y, in, 18 * u, dark);
            int nr = 0;
            for (int rid : f.road) if (rid >= 0) nr++;
            y = wrap(c, "Wege an dieser Fahne: " + nr, x, y, in, 18 * u, dark);
            if (f.id != w.hqB(me).flag && !w.connected(f.id, w.hqB(me).flag))
                y = wrap(c, "Diese Fahne ist noch nicht mit dem Hauptquartier verbunden.", x, y + 6 * u, in, 18 * u, 0xFFC62828);
            boolean mountainNear = false;
            for (int m : World.around(f.node, 6)) if (w.terrain[m] == World.T_MOUNTAIN) mountainNear = true;
            if (mountainNear) y = wrap(c, "Am Berg? Schick einen Geologen – er sucht Bodenschätze und stellt Schilder auf.", x, y + 6 * u, in, 17 * u, 0xFF5D4037);
            float by = r.bottom - 252 * u;
            final World.Flag ff = f;
            button(c, x, by, in, 52 * u, "Geologen schicken", 0xFF5D6B3A, true, () -> {
                host.exec(new int[]{World.C_GEO, me, ff.id, 0});
                say("Ein Geologe macht sich auf den Weg (braucht einen Hammer).");
                select(-1);
            });
            by += 62 * u;
            button(c, x, by, in, 52 * u, "Späher schicken", 0xFF00695C, true, () -> {
                host.exec(new int[]{World.C_SCOUT, me, ff.id, 0});
                say("Ein Späher zieht los und erkundet die Gegend rund um die Fahne.");
                select(-1);
            });
            by += 62 * u;
            button(c, x, by, in, 52 * u, "Weg bauen", 0xFF1565C0, true, () -> startRoad(ff.id));
            if (f.id != w.hqB(me).flag) {
                World.Building fbld = w.buildingAt(World.nb(f.node, 4));
                boolean withHouse = fbld != null && fbld.flag == f.id;
                if (withHouse) y = wrap(c, "Achtung: Das Gebäude an dieser Fahne wird mit abgerissen.", x, y + 6 * u, in, 17 * u, 0xFFB71C1C);
                demolishButton(c, x, by + 62 * u, in, withHouse, () -> host.exec(new int[]{World.C_REMOVE, me, 0, ff.node}));
            }
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
            button(c, x, by, in, 52 * u, "Fahne hier setzen", 0xFF1565C0, w.canFlag(n, me), () -> {
                host.exec(new int[]{World.C_FLAG, me, 0, node});
                host.play(Synth.S_PLACE);
                say("Fahne gesetzt – der Weg hat jetzt zwei Träger.");
                select(-1);
            });
            demolishButton(c, x, by + 62 * u, in, false, () -> host.exec(new int[]{World.C_REMOVE, me, 0, rr.path[1]}));
        } else {
            buildMenu(c, n, x, y, in, r);
        }
    }

    private void demolishButton(Canvas c, float x, float y, float wd, boolean ask, Runnable act) {
        button(c, x, y, wd, 52 * u, confirmDemolish ? "Wirklich abreißen?" : "Abreißen", confirmDemolish ? 0xFFB71C1C : 0xFF8D3A2A, true, () -> {
            if (ask && !confirmDemolish) confirmDemolish = true;
            else {
                act.run();
                select(-1);
                say("Abgerissen.");
            }
        });
    }

    private void buildMenu(Canvas c, int n, float x, float y, float in, RectF r) {
        int dark = 0xFF3B2A1A;
        if (!w.own(n, me)) {
            if (w.owner[n] != 0) {
                int o = w.owner[n] - 1;
                text(c, "Land von " + World.P_NAMES[o], x, y, 24 * u, World.P_COLORS[o], true, Paint.Align.LEFT);
                wrap(c, "Hier baut Spieler " + World.P_NAMES[o] + ". Such dir freies Land.", x, y + 34 * u, in, 18 * u, dark);
                return;
            }
            text(c, "Fremdes Land", x, y, 24 * u, dark, true, Paint.Align.LEFT);
            wrap(c, "Hier kannst du (noch) nicht bauen. Baue eine Wachstube oder einen Wachturm nahe der Grenze, dann wächst dein Land.", x, y + 34 * u, in, 18 * u, dark);
            return;
        }
        int size = w.buildSize(n, me);
        boolean mine = w.mineOk(n, me);
        boolean flagOk = w.canFlag(n, me);
        text(c, mine ? "Mine bauen" : "Bauen", x, y, 24 * u, dark, true, Paint.Align.LEFT);
        y += 14 * u;
        int[] types;
        if (mine) {
            types = new int[]{World.B_COAL, World.B_IRON, World.B_GOLD, World.B_GRANITE};
            int sg = w.sign[n];
            y = wrap(c, sg > 0 && sg < 9 ? "Das Schild hier sagt: " + World.RES_NAMES[sg] + "." : sg == 9 ? "Der Geologe hat hier nichts gefunden."
                    : "Tipp: Erst einen Geologen schicken, damit du weißt, was im Berg steckt.", x, y + 18 * u, in, 16 * u, 0xFF5D4037);
            size = 4;
        } else {
            String[] tabs = {"Holz & Stein", "Nahrung", "Metall", "Militär"};
            float tw = (in - 18 * u) / 4;
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                float tx = x + k * (tw + 6 * u);
                RectF tr = rect(tx, y, tx + tw, y + 40 * u);
                box(c, tr, buildTab == k ? 0xFF8D6E4A : 0xFFD7C7A6, 10 * u);
                text(c, tabs[k], tx + tw / 2, y + 26 * u, 13.5f * u, buildTab == k ? 0xFFFFFFFF : dark, true, Paint.Align.CENTER);
                btns.add(new Btn(tr, () -> buildTab = kk, tabs[k]));
            }
            y += 46 * u;
            types = buildTab == 0 ? new int[]{World.B_WOOD, World.B_QUARRY, World.B_FOREST, World.B_SAW, World.B_STORE, World.B_SCOUT}
                    : buildTab == 1 ? new int[]{World.B_FISH, World.B_HUNT, World.B_WELL, World.B_FARM, World.B_MILL, World.B_BAKERY, World.B_PIGS, World.B_BUTCHER}
                    : buildTab == 2 ? new int[]{World.B_SMELTER, World.B_TOOLS, World.B_ARMORY}
                    : new int[]{World.B_GUARD, World.B_TOWER, World.B_FORT};
            if (buildTab == 2 && size > 0)
                wrap(c, "Minen baust du direkt auf einem Berg: dort hintippen.", x, y + 240 * u, in, 16 * u, 0xFF5D4037);
        }
        if (size == 0) {
            String why = w.obj[n] == World.O_TREE ? "Hier steht ein Baum."
                    : w.obj[n] == World.O_STONE ? "Hier liegen Steine."
                    : w.terrain[n] == World.T_WATER ? "Das ist Wasser."
                    : w.terrain[n] == World.T_MOUNTAIN ? "Hier passt keine Mine hin – such eine Stelle mit kleinem dunklem Eingang („Bauplätze“)."
                    : w.terrain[n] == World.T_SAND ? "Auf Sand kann man keine Häuser bauen."
                    : "Für ein Haus ist hier kein Platz.";
            y = wrap(c, why, x, y + 20 * u, in, 18 * u, dark);
        }
        float cw = (in - 12 * u) / 2, ch = 104 * u;
        int col = 0;
        float yy = y + 6 * u;
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
                float[] ib = Art.BOX[type];
                float sc = Math.min(cw * 0.92f / (ib[2] - ib[0]), 58 * u / (ib[3] - ib[1]));
                c.translate(bx + cw / 2, yy + 4 * u - ib[1] * sc);
                c.scale(sc, sc);
                ren.bannerFor(me);
                ren.scaleHint = sc;
                ren.buildingSprite(c, type, 0, 0, now);
                ren.scaleHint = 0;
                c.restore();
                text(c, World.B_NAMES[type], bx + cw / 2, yy + 76 * u, 15 * u, ok ? dark : 0xFF8A7D66, true, Paint.Align.CENTER);
                int[] cost = World.B_COST[type];
                String cs = cost[1] + " Bretter" + (cost[2] > 0 ? ", " + cost[2] + (cost[2] == 1 ? " Stein" : " Steine") : "");
                text(c, ok ? cs : "zu wenig Platz", bx + cw / 2, yy + 94 * u, 12.5f * u, ok ? 0xFF6D5A44 : 0xFF8A7D66, false, Paint.Align.CENTER);
                if (ok) {
                    final int tt = type;
                    btns.add(new Btn(br, () -> {
                        if (World.B_SIZE[tt] == 4 ? !w.mineOk(n, me) : w.buildSize(n, me) < World.B_SIZE[tt]) {
                            err("Hier geht das nicht.");
                            return;
                        }
                        host.exec(new int[]{World.C_BUILD, me, tt, n});
                        host.play(Synth.S_PLACE);
                        select(-1);
                        pendBuild = n;
                        pendT = now;
                        resolvePending();
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
                host.exec(new int[]{World.C_FLAG, me, 0, n});
                host.play(Synth.S_PLACE);
                select(-1);
                pendFlag = n;
                pendT = now;
                resolvePending();
                say("Fahne gesetzt. Tippe eine andere Fahne an, um sie zu verbinden.");
            });
        }
    }

    private void menu(Canvas c, int vw, int vh) {
        box(c, rect(0, 0, vw, vh), 0x99000000, 0);
        float mw = 560 * u, mh = 640 * u;
        RectF r = rect(vw / 2f - mw / 2, vh / 2f - mh / 2, vw / 2f + mw / 2, vh / 2f + mh / 2);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, "Talsiedler", vw / 2f, r.top + 54 * u, 34 * u, 0xFF3B2A1A, true, Paint.Align.CENTER);
        text(c, "Stufe 2 – Nahrung, Bergbau, Werkzeug", vw / 2f, r.top + 84 * u, 16 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
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
        button(c, bx, y0, bw, bh, "Lager", 0xFF5D6B3A, true, () -> { win = WIN_STORE; menu = false; });
        button(c, bx2, y0, bw, bh, "Werkzeug", 0xFF5D6B3A, true, () -> { win = WIN_TOOLS; menu = false; });
        y0 += bh + gap;
        button(c, bx, y0, bw, bh, "Ton", 0xFF5D6B3A, true, () -> { win = WIN_SOUND; menu = false; });
        button(c, bx2, y0, bw, bh, "2 Spieler", 0xFF6A1B9A, true, () -> { win = WIN_MP; menu = false; });
        y0 += bh + gap;
        button(c, bx, y0, bw, bh, "Titelbild", 0xFF5D6B3A, true, () -> { intro = true; menu = false; });
        boolean solo = host.role() == 0;
        button(c, bx2, y0, bw, bh, "Neues Spiel", 0xFF8D3A2A, solo, () -> confirmNew = true);
        if (confirmNew) {
            float nw2 = bw * 2 + gap, nh = bh * 5 + gap * 5 + 120 * u;
            RectF nr = rect(bx, r.top + 70 * u, bx + nw2, r.top + 70 * u + nh);
            box(c, rect(nr.left - 14 * u, nr.top - 10 * u, nr.right + 14 * u, nr.bottom + 10 * u), 0xFFE9DCC0, 16 * u);
            btns.add(new Btn(rect(nr.left - 14 * u, nr.top - 10 * u, nr.right + 14 * u, nr.bottom + 10 * u), () -> { }, null));
            text(c, "Neues Spiel – wie möchtest du spielen?", vw / 2f, nr.top + 30 * u, 20 * u, 0xFF3B2A1A, true, Paint.Align.CENTER);
            float ny = nr.top + 50 * u;
            button(c, bx, ny, nw2, bh, "Allein aufbauen (ohne Gegner)", 0xFF2E7D32, true, () -> startNew(0));
            ny += bh + gap + 28 * u;
            text(c, "Gegen den Computer (Rot):", bx, ny - 10 * u, 17 * u, 0xFF6D5A44, true, Paint.Align.LEFT);
            float tw3 = (nw2 - 2 * gap) / 3;
            button(c, bx, ny, tw3, bh, "Leicht", 0xFF8D6E4A, true, () -> startNew(1));
            button(c, bx + tw3 + gap, ny, tw3, bh, "Mittel", 0xFFB26A00, true, () -> startNew(2));
            button(c, bx + 2 * (tw3 + gap), ny, tw3, bh, "Schwer", 0xFFB71C1C, true, () -> startNew(3));
            ny += bh + gap;
            button(c, bx, ny, nw2, bh, "Abbrechen", 0xFF6D5A44, true, () -> confirmNew = false);
        }
        text(c, "Das Spiel wird automatisch gespeichert.", vw / 2f, r.bottom - 34 * u, 15 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
    }

    private void startNew(int ai) {
        confirmNew = false;
        menu = false;
        select(-1);
        ren.roadFrom = -1;
        host.newGame(ai);
        say(ai == 0 ? "Neues Spiel gestartet." : "Neues Spiel gegen den Computer (Rot). Er sitzt im Osten der Karte – viel Glück!");
    }

    private int attackTarget = -1, attackN = 0;

    /** Fenster für ein gegnerisches Militärgebäude: Soldaten ansehen und angreifen */
    private void enemyPanel(Canvas c, World.Building b, float x, float y, float in, RectF r) {
        int dark = 0xFF3B2A1A;
        c.save();
        float[] ib = Art.BOX[b.type];
        float isc = Math.min(2.2f * u, Math.min(84 * u / (ib[3] - ib[1]), in * 0.7f / (ib[2] - ib[0])));
        c.translate(x + in / 2, y + 4 * u - ib[1] * isc);
        c.scale(isc, isc);
        ren.bannerFor(b.player);
        ren.scaleHint = isc;
        ren.buildingSprite(c, b.type, 0, 0, now);
        ren.scaleHint = 0;
        c.restore();
        ren.bannerFor(me);
        y += 100 * u;
        int[] rk = w.insideRanks(b);
        int n = 0;
        for (int v : rk) n += v;
        y = wrap(c, "Soldaten drin: " + n, x, y, in, 18 * u, dark);
        y = rankRow(c, rk, x, y + 4 * u, in);
        int avail = w.attackersFor(b, me);
        if (attackTarget != b.id) {
            attackTarget = b.id;
            attackN = avail;
        }
        attackN = Math.max(avail > 0 ? 1 : 0, Math.min(attackN, avail));
        if (!w.canAttack(b, me)) {
            wrap(c, b.occupied || World.isHq(b) ? "Das kannst du gerade nicht angreifen." : "Noch niemand drin – hier gibt es nichts zu erobern.", x, y + 8 * u, in, 17 * u, 0xFF5D4037);
            return;
        }
        if (avail == 0) {
            wrap(c, "Keine Soldaten in der Nähe. Angreifen können Soldaten aus deinen Wachgebäuden (und dem Hauptquartier), die höchstens "
                    + World.ATTACK_RANGE + " Felder weit weg sind. Jedes Gebäude behält einen Soldaten.", x, y + 8 * u, in, 17 * u, 0xFF5D4037);
            return;
        }
        y = wrap(c, "So viele Soldaten können angreifen: " + avail, x, y + 8 * u, in, 18 * u, dark);
        float by = r.bottom - 190 * u;
        float bw = 70 * u;
        final World.Building fb = b;
        button(c, x, by, bw, 52 * u, "–", 0xFF8D6E4A, attackN > 1, () -> attackN--);
        text(c, attackN + (attackN == 1 ? " Soldat" : " Soldaten"), x + in / 2, by + 34 * u, 22 * u, dark, true, Paint.Align.CENTER);
        button(c, x + in - bw, by, bw, 52 * u, "+", 0xFF8D6E4A, attackN < avail, () -> attackN++);
        button(c, x, by + 62 * u, in, 60 * u, "Angreifen!", 0xFFB71C1C, true, () -> {
            host.exec(new int[]{World.C_ATTACK, me, fb.id, attackN});
            say(attackN + (attackN == 1 ? " Soldat zieht" : " Soldaten ziehen") + " los. Sie kämpfen einzeln gegen die Verteidiger.");
            select(-1);
        });
    }

    /** kleine Helme in Rangfarbe mit Anzahl */
    private float rankRow(Canvas c, int[] rk, float x, float y, float in) {
        float cx = x;
        for (int k = 0; k < 5; k++) {
            if (rk[k] == 0) continue;
            p.setColor(Renderer.RANK_COL[k]);
            rf.set(cx, y, cx + 20 * u, y + 16 * u);
            c.drawArc(rf, 180, 180, true, p);
            c.drawRect(cx, y + 8 * u, cx + 20 * u, y + 11 * u, p);
            text(c, rk[k] + "× " + World.RANK_NAMES[k], cx + 24 * u, y + 13 * u, 13.5f * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            p.setTextSize(13.5f * u);
            cx += 34 * u + p.measureText(rk[k] + "× " + World.RANK_NAMES[k]);
            if (cx > x + in - 120 * u) {
                cx = x;
                y += 22 * u;
            }
        }
        return y + 30 * u;
    }

    private static final String[] HELP = {
            "Ziel: Baue eine Siedlung, die sich selbst mit Holz, Brettern und Steinen versorgt, und vergrößere dein Land.",
            "1. Auf freies Land tippen → Gebäude aussuchen. Es entsteht eine Baustelle mit einer Fahne davor.",
            "2. Jede Fahne braucht einen Weg zum Hauptquartier: Fahne antippen → „Weg bauen“ → andere Fahne antippen. Fahnen werden automatisch gesetzt.",
            "3. Auf jedem Wegstück zwischen zwei Fahnen arbeitet ein Träger. Er reicht die Waren von Fahne zu Fahne weiter.",
            "4. Ein Bauarbeiter kommt, sobald Bretter und Steine da sind, wird gebaut. Danach zieht ein Arbeiter ein.",
            "5. Holzfäller fällt Bäume → Sägewerk macht Bretter. Förster pflanzt neue Bäume. Steinbruch holt Steine.",
            "6. Wachstube (2 Soldaten), Wachturm (4) und Festung (8) an der Grenze: sobald ein Soldat drin ist, wächst dein Land.",
            "7. Viel benutzte Wege werden gepflastert (schneller) und bekommen bei noch mehr Verkehr einen zweiten Träger.",
            "8. Nahrung: Fischer (am Wasser), Jäger (im Wald), Bauernhof → Mühle → Bäckerei (+ Brunnen), Schweinezucht → Metzgerei.",
            "9. Erkunden: An jeder Fahne kannst du einen Späher losschicken – er deckt den Nebel um die Fahne herum auf. Bergbau: Geologen an eine Fahne am Berg schicken, dann die passende Mine auf den Berg bauen. Minen brauchen Nahrung. Eisenschmelze: Erz + Kohle → Eisen. Werkzeugmacher: Eisen + Brett → Werkzeug.",
            "10. Jeder Beruf braucht sein Werkzeug (Axt, Säge, Spitzhacke …). Fehlt eins, steht es beim Gebäude. Menü → „Werkzeug“ bestimmt, was zuerst gemacht wird.",
            "11. Soldaten: Der Waffenschmied macht aus Eisen + Kohle abwechselnd Schwert und Schild. Im Hauptquartier wird daraus mit einem freien Siedler ein neuer Soldat. Gold in einem Wachgebäude macht Soldaten stärker (5 Ränge: Helmfarbe grau, bronze, silber, blau, gold).",
            "12. Kampf (gegen den Computer oder im Bluetooth-Kampf): Gegnerisches Wachgebäude antippen → Anzahl wählen → „Angreifen!“. Die Soldaten kämpfen einzeln gegen die Verteidiger. Gewinnen sie, gehört das Gebäude dir – alles vom Gegner auf dem neuen Land brennt ab. Wer das Hauptquartier des anderen erobert, gewinnt.",
            "13. Lagerhaus: zweites Lager, kurze Wege. Späherhütte: der Späher erkundet den Nebel. Bei Baustellen: „Zuerst bauen“.",
            "14. Zu zweit: Menü → „2 Spieler“. Die Tablets einmal in den Android-Einstellungen per Bluetooth koppeln. Einer tippt „Spiel erstellen“, der andere wählt dieses Tablet aus der Liste.",
            "Bedienen: Mit einem Finger schieben, mit zwei Fingern zoomen. „Bauplätze“ zeigt, wo was hinpasst: gelb = klein, orange = groß. „Auslastung“ zeigt, wie fleißig jedes Haus ist. Oben auf die Waren tippen öffnet das Lager. Die kleine Karte unten links bringt dich mit einem Tipp überall hin.",
    };

    private void help(Canvas c, int vw, int vh) {
        box(c, rect(0, 0, vw, vh), 0xAA000000, 0);
        float mw = Math.min(vw - 60 * u, 820 * u), mh = vh - 80 * u;
        RectF r = rect(vw / 2f - mw / 2, 40 * u, vw / 2f + mw / 2, 40 * u + mh);
        box(c, r, 0xFFE9DCC0, 18 * u);
        text(c, "So geht's", r.left + 30 * u, r.top + 50 * u, 30 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
        int[] starts = {0, 7, 12, HELP.length};
        int pages = starts.length - 1;
        helpPage = Math.max(0, Math.min(pages - 1, helpPage));
        text(c, "Seite " + (helpPage + 1) + " von " + pages, r.right - 30 * u, r.top + 50 * u, 17 * u, 0xFF8D6E4A, false, Paint.Align.RIGHT);
        float y = r.top + 92 * u;
        for (int i = starts[helpPage]; i < starts[helpPage + 1]; i++)
            y = wrap(c, HELP[i], r.left + 30 * u, y, mw - 60 * u, 19 * u, 0xFF3B2A1A) + 8 * u;
        if (helpPage > 0)
            button(c, r.left + 30 * u, r.bottom - 76 * u, 170 * u, 56 * u, "Zurück", 0xFF8D6E4A, true, () -> helpPage--);
        if (helpPage < pages - 1)
            button(c, r.right - 200 * u, r.bottom - 76 * u, 170 * u, 56 * u, "Weiter", 0xFF1565C0, true, () -> helpPage++);
        else
            button(c, r.right - 200 * u, r.bottom - 76 * u, 170 * u, 56 * u, "Alles klar", 0xFF2E7D32, true, () -> { help = false; helpPage = 0; });
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
        RectF r = window(c, vw, vh, 1000 * u, 560 * u, "Lager & Leute");
        int dark = 0xFF3B2A1A;
        int[] onWay = new int[World.NWARES];
        for (World.Flag f : w.flags.values()) if (f.player == me) for (World.Ware x : f.wares) onWay[x.type]++;
        int carriers = 0, builders = 0, workers = 0, soldiersOut = 0;
        for (World.Settler s : w.settlers.values()) {
            if (s.player != me) continue;
            if (s.carry != null) onWay[s.carry.type]++;
            if (s.job == World.J_CARRIER) carriers++;
            else if (s.job == World.J_BUILDER) builders++;
            else if (s.job == World.J_SOLDIER) soldiersOut++;
            else workers++;
        }
        float x = r.left + 26 * u, y = r.top + 78 * u, in = r.width() - 52 * u;
        String[] groups = {"Baustoffe", "Nahrung", "Bergbau & Metall", "Werkzeug", "Waffen"};
        int[][] gw = {{0, 1, 2}, {3, 4, 5, 6, 7, 8, 9}, {10, 11, 12, 13}, {14, 15, 16, 17, 18, 19, 20, 21, 22, 23}, {24, 25}};
        float cw = in / 10f, ch = 74 * u;
        float gx = x;
        // erste Zeile: Baustoffe + Bergbau, zweite: Nahrung, dritte: Werkzeug
        int[][] layout = {{0, 2}, {1, 4}, {3}};
        for (int[] row : layout) {
            gx = x;
            for (int gi : row) {
                text(c, groups[gi], gx, y, 17 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
                for (int k = 0; k < gw[gi].length; k++) {
                    int t = gw[gi][k];
                    float bx = gx + k * cw;
                    RectF cell = rect(bx, y + 8 * u, bx + cw - 6 * u, y + 8 * u + ch);
                    box(c, cell, 0xFFF7EEDA, 10 * u);
                    c.save();
                    c.translate(bx + cw / 2 - 3 * u, y + 34 * u);
                    c.scale(3f * u, 3f * u);
                    ren.ware(c, t, 0, 0, 1f);
                    c.restore();
                    int val = w.stockTotal(me, t);
                    text(c, String.valueOf(val), bx + cw / 2 - 3 * u, y + 64 * u, 18 * u, val == 0 ? 0xFFB0A288 : dark, true, Paint.Align.CENTER);
                    text(c, World.WARE_NAMES[t], bx + cw / 2 - 3 * u, y + 78 * u, 10.5f * u, 0xFF6D5A44, false, Paint.Align.CENTER);
                    if (onWay[t] > 0) text(c, "+" + onWay[t], bx + cw - 10 * u, y + 24 * u, 11 * u, 0xFF2E7D32, true, Paint.Align.RIGHT);
                }
                gx += gw[gi].length * cw + 24 * u;
            }
            y += ch + 40 * u;
        }
        text(c, "Leute", x, y, 17 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 26 * u;
        String[][] rows = {
                {"Freie Siedler", w.pool(me) + " / " + World.POOL_MAX},
                {"Träger", String.valueOf(carriers)},
                {"Bauarbeiter unterwegs", String.valueOf(builders)},
                {"Arbeiter in Gebäuden", String.valueOf(workers)},
                {"Soldaten im Hauptquartier", String.valueOf(w.soldiers(me))},
                {"Soldaten in Wachgebäuden", String.valueOf(soldiersOut)}};
        float colW = in / 3;
        for (int i = 0; i < rows.length; i++) {
            float rx = x + (i % 3) * colW, ry = y + (i / 3) * 28 * u;
            text(c, rows[i][0], rx, ry, 15 * u, dark, false, Paint.Align.LEFT);
            text(c, rows[i][1], rx + colW - 24 * u, ry, 16 * u, dark, true, Paint.Align.RIGHT);
        }
        y += 2 * 28 * u + 6 * u;
        StringBuilder sb = new StringBuilder();
        int[] pf = w.profs(me);
        for (int j = 0; j < World.NJ; j++)
            if (pf[j] > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(pf[j]).append(' ').append(World.J_NAMES[j]);
            }
        wrap(c, "Warten ausgebildet im Hauptquartier: " + (sb.length() == 0 ? "niemand" : sb.toString()) + ". Das grüne +Zahl ist unterwegs auf den Wegen.",
                x, y, in, 14 * u, 0xFF6D5A44);
    }

    // ---------- Werkzeug ----------

    private void toolsWin(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 980 * u, 640 * u, "Werkzeug");
        int dark = 0xFF3B2A1A;
        float x = r.left + 28 * u, y = r.top + 80 * u, in = r.width() - 56 * u;
        y = wrap(c, "Der Werkzeugmacher macht immer zuerst, worauf gerade ein Gebäude wartet. Sonst richtet er sich nach dem Vorrang: 0 = gar nicht, 10 = ganz oft.",
                x, y, in, 16 * u, 0xFF6D5A44) + 6 * u;
        int[] pr = w.toolPrio(me);
        World.Building h = w.hqB(me);
        float colW = (in - 20 * u) / 2, rh = 82 * u;
        int[] users = {World.J_WOODCUTTER, World.J_SAWYER, World.J_MASON, World.J_BUILDER, World.J_FORESTER, World.J_FISHER, World.J_FARMER, World.J_HUNTER, World.J_BUTCHER, World.J_BAKER};
        for (int i = 0; i < World.NTOOLS; i++) {
            final int ti = i;
            int t = World.FIRST_TOOL + i;
            float rx = x + (i % 2) * (colW + 20 * u), ry = y + (i / 2) * rh;
            box(c, rect(rx, ry, rx + colW, ry + rh - 8 * u), 0xFFF7EEDA, 12 * u);
            c.save();
            c.translate(rx + 36 * u, ry + 37 * u);
            c.scale(4f * u, 4f * u);
            ren.ware(c, t, 0, 0, 1f);
            c.restore();
            boolean waiting = false;
            for (World.Building b : w.buildings.values()) if (b.player == me && b.toolWait == t) waiting = true;
            if (waiting) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * u);
                p.setColor(0xFFC62828);
                c.drawRoundRect(rect(rx, ry, rx + colW, ry + rh - 8 * u), 12 * u, 12 * u, p);
                p.setStyle(Paint.Style.FILL);
            }
            text(c, World.WARE_NAMES[t] + "  ·  " + h.stock[t] + " da", rx + 70 * u, ry + 28 * u, 17 * u, dark, true, Paint.Align.LEFT);
            text(c, waiting ? "Wird gerade gebraucht!" : "für " + J_FOR_TOOL(i, users), rx + 70 * u, ry + 50 * u, 12.5f * u,
                    waiting ? 0xFFC62828 : 0xFF6D5A44, waiting, Paint.Align.LEFT);
            float bx = rx + colW - 204 * u;
            button(c, bx, ry + 14 * u, 46 * u, 46 * u, "–", 0xFF8D6E4A, true, () -> host.exec(new int[]{World.C_TOOLPRIO, me, ti, pr[ti] - 1}));
            box(c, rect(bx + 54 * u, ry + 18 * u, bx + 142 * u, ry + 56 * u), 0xFFE0D2B4, 8 * u);
            text(c, String.valueOf(pr[i]), bx + 98 * u, ry + 45 * u, 22 * u, pr[i] == 0 ? 0xFFB0A288 : dark, true, Paint.Align.CENTER);
            button(c, bx + 150 * u, ry + 14 * u, 46 * u, 46 * u, "+", 0xFF8D6E4A, true, () -> host.exec(new int[]{World.C_TOOLPRIO, me, ti, pr[ti] + 1}));
        }
    }

    private String J_FOR_TOOL(int i, int[] users) {
        String who = World.J_NAMES[users[i]];
        if (i == World.T_PICK - World.FIRST_TOOL) who = "Steinmetz, Bergmann";
        if (i == World.T_HAMMER - World.FIRST_TOOL) who = "Bauarbeiter, Geologe";
        return who;
    }

    private void stats(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 900 * u, 640 * u, "Statistik");
        java.util.ArrayList<int[]> st = w.statsOf(me);
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
        java.util.ArrayList<World.Msg> list = new java.util.ArrayList<>();
        for (World.Msg m : w.msgs) if (mine(m)) list.add(m);
        if (list.isEmpty()) {
            text(c, "Noch keine Meldungen.", r.left + 30 * u, r.top + 100 * u, 19 * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            return;
        }
        float y = r.top + 80 * u;
        int rows = (int) ((r.bottom - y - 20 * u) / (54 * u));
        for (int i = list.size() - 1, k = 0; i >= 0 && k < rows; i--, k++) {
            final World.Msg m = list.get(i);
            RectF br = rect(r.left + 24 * u, y, r.right - 24 * u, y + 46 * u);
            box(c, br, 0xFFF7EEDA, 10 * u);
            text(c, m.text, br.left + 16 * u, br.centerY() + 6 * u, 17 * u, 0xFF3B2A1A, false, Paint.Align.LEFT);
            text(c, ago(m.time), br.right - 16 * u, br.centerY() + 6 * u, 14 * u, 0xFF8D7A60, false, Paint.Align.RIGHT);
            btns.add(new Btn(br, () -> jump(m.node), null));
            y += 54 * u;
        }
    }

    private void slots(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 900 * u, 470 * u, "Spielstände");
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
            text(c, "Platz " + i + (info == null ? "" : "  –  " + l1), br.left + 18 * u, br.top + 34 * u, 19 * u, 0xFF3B2A1A, true, Paint.Align.LEFT);
            text(c, info == null ? "leer" : l2, br.left + 18 * u, br.top + 64 * u, 14 * u, 0xFF6D5A44, false, Paint.Align.LEFT);
            boolean askSave = confirmSlot == slot && confirmSlotKind == 1;
            boolean askDel = confirmSlot == slot && confirmSlotKind == 2;
            boolean solo = host.role() == 0;
            button(c, br.right - 438 * u, br.top + 16 * u, 136 * u, 52 * u, askSave ? "Überschreiben?" : "Speichern", askSave ? 0xFFB71C1C : 0xFF1565C0, host.role() != 2, () -> {
                if (info != null && !askSave) {
                    confirmSlot = slot;
                    confirmSlotKind = 1;
                    return;
                }
                confirmSlot = 0;
                host.saveSlot(slot);
                slotNote = "Auf Platz " + slot + " gespeichert.";
            });
            button(c, br.right - 294 * u, br.top + 16 * u, 136 * u, 52 * u, "Laden", 0xFF2E7D32, info != null && solo, () -> {
                confirmSlot = 0;
                if (host.loadSlot(slot)) {
                    win = WIN_NONE;
                    say("Spielstand " + slot + " geladen.");
                } else slotNote = "Laden hat nicht geklappt.";
            });
            button(c, br.right - 150 * u, br.top + 16 * u, 134 * u, 52 * u, askDel ? "Wirklich?" : "Löschen", askDel ? 0xFFB71C1C : 0xFF8D3A2A, info != null, () -> {
                if (!askDel) {
                    confirmSlot = slot;
                    confirmSlotKind = 2;
                    return;
                }
                confirmSlot = 0;
                host.deleteSlot(slot);
                slotNote = "Platz " + slot + " gelöscht.";
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

    // ---------- 2 Spieler ----------

    private void mpWin(Canvas c, int vw, int vh) {
        RectF r = window(c, vw, vh, 880 * u, 640 * u, "2 Spieler über Bluetooth");
        int dark = 0xFF3B2A1A;
        float x = r.left + 30 * u, y = r.top + 90 * u, in = r.width() - 60 * u;
        int st = host.mpState();
        String txt = host.mpText();
        if (st != 0) {
            box(c, rect(x, y - 6 * u, x + in, y + 70 * u), 0xFFF7EEDA, 12 * u);
            y = wrap(c, txt == null ? "" : txt, x + 18 * u, y + 26 * u, in - 36 * u, 19 * u, dark);
            button(c, x, r.bottom - 84 * u, in, 58 * u, st == 3 ? "Verbindung trennen" : "Abbrechen", 0xFF8D3A2A, true, () -> host.mpLeave());
            if (st == 3) {
                float yy = r.top + 200 * u;
                String mode = w.mode == 1 ? "Miteinander – ihr baut zusammen an einer Siedlung." : w.mode == 2 ? "Wettbauen – " + goalText() + "."
                        : w.mode == 3 ? "Kampf – wer das Hauptquartier des anderen erobert, gewinnt." : "";
                yy = wrap(c, mode, x, yy, in, 19 * u, dark) + 6 * u;
                if (w.mode == 2 || w.mode == 3)
                    wrap(c, "Du bist Spieler " + World.P_NAMES[me] + ".", x, yy, in, 19 * u, World.P_COLORS[me]);
                if (host.role() == 2)
                    wrap(c, "Tempo und Pause bestimmt der Gastgeber.", x, yy + 34 * u, in, 17 * u, 0xFF6D5A44);
            }
            return;
        }
        if (txt != null) y = wrap(c, txt, x, y, in, 17 * u, 0xFFB71C1C) + 6 * u;
        text(c, "Spiel erstellen (du bist der Gastgeber)", x, y, 20 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 14 * u;
        float bw = (in - 36 * u) / 4;
        button(c, x, y, bw, 70 * u, "Miteinander", 0xFF2E7D32, true, () -> host.mpHost(1, 0, 0));
        button(c, x + bw + 12 * u, y, bw, 70 * u, "Wettbauen: Land", 0xFF6A1B9A, true, () -> host.mpHost(2, 1, 450));
        button(c, x + 2 * (bw + 12 * u), y, bw, 70 * u, "Wettbauen: Bretter", 0xFF6A1B9A, true, () -> host.mpHost(2, 2, 150));
        button(c, x + 3 * (bw + 12 * u), y, bw, 70 * u, "Kampf", 0xFFB71C1C, true, () -> host.mpHost(3, 0, 0));
        y += 96 * u;
        y = wrap(c, "Miteinander: dein jetziges Spiel wird geteilt. Wettbauen: neue Karte, jeder hat sein eigenes Hauptquartier – wer zuerst 450 Felder Land bzw. 150 Bretter im Lager hat, gewinnt. Kampf: neue Karte, wer das Hauptquartier des anderen erobert, gewinnt.",
                x, y, in, 16 * u, 0xFF6D5A44) + 14 * u;
        text(c, "Beitreten (Spiel des anderen)", x, y, 20 * u, 0xFF8D6E4A, true, Paint.Align.LEFT);
        y += 14 * u;
        String[] dev = host.mpDevices();
        if (dev == null) {
            wrap(c, "Bluetooth ist aus oder das Spiel darf es noch nicht benutzen. Bitte Bluetooth einschalten und erlauben, dann dieses Fenster neu öffnen.", x, y + 20 * u, in, 17 * u, 0xFFB71C1C);
        } else if (dev.length == 0) {
            wrap(c, "Keine gekoppelten Geräte. Koppel die beiden Tablets einmal in den Android-Einstellungen unter Bluetooth.", x, y + 20 * u, in, 17 * u, 0xFFB71C1C);
        } else {
            float dw = (in - 12 * u) / 2;
            for (int i = 0; i < dev.length && i < 6; i++) {
                final int idx = i;
                float bx = x + (i % 2) * (dw + 12 * u), by = y + (i / 2) * 62 * u;
                button(c, bx, by, dw, 52 * u, dev[i], 0xFF1565C0, true, () -> host.mpJoin(idx));
            }
            y += ((Math.min(dev.length, 6) + 1) / 2) * 62 * u;
        }
        wrap(c, "Tipp: Erst auf dem einen Tablet „Spiel erstellen“ tippen, dann auf dem anderen das Tablet aus der Liste wählen.", x, r.bottom - 30 * u, in, 15 * u, 0xFF6D5A44);
    }

    private String goalText() {
        return w.goalType == 1 ? "wer zuerst " + w.goalValue + " Felder Land hat, gewinnt" : "wer zuerst " + w.goalValue + " Bretter im Lager hat, gewinnt";
    }

    private void raceBar(Canvas c, int vw) {
        float bw = 520 * u, x = vw / 2f - bw / 2, y = 66 * u;
        if (ren.roadFrom >= 0) y = 158 * u;
        box(c, rect(x, y, x + bw, y + 40 * u), 0xE0302318, 10 * u);
        String unit = w.goalType == 1 ? " Land" : " Bretter";
        text(c, "Ziel " + w.goalValue + unit, x + 14 * u, y + 26 * u, 15 * u, 0xFFFFD54F, true, Paint.Align.LEFT);
        float bl = x + 170 * u, br = x + bw - 14 * u, half = (br - bl - 10 * u) / 2;
        for (int pl = 0; pl < 2; pl++) {
            float l = bl + pl * (half + 10 * u);
            box(c, rect(l, y + 12 * u, l + half, y + 28 * u), 0xFF5A4A3A, 6 * u);
            float f = Math.min(1f, w.goalProgress(pl) / (float) Math.max(1, w.goalValue));
            box(c, rect(l, y + 12 * u, l + half * f, y + 28 * u), World.P_COLORS[pl], 6 * u);
            text(c, World.P_NAMES[pl] + " " + w.goalProgress(pl), l + half / 2, y + 25 * u, 12 * u, 0xFFFFFFFF, true, Paint.Align.CENTER);
        }
    }

    private void winner(Canvas c, int vw, int vh) {
        int wp = w.winner - 1;
        float bw = 620 * u, bh = 200 * u;
        RectF r = rect(vw / 2f - bw / 2, vh / 2f - bh / 2, vw / 2f + bw / 2, vh / 2f + bh / 2);
        box(c, rect(r.left + 6 * u, r.top + 8 * u, r.right + 6 * u, r.bottom + 8 * u), 0x66000000, 20 * u);
        box(c, r, 0xF2E9DCC0, 20 * u);
        text(c, wp == me ? "Gewonnen!" : "Leider verloren", vw / 2f, r.top + 70 * u, 46 * u, World.P_COLORS[wp], true, Paint.Align.CENTER);
        text(c, w.combat() ? "Das Hauptquartier von " + World.P_NAMES[1 - wp] + " ist gefallen." : "Spieler " + World.P_NAMES[wp] + " hat das Ziel zuerst erreicht.",
                vw / 2f, r.top + 116 * u, 20 * u, 0xFF3B2A1A, false, Paint.Align.CENTER);
        text(c, "Antippen zum Schließen – ihr könnt einfach weiterspielen.", vw / 2f, r.top + 150 * u, 16 * u, 0xFF6D5A44, false, Paint.Align.CENTER);
        btns.add(new Btn(rect(0, 0, vw, vh), () -> winnerSeen = w.winner, null));
    }
}
