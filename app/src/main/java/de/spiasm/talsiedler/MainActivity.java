package de.spiasm.talsiedler;

import android.app.Activity;
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

public class MainActivity extends Activity implements Hud.Host {
    private GameView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this, this);
        World w = load();
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
    }

    private File file() {
        return new File(getFilesDir(), "spielstand.dat");
    }

    private World load() {
        File f = file();
        if (!f.exists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            return (World) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void save() {
        World w = view.world();
        if (w == null) return;
        File tmp = new File(getFilesDir(), "spielstand.tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeObject(w);
        } catch (Exception e) {
            return;
        }
        tmp.renameTo(file());
    }

    @Override
    public void newGame() {
        view.setWorld(new World(System.currentTimeMillis()));
        save();
    }
}
