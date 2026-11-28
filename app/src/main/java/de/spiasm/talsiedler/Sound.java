package de.spiasm.talsiedler;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Random;

/** Spielt die erzeugten Töne ab: Arbeitsgeräusche an der richtigen Stelle, Umgebung, Knöpfe. */
public class Sound {
    private static final int VERSION = 4;
    private final SoundPool pool;
    private final int[] ids = new int[Synth.COUNT];
    private final HashSet<Integer> ready = new HashSet<>();
    private final float[] last = new float[Synth.COUNT];
    private final Random rng = new Random();
    private float fx = 0.7f, amb = 0.5f, clock, birdT = 3f, scanT;
    private int windStream, waterStream;
    private float waterAmount, treeAmount;
    private boolean paused;

    public Sound(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(aa).build();
        pool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) synchronized (ready) {
                ready.add(sampleId);
            }
        });
        final File dir = ctx.getCacheDir();
        new Thread(() -> {
            for (int i = 1; i < Synth.COUNT; i++) {
                try {
                    File f = new File(dir, "ton" + VERSION + "_" + i + ".wav");
                    if (!f.exists()) {
                        try (FileOutputStream out = new FileOutputStream(f)) {
                            out.write(Synth.wav(Synth.make(i)));
                        }
                    }
                    ids[i] = pool.load(f.getPath(), 1);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private boolean ok(int id) {
        if (ids[id] == 0) return false;
        synchronized (ready) {
            return ready.contains(ids[id]);
        }
    }

    public void setVolumes(int fxVol, int ambVol) {
        fx = fxVol / 10f;
        amb = ambVol / 10f;
    }

    private void play(int id, float vol, float rate) {
        if (!ok(id) || vol <= 0.01f || paused) return;
        if (clock - last[id] < 0.06f) return;
        last[id] = clock;
        vol = Math.min(1f, vol);
        pool.play(ids[id], vol, vol, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    /** Knopf- und Hinweistöne */
    public void ui(int id) {
        play(id, fx * (id == Synth.S_MSG ? 0.5f : 0.6f), 1f);
    }

    private static float gain(int id) {
        switch (id) {
            case Synth.S_CHOP: return 0.8f;
            case Synth.S_CLINK: return 0.45f;
            case Synth.S_DIG: return 0.5f;
            case Synth.S_HAMMER: return 0.55f;
            case Synth.S_SAW: return 0.45f;
            case Synth.S_TREEFALL: return 0.9f;
            case Synth.S_DROP: return 0.25f;
            case Synth.S_SPLASH: return 0.5f;
            case Synth.S_BOW: return 0.55f;
            case Synth.S_SWISH: return 0.45f;
            case Synth.S_OINK: return 0.4f;
            case Synth.S_SOB: return 0.6f;
            default: return 0.8f;
        }
    }

    /** Einmal pro Bild aufrufen. */
    public void frame(World w, Renderer r, float dt, boolean gamePaused) {
        clock += dt;
        java.util.ArrayList<int[]> ev = w.events;
        if (ev != null) {
            int played = 0;
            float zoomF = 0.4f + 0.6f * Math.min(1f, r.zoom / 2.2f);
            for (int[] e : ev) {
                if (gamePaused || played >= 5) break;
                int id = e[0], n = e[1];
                boolean global = id == Synth.S_DONE || id == Synth.S_LAND;
                float vol;
                if (global) vol = fx * 0.7f;
                else {
                    if (n < 0 || !w.explored[n]) continue;
                    float sx = (Renderer.nx(n) - r.camX) * r.zoom, sy = (Renderer.ny(w, n) - r.camY) * r.zoom;
                    float mx = r.viewW * 0.1f, my = r.viewH * 0.1f;
                    if (sx < -mx || sy < -my || sx > r.viewW + mx || sy > r.viewH + my) continue;
                    // zur Mitte hin etwas lauter
                    float dx = (sx - r.viewW / 2f) / r.viewW, dy = (sy - r.viewH / 2f) / r.viewH;
                    float center = 1f - Math.min(0.5f, (float) Math.sqrt(dx * dx + dy * dy) * 0.6f);
                    vol = fx * gain(id) * zoomF * center;
                }
                play(id, vol, 0.92f + rng.nextFloat() * 0.16f);
                played++;
            }
            ev.clear();
        }
        // Umgebung
        scanT -= dt;
        if (scanT <= 0) {
            scanT = 1f;
            scan(w, r);
        }
        if (windStream == 0 && ok(Synth.S_WIND)) windStream = pool.play(ids[Synth.S_WIND], 0, 0, 0, -1, 1f);
        if (waterStream == 0 && ok(Synth.S_WATER)) waterStream = pool.play(ids[Synth.S_WATER], 0, 0, 0, -1, 1f);
        float wv = paused ? 0 : amb * 0.28f;
        if (windStream != 0) pool.setVolume(windStream, wv, wv);
        float wa = paused ? 0 : amb * 0.55f * Math.min(1f, waterAmount * 3f);
        if (waterStream != 0) pool.setVolume(waterStream, wa, wa);
        birdT -= dt;
        if (birdT <= 0) {
            birdT = 2.5f + rng.nextFloat() * 6f;
            if (treeAmount > 0.04f && !gamePaused) {
                int b = Synth.S_BIRD1 + rng.nextInt(3);
                float v = amb * (0.25f + rng.nextFloat() * 0.35f) * Math.min(1f, treeAmount * 5f);
                if (ok(b) && v > 0.01f) {
                    float pan = rng.nextFloat();
                    pool.play(ids[b], v * (1 - pan * 0.7f), v * (0.3f + pan * 0.7f), 0, 0, 0.9f + rng.nextFloat() * 0.25f);
                }
            }
        }
    }

    private void scan(World w, Renderer r) {
        int water = 0, trees = 0, total = 0;
        for (int i = 0; i < 300; i++) {
            float sx = rng.nextFloat() * r.viewW, sy = rng.nextFloat() * r.viewH;
            float wx = sx / r.zoom + r.camX, wy = sy / r.zoom + r.camY;
            int y = (int) ((wy - Renderer.MY) / Renderer.RH), x = (int) ((wx - Renderer.MX) / Renderer.TW);
            if (x < 0 || y < 0 || x >= World.W || y >= World.H) continue;
            int n = y * World.W + x;
            total++;
            if (!w.explored[n]) continue;
            if (w.terrain[n] == World.T_WATER) water++;
            if (w.obj[n] == World.O_TREE) trees++;
        }
        waterAmount = total == 0 ? 0 : water / (float) total;
        treeAmount = total == 0 ? 0 : trees / (float) total;
    }

    public void pause() {
        paused = true;
        pool.autoPause();
    }

    public void resume() {
        paused = false;
        pool.autoResume();
    }

    public void release() {
        pool.release();
    }
}
