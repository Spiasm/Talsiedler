package de.spiasm.talsiedler;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity implements Hud.Host, BtLink.Listener {
    private static final String PERM_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final int REQ_PERM = 7;

    private GameView view;
    private Sound sound;
    private SharedPreferences prefs;
    private final Session session = new Session(null);
    private BtLink link;
    private int mpState;
    private String mpText;
    private boolean asked;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    // was nach dem Verbinden gestartet werden soll (Gastgeber)
    private int hostMode, hostGoalType, hostGoalValue;
    private boolean joining;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("talsiedler", MODE_PRIVATE);
        sound = new Sound(this);
        view = new GameView(this, this);
        view.session = session;
        view.hud.fxVol = prefs.getInt("fx", 7);
        view.hud.ambVol = prefs.getInt("amb", 5);
        sound.setVolumes(view.hud.fxVol, view.hud.ambVol);
        World w = read(file());
        if (w == null) w = new World(System.currentTimeMillis());
        if (w.ai == 0) w.mode = 0;
        session.w = w;
        view.setWorld(w);
        setContentView(view);
        hideBars();
    }

    private void hideBars() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
        sound.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sound.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (link != null) link.close();
        sound.release();
    }

    // ---------------- Speichern ----------------

    private File file() {
        return new File(getFilesDir(), "spielstand.dat");
    }

    private File slotFile(int i) {
        return new File(getFilesDir(), "platz" + i + ".dat");
    }

    private World read(File f) {
        if (!f.exists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            World w = (World) in.readObject();
            w.fixup();
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean write(World w, File target) {
        File tmp = new File(target.getPath() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeObject(w);
        } catch (Exception e) {
            return false;
        }
        return tmp.renameTo(target);
    }

    /** Das eigene Spiel speichern – aber nie ein fremdes oder ein Wettbau-Spiel über das eigene. */
    @Override
    public void save() {
        World w = view.world();
        if (w == null) return;
        if (session.role == Session.GUEST) return;
        if (session.role != Session.SOLO || w.players > 1 && w.ai == 0) return;
        write(w, file());
    }

    @Override
    public void newGame(int ai, int mapType, int mapSize) {
        World w;
        if (ai > 0) {
            w = new World(System.currentTimeMillis(), 2, 4, mapType, mapSize);
            w.ai = ai;
        } else w = new World(System.currentTimeMillis(), 1, 0, mapType, mapSize);
        view.hud.me = 0;
        view.ren.me = 0;
        session.w = w;
        view.setWorld(w);
        save();
    }

    @Override
    public void quit() {
        save();
        if (link != null) link.close();
        link = null;
        finishAndRemoveTask();
    }

    @Override
    public void play(int s) {
        sound.ui(s);
    }

    @Override
    public void saveSlot(int i) {
        World w = view.world();
        if (w == null || !write(w, slotFile(i))) return;
        int land = w.landCount[view.hud.me];
        int mins = (int) (w.time / 60);
        String info = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date())
                + "  ·  Spielzeit " + (mins / 60) + ":" + String.format(Locale.GERMANY, "%02d", mins % 60) + " h"
                + "  ·  " + World.MAP_NAMES[w.mapType] + " " + w.mapW + "×" + w.mapH + "  ·  Land " + land
                + (w.ai > 0 ? "  ·  gegen Computer (" + (w.ai == 1 ? "leicht" : w.ai == 2 ? "mittel" : "schwer") + ")" : w.players > 1 ? "  ·  2 Spieler" : "");
        prefs.edit().putString("slot" + i, info).apply();
    }

    @Override
    public boolean loadSlot(int i) {
        if (session.role != Session.SOLO) return false;
        World w = read(slotFile(i));
        if (w == null) return false;
        if (w.ai > 0) w.mode = 4;
        else if (w.players > 1) w.mode = 2;
        else w.mode = 0;
        session.w = w;
        view.hud.me = 0;
        view.setWorld(w);
        save();
        return true;
    }

    @Override
    public String slotInfo(int i) {
        if (!slotFile(i).exists()) return null;
        return prefs.getString("slot" + i, "gespeichert");
    }

    @Override
    public void deleteSlot(int i) {
        //noinspection ResultOfMethodCallIgnored
        slotFile(i).delete();
        prefs.edit().remove("slot" + i).apply();
    }

    @Override
    public void volumes(int fx, int amb) {
        sound.setVolumes(fx, amb);
        prefs.edit().putInt("fx", fx).putInt("amb", amb).apply();
    }

    @Override
    public void frame(World w, Renderer r, float dt, boolean paused) {
        sound.frame(w, r, dt, paused);
    }

    @Override
    public void exec(int[] cmd) {
        if (session.role == Session.SOLO) session.w = view.world();
        session.local(cmd);
    }

    @Override
    public int role() {
        return session.role;
    }

    // ---------------- Bluetooth ----------------

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        if (checkSelfPermission(PERM_CONNECT) == PackageManager.PERMISSION_GRANTED) return true;
        if (!asked) {
            asked = true;
            requestPermissions(new String[]{PERM_CONNECT}, REQ_PERM);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED))
            mpText = "Ohne Erlaubnis für „Geräte in der Nähe“ geht Bluetooth nicht. Bitte in den Einstellungen der App erlauben.";
    }

    @Override
    public int mpState() {
        return mpState;
    }

    @Override
    public String mpText() {
        return mpText;
    }

    @Override
    public String[] mpDevices() {
        BluetoothAdapter a = adapter();
        if (a == null || !allowed()) return null;
        try {
            if (!a.isEnabled()) return null;
            devices.clear();
            devices.addAll(a.getBondedDevices());
            String[] names = new String[devices.size()];
            for (int i = 0; i < names.length; i++) {
                String n = devices.get(i).getName();
                names[i] = n == null ? devices.get(i).getAddress() : n;
            }
            return names;
        } catch (SecurityException e) {
            return null;
        }
    }

    @Override
    public void mpHost(int mode, int goalType, int goalValue) {
        BluetoothAdapter a = adapter();
        if (a == null || !allowed()) {
            mpText = "Bluetooth ist nicht bereit.";
            return;
        }
        hostMode = mode;
        hostGoalType = goalType;
        hostGoalValue = goalValue;
        joining = false;
        if (link != null) link.close();
        link = new BtLink(this);
        link.host(a);
        mpState = 1;
        mpText = "Warte auf den Mitspieler … Auf dem anderen Tablet jetzt „2 Spieler“ öffnen und dieses Tablet antippen.";
    }

    @Override
    public void mpJoin(int index) {
        BluetoothAdapter a = adapter();
        if (a == null || index < 0 || index >= devices.size() || !allowed()) return;
        joining = true;
        if (link != null) link.close();
        link = new BtLink(this);
        link.join(devices.get(index), a);
        mpState = 2;
        mpText = "Verbinde mit " + mpDevicesName(index) + " …";
    }

    private String mpDevicesName(int i) {
        try {
            String n = devices.get(i).getName();
            return n == null ? "dem Tablet" : n;
        } catch (SecurityException e) {
            return "dem Tablet";
        }
    }

    @Override
    public void mpLeave() {
        if (link != null) link.close();
        link = null;
        endSession("Verbindung getrennt.");
    }

    @Override
    public void onConnected(String peerName) {
        mpState = 3;
        if (!joining) {
            // Gastgeber: Spiel festlegen und an den Gast schicken
            World w;
            if (hostMode == 2 || hostMode == 3) {
                save();
                w = new World(System.currentTimeMillis(), 2, hostMode, view.hud.mapType, World.MAP_SIZES[view.hud.mapSize]);
                w.goalType = hostGoalType;
                w.goalValue = hostGoalValue;
                view.hud.me = 0;
                session.w = w;
                view.setWorld(w);
            } else {
                w = view.world();
                if (w.players > 1) {
                    w = new World(System.currentTimeMillis());
                    session.w = w;
                    view.setWorld(w);
                }
                w.mode = 1;
            }
            session.startHost(w, line -> {
                BtLink l = link;
                if (l != null) l.send(line);
            });
            mpText = "Verbunden mit " + peerName + ". Viel Spaß!";
        } else {
            save();
            session.startGuest(line -> {
                BtLink l = link;
                if (l != null) l.send(line);
            }, (nw, first) -> {
                view.hud.me = session.me;
                view.ren.me = session.me;
                if (first) {
                    view.setWorld(nw);
                    view.centerOnMine();
                } else view.replaceWorld(nw);
            });
            mpText = "Verbunden mit " + peerName + ". Das Spiel wird geladen …";
        }
        view.hud.say("Verbunden mit " + peerName + ".");
    }

    @Override
    public void onMessage(String line) {
        session.onMessage(line);
        if (session.role == Session.GUEST && session.ready() && mpText != null && mpText.endsWith("geladen …"))
            mpText = "Verbunden. Viel Spaß!";
    }

    @Override
    public void onClosed(String reason) {
        link = null;
        endSession(reason);
    }

    private void endSession(String reason) {
        int role = session.role;
        World w = view.world();
        session.stop();
        mpState = 0;
        mpText = role == Session.SOLO ? null : reason;
        view.hud.me = 0;
        view.ren.me = 0;
        if (role == Session.GUEST || (w != null && w.players > 1)) {
            // zurück zum eigenen Spiel
            World own = read(file());
            if (own == null) own = new World(System.currentTimeMillis());
            if (own.ai == 0) own.mode = 0;
            session.w = own;
            view.setWorld(own);
        } else if (w != null) {
            if (w.ai == 0) w.mode = 0;
            session.w = w;
        }
        if (role != Session.SOLO) view.hud.say(reason + " Du spielst wieder dein eigenes Spiel.");
    }
}
