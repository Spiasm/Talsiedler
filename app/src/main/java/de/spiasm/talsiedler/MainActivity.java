package de.spiasm.talsiedler;

import android.app.Activity;
import android.content.SharedPreferences;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements Hud.Host {
    private GameView view;
    private Sound sound;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("talsiedler", MODE_PRIVATE);
        sound = new Sound(this);
        view = new GameView(this, this);
        view.hud.fxVol = prefs.getInt("fx", 7);
        view.hud.ambVol = prefs.getInt("amb", 5);
        sound.setVolumes(view.hud.fxVol, view.hud.ambVol);
        World w = read(file());
        if (w == null) w = new World(System.currentTimeMillis());
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
        sound.release();
    }

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

    @Override
    public void save() {
        World w = view.world();
        if (w != null) write(w, file());
    }

    @Override
    public void newGame() {
        view.setWorld(new World(System.currentTimeMillis()));
        save();
    }

    @Override
    public void play(int s) {
        sound.ui(s);
    }

    @Override
    public void saveSlot(int i) {
        World w = view.world();
        if (w == null || !write(w, slotFile(i))) return;
        int land = 0;
        for (boolean o : w.owned) if (o) land++;
        int mins = (int) (w.time / 60);
        String info = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date())
                + "  ·  Spielzeit " + (mins / 60) + ":" + String.format(Locale.GERMANY, "%02d", mins % 60) + " h"
                + "  ·  " + (w.buildings.size() - 1) + " Gebäude  ·  Land " + land;
        prefs.edit().putString("slot" + i, info).apply();
    }

    @Override
    public boolean loadSlot(int i) {
        World w = read(slotFile(i));
        if (w == null) return false;
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
    public void volumes(int fx, int amb) {
        sound.setVolumes(fx, amb);
        prefs.edit().putInt("fx", fx).putInt("amb", amb).apply();
    }

    @Override
    public void frame(World w, Renderer r, float dt, boolean paused) {
        sound.frame(w, r, dt, paused);
    }
}
