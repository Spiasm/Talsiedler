package de.spiasm.talsiedler;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {
    public final Renderer ren = new Renderer();
    public final Hud hud;
    private World world;
    private long last;
    private float clock, acc, saveT;
    private final Hud.Host host;
    public Session session;

    // Touch
    private float downX, downY, lastX, lastY, startDist, startZoom, midX, midY;
    private boolean moved, pinching, uiTouch;

    public GameView(Context ctx, Hud.Host host) {
        super(ctx);
        this.host = host;
        hud = new Hud(ren, host);
    }

    public void setWorld(World w) {
        world = w;
        hud.w = w;
        hud.reset();
        ren.selected = -1;
        ren.roadFrom = -1;
        if (ren.viewW > 0) ren.centerOn(w, w.hqB().node);
    }

    public World world() {
        return world;
    }

    /** Welt austauschen, ohne Ansicht und Bedienung zurückzusetzen (Neuabgleich zu zweit). */
    public void replaceWorld(World w) {
        world = w;
        hud.w = w;
    }

    public void centerOnMine() {
        if (world != null && ren.viewW > 0) ren.centerOn(world, world.hqB(hud.me).node);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        boolean first = ren.viewW == 0;
        ren.viewW = w;
        ren.viewH = h;
        if (first) {
            ren.zoom = Math.max(1.2f, h / 520f);
            if (world != null) ren.centerOn(world, world.hqB().node);
        }
        ren.clampCam();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : (now - last) / 1e9f;
        last = now;
        if (dt > 0.25f) dt = 0.25f;
        clock += dt;
        if (world != null) {
            int role = session == null ? Session.SOLO : session.role;
            if (role == Session.GUEST) {
                // der Gast rechnet nur, was der Gastgeber schickt
                if (session.w != null && session.w != world) replaceWorld(session.w);
            } else if (!hud.paused && !hud.intro) {
                acc += dt * hud.speed;
                int steps = 0;
                while (acc >= 0.05f && steps < 40) {
                    acc -= 0.05f;
                    steps++;
                }
                if (steps >= 40) acc = 0;
                if (role == Session.HOST) session.hostFrame(steps);
                else for (int i = 0; i < steps; i++) world.update(0.05f);
            } else if (role == Session.HOST) session.hostFrame(0);
            saveT += dt;
            if (saveT > 60f) {
                saveT = 0;
                host.save();
            }
            ren.draw(c, world, clock);
            hud.draw(c, clock);
            host.frame(world, ren, dt, hud.paused || hud.intro);
        }
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (world == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                moved = false;
                pinching = false;
                uiTouch = hud.overUi(downX, downY);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2 && !uiTouch) {
                    pinching = true;
                    moved = true;
                    startDist = spread(e);
                    startZoom = ren.zoom;
                    midX = (e.getX(0) + e.getX(1)) / 2;
                    midY = (e.getY(0) + e.getY(1)) / 2;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (uiTouch) break;
                if (pinching && e.getPointerCount() >= 2) {
                    float d = spread(e);
                    if (startDist > 10) {
                        float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                        float wx = midX / ren.zoom + ren.camX, wy = midY / ren.zoom + ren.camY;
                        ren.zoom = Math.max(0.6f, Math.min(4.5f, startZoom * d / startDist));
                        ren.camX = wx - mx / ren.zoom;
                        ren.camY = wy - my / ren.zoom;
                        midX = mx;
                        midY = my;
                        startDist = d;
                        startZoom = ren.zoom;
                        ren.clampCam();
                    }
                } else if (!pinching) {
                    float x = e.getX(), y = e.getY();
                    if (Math.abs(x - downX) + Math.abs(y - downY) > 18) moved = true;
                    if (moved) {
                        ren.camX -= (x - lastX) / ren.zoom;
                        ren.camY -= (y - lastY) / ren.zoom;
                        ren.clampCam();
                    }
                    lastX = x;
                    lastY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!moved && !pinching) hud.tap(e.getX(), e.getY());
                pinching = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                pinching = false;
                break;
        }
        return true;
    }

    private static float spread(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
