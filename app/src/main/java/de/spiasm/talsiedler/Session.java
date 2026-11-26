package de.spiasm.talsiedler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gleichlauf zweier Tablets.
 * Der Gastgeber rechnet das Spiel und schickt pro Bild „so viele Schritte weiter, vorher diese Befehle“.
 * Der Gast rechnet genau dieselben Schritte mit. Alle 100 Schritte wird eine Prüfsumme verglichen;
 * stimmt sie nicht, schickt der Gastgeber den kompletten Spielstand neu.
 * Nachrichten sind einfache Textzeilen.
 */
public class Session {
    public interface Link {
        void send(String line);
    }

    public interface Listener {
        /** Gast hat einen (neuen) Spielstand bekommen */
        void worldReplaced(World w, boolean first);
    }

    public static final int SOLO = 0, HOST = 1, GUEST = 2;
    public static final float DT = 0.05f;

    public int role = SOLO;
    /** eigener Spieler (0 = blau, 1 = rot) */
    public int me;
    /** Spieler des Gegenübers */
    public int peer;
    public World w;
    public int resyncs;
    private Link link;
    private Listener listener;
    private final ArrayList<int[]> queue = new ArrayList<>();
    private boolean ready, everReady;
    private long hashStep = -1, hashVal;
    private final ArrayList<String> hashes = new ArrayList<>();

    public Session(World w) {
        this.w = w;
    }

    // ---------------- Starten ----------------

    public void startHost(World world, Link l) {
        w = world;
        link = l;
        role = HOST;
        me = 0;
        peer = world.players > 1 ? 1 : 0;
        queue.clear();
        sendSnapshot();
    }

    public void startGuest(Link l, Listener ls) {
        link = l;
        listener = ls;
        role = GUEST;
        ready = false;
    }

    public void stop() {
        role = SOLO;
        link = null;
        queue.clear();
        me = 0;
    }

    public boolean ready() {
        return role != GUEST || ready;
    }

    // ---------------- Befehle ----------------

    /** Befehl von der eigenen Bedienung. */
    public void local(int[] cmd) {
        cmd[1] = me;
        if (role == GUEST) {
            if (link != null && ready) link.send("C " + enc(cmd));
        } else if (role == HOST) {
            queue.add(cmd);
        } else {
            w.apply(cmd);
        }
    }

    static String enc(int[] c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(c[i]);
        }
        return sb.toString();
    }

    static int[] dec(String s) {
        String[] p = s.split(",");
        int[] c = new int[Math.max(4, p.length)];
        for (int i = 0; i < p.length; i++) c[i] = Integer.parseInt(p[i].trim());
        return c;
    }

    // ---------------- Gastgeber ----------------

    /** Ein Bild lang weiterrechnen (Gastgeber). */
    public void hostFrame(int nSteps) {
        StringBuilder cmds = new StringBuilder();
        for (int[] c : queue) {
            w.apply(c);
            if (cmds.length() > 0) cmds.append(';');
            cmds.append(enc(c));
        }
        queue.clear();
        hashes.clear();
        for (int i = 0; i < nSteps; i++) {
            w.update(DT);
            if (w.steps % 100 == 0) hashes.add("H " + w.steps + " " + w.hash());
        }
        if (link != null && (nSteps > 0 || cmds.length() > 0)) {
            link.send("F " + nSteps + " " + (cmds.length() == 0 ? "-" : cmds));
            for (String h : hashes) link.send(h);
        }
    }

    void sendSnapshot() {
        if (link == null) return;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(bo))) {
                out.writeObject(w);
            }
            link.send("SNAP " + w.steps + " " + peer + " " + Base64.getEncoder().encodeToString(bo.toByteArray()));
        } catch (Exception ignored) {
        }
    }

    // ---------------- Nachrichten ----------------

    public void onMessage(String line) {
        if (line == null || line.isEmpty()) return;
        int sp = line.indexOf(' ');
        String kind = sp < 0 ? line : line.substring(0, sp);
        String rest = sp < 0 ? "" : line.substring(sp + 1);
        if (role == HOST) {
            if (kind.equals("C")) {
                try {
                    int[] c = dec(rest);
                    c[1] = peer; // der Gast darf nur für seinen Spieler bauen
                    queue.add(c);
                } catch (Exception ignored) {
                }
            } else if (kind.equals("RS")) {
                resyncs++;
                sendSnapshot();
            }
            return;
        }
        if (role != GUEST) return;
        switch (kind) {
            case "SNAP": {
                String[] p = rest.split(" ", 3);
                try {
                    byte[] data = Base64.getDecoder().decode(p[2]);
                    World nw;
                    try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
                        nw = (World) in.readObject();
                    }
                    nw.fixup();
                    boolean first = !everReady;
                    w = nw;
                    me = Integer.parseInt(p[1]);
                    ready = true;
                    everReady = true;
                    hashStep = -1;
                    if (listener != null) listener.worldReplaced(nw, first);
                } catch (Exception e) {
                    if (link != null) link.send("RS");
                }
                break;
            }
            case "F": {
                if (!ready) return;
                String[] p = rest.split(" ", 2);
                int n = Integer.parseInt(p[0]);
                if (p.length > 1 && !p[1].equals("-")) {
                    for (String cs : p[1].split(";")) {
                        try {
                            w.apply(dec(cs));
                        } catch (Exception ignored) {
                        }
                    }
                }
                for (int i = 0; i < n; i++) {
                    w.update(DT);
                    if (w.steps % 100 == 0) {
                        hashStep = w.steps;
                        hashVal = w.hash();
                    }
                }
                break;
            }
            case "H": {
                if (!ready) return;
                String[] p = rest.split(" ");
                long step = Long.parseLong(p[0]), h = Long.parseLong(p[1]);
                if (step != hashStep || h != hashVal) {
                    resyncs++;
                    ready = false;
                    if (link != null) link.send("RS");
                }
                break;
            }
        }
    }
}
