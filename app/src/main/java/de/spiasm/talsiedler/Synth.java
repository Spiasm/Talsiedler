package de.spiasm.talsiedler;

import java.util.Random;

/** Erzeugt alle Töne rechnerisch (keine Tondateien nötig). 22050 Hz, mono, 16 Bit. */
public final class Synth {
    public static final int RATE = 22050;

    // gleiche Nummern wie die Ereignisse in World
    public static final int S_CHOP = 1, S_CLINK = 2, S_DIG = 3, S_HAMMER = 4, S_SAW = 5, S_TREEFALL = 6, S_DONE = 7,
            S_LAND = 8, S_DROP = 9, S_PAVE = 10, S_CLICK = 11, S_PLACE = 12, S_ERROR = 13,
            S_BIRD1 = 14, S_BIRD2 = 15, S_BIRD3 = 16, S_WIND = 17, S_WATER = 18, S_MSG = 19,
            S_SPLASH = 20, S_BOW = 21, S_SWISH = 22, S_OINK = 23, S_SOB = 24, S_CLASH = 25, S_DIE = 26, S_HORN = 27, S_FIRE = 28, COUNT = 29;

    private Synth() {
    }

    private static float[] buf(float sec) {
        return new float[(int) (sec * RATE)];
    }

    /** einfacher Tiefpass (in place) */
    private static void lowpass(float[] a, float k) {
        float y = 0;
        for (int i = 0; i < a.length; i++) {
            y += (a[i] - y) * k;
            a[i] = y;
        }
    }

    private static void highpass(float[] a, float k) {
        float y = 0, prev = 0;
        for (int i = 0; i < a.length; i++) {
            float lp = y + (a[i] - y) * k;
            y = lp;
            float hp = a[i] - lp;
            a[i] = hp;
            prev = hp;
        }
    }

    private static float env(float t, float attack, float decay) {
        if (t < attack) return t / attack;
        return (float) Math.exp(-(t - attack) / decay);
    }

    private static void add(float[] out, float[] src, int at, float g) {
        for (int i = 0; i < src.length && at + i < out.length; i++) if (at + i >= 0) out[at + i] += src[i] * g;
    }

    private static float[] noise(float sec, Random r) {
        float[] a = buf(sec);
        for (int i = 0; i < a.length; i++) a[i] = r.nextFloat() * 2 - 1;
        return a;
    }

    public static short[] make(int id) {
        Random r = new Random(id * 977L);
        float[] a;
        switch (id) {
            case S_CHOP: { // Axt in Holz: dumpfer Schlag + kurzes Knacken
                a = buf(0.22f);
                float[] n = noise(0.22f, r);
                lowpass(n, 0.25f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = n[i] * env(t, 0.002f, 0.035f) * 0.9f
                            + (float) Math.sin(2 * Math.PI * (150 - 200 * t) * t) * env(t, 0.001f, 0.05f) * 0.8f;
                }
                break;
            }
            case S_CLINK: { // Spitzhacke auf Stein: helles Klingen
                a = buf(0.35f);
                float[] n = noise(0.35f, r);
                highpass(n, 0.3f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = (float) (Math.sin(2 * Math.PI * 2350 * t) * 0.35 + Math.sin(2 * Math.PI * 3420 * t) * 0.25
                            + Math.sin(2 * Math.PI * 1210 * t) * 0.2) * env(t, 0.001f, 0.06f)
                            + n[i] * env(t, 0.001f, 0.012f) * 0.8f;
                }
                break;
            }
            case S_DIG: { // Spaten in Erde: Rascheln
                a = buf(0.35f);
                float[] n = noise(0.35f, r);
                lowpass(n, 0.12f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / 0.35f / RATE;
                    a[i] = n[i] * (float) Math.sin(Math.PI * t) * 1.6f;
                }
                break;
            }
            case S_HAMMER: { // Hammer auf Holz
                a = buf(0.16f);
                float[] n = noise(0.16f, r);
                lowpass(n, 0.5f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = (float) Math.sin(2 * Math.PI * 820 * t) * env(t, 0.001f, 0.02f) * 0.6f
                            + (float) Math.sin(2 * Math.PI * 210 * t) * env(t, 0.001f, 0.04f) * 0.5f
                            + n[i] * env(t, 0.001f, 0.01f) * 0.6f;
                }
                break;
            }
            case S_SAW: { // Säge: rauschen mit Hin- und Herbewegung
                a = buf(1.9f);
                float[] n = noise(1.9f, r);
                highpass(n, 0.15f);
                float ph = 0;
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    float stroke = (float) Math.abs(Math.sin(Math.PI * 2.2f * t));
                    float f = 95 + 40 * stroke;
                    ph += f / RATE;
                    float saw = (ph % 1f) * 2 - 1;
                    float fade = Math.min(1f, Math.min(t / 0.05f, (1.9f - t) / 0.15f));
                    a[i] = (n[i] * 0.55f + saw * 0.35f) * (0.25f + 0.75f * stroke) * fade;
                }
                break;
            }
            case S_TREEFALL: { // Knarzen, dann Krachen und Aufschlag
                a = buf(1.6f);
                float ph = 0;
                for (int i = 0; i < (int) (0.7f * RATE); i++) {
                    float t = i / (float) RATE;
                    float f = 70 + 90 * t + 18 * (float) Math.sin(t * 60);
                    ph += f / RATE;
                    float sq = (ph % 1f) < 0.12f ? 1 : -0.1f;
                    a[i] += sq * 0.35f * env(t, 0.1f, 0.4f);
                }
                float[] n = noise(0.9f, r);
                lowpass(n, 0.18f);
                int at = (int) (0.65f * RATE);
                for (int i = 0; i < n.length && at + i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[at + i] += n[i] * env(t, 0.005f, 0.22f) * 1.3f
                            + (float) Math.sin(2 * Math.PI * (60 - 20 * t) * t) * env(t, 0.003f, 0.18f) * 0.9f;
                }
                break;
            }
            case S_DONE: { // kleine Fanfare: aufsteigender Dreiklang
                a = buf(1.3f);
                float[] fr = {523.25f, 659.25f, 783.99f, 1046.5f};
                for (int k = 0; k < fr.length; k++) {
                    int at = (int) (k * 0.12f * RATE);
                    for (int i = 0; at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        float e = env(t, 0.01f, k == 3 ? 0.45f : 0.22f);
                        a[at + i] += (float) (Math.sin(2 * Math.PI * fr[k] * t) * 0.5 + Math.sin(4 * Math.PI * fr[k] * t) * 0.15
                                + Math.sin(6 * Math.PI * fr[k] * t) * 0.08) * e * 0.5f;
                    }
                }
                break;
            }
            case S_LAND: { // Horn
                a = buf(1.6f);
                float[] fr = {392f, 523.25f};
                float[] st = {0f, 0.45f};
                float[] len = {0.42f, 1.1f};
                for (int k = 0; k < 2; k++) {
                    int at = (int) (st[k] * RATE);
                    float ph = 0;
                    for (int i = 0; i < (int) (len[k] * RATE) && at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        float f = fr[k] * (1 + 0.006f * (float) Math.sin(t * 30));
                        ph += f / RATE;
                        float saw = (ph % 1f) * 2 - 1;
                        float e = Math.min(1f, t / 0.05f) * Math.min(1f, (len[k] - t) / 0.12f);
                        a[at + i] += saw * e * 0.32f;
                    }
                }
                lowpass(a, 0.18f);
                for (int i = 0; i < a.length; i++) a[i] *= 1.8f;
                break;
            }
            case S_DROP: { // Ware abgelegt
                a = buf(0.09f);
                float[] n = noise(0.09f, r);
                lowpass(n, 0.2f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = n[i] * env(t, 0.002f, 0.02f) * 0.8f + (float) Math.sin(2 * Math.PI * 140 * t) * env(t, 0.001f, 0.025f) * 0.4f;
                }
                break;
            }
            case S_PAVE: { // Steine klackern
                a = buf(0.6f);
                for (int k = 0; k < 6; k++) {
                    int at = (int) ((k * 0.08f + r.nextFloat() * 0.03f) * RATE);
                    float f = 1400 + r.nextFloat() * 1200;
                    for (int i = 0; i < 0.08f * RATE && at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        a[at + i] += (float) Math.sin(2 * Math.PI * f * t) * env(t, 0.001f, 0.015f) * 0.45f;
                    }
                }
                break;
            }
            case S_CLICK: {
                a = buf(0.05f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = (float) Math.sin(2 * Math.PI * 1500 * t) * env(t, 0.001f, 0.008f) * 0.5f;
                }
                break;
            }
            case S_PLACE: { // Holzklopfen: tock-tock
                a = buf(0.3f);
                for (int k = 0; k < 2; k++) {
                    int at = (int) (k * 0.11f * RATE);
                    for (int i = 0; at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        a[at + i] += (float) Math.sin(2 * Math.PI * (k == 0 ? 620 : 780) * t) * env(t, 0.001f, 0.03f) * 0.55f;
                    }
                }
                break;
            }
            case S_ERROR: {
                a = buf(0.35f);
                for (int k = 0; k < 2; k++) {
                    int at = (int) (k * 0.16f * RATE);
                    for (int i = 0; i < 0.13f * RATE && at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        float sq = Math.sin(2 * Math.PI * 150 * t) > 0 ? 1 : -1;
                        a[at + i] += sq * 0.22f * Math.min(1f, (0.13f - t) / 0.02f);
                    }
                }
                lowpass(a, 0.3f);
                break;
            }
            case S_BIRD1:
            case S_BIRD2:
            case S_BIRD3: { // Vogelgezwitscher: mehrere kurze Pfiffe
                int k = id - S_BIRD1;
                a = buf(0.9f + k * 0.2f);
                int notes = 3 + k * 2;
                for (int j = 0; j < notes; j++) {
                    float start = j * (0.13f + k * 0.02f) + r.nextFloat() * 0.03f;
                    float len = 0.06f + r.nextFloat() * 0.05f;
                    float f0 = 2800 + r.nextFloat() * 1600 + k * 300, f1 = f0 + (r.nextBoolean() ? 1200 : -900);
                    int at = (int) (start * RATE);
                    float ph = 0;
                    for (int i = 0; i < len * RATE && at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        float f = f0 + (f1 - f0) * (t / len);
                        ph += f / RATE;
                        a[at + i] += (float) Math.sin(2 * Math.PI * ph) * (float) Math.sin(Math.PI * t / len) * 0.35f;
                    }
                }
                break;
            }
            case S_WIND: { // Wind, nahtlos wiederholbar
                a = buf(6f);
                float[] n = noise(6f, r);
                lowpass(n, 0.02f);
                lowpass(n, 0.05f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) a.length;
                    float sw = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * t * 2) * (float) Math.sin(2 * Math.PI * t * 3 + 1);
                    a[i] = n[i] * sw * 6f;
                }
                a = seamless(a);
                break;
            }
            case S_WATER: { // Wellen am Ufer
                a = buf(5f);
                float[] n = noise(5f, r);
                lowpass(n, 0.08f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) a.length;
                    float wave = (float) Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * t * 3), 2);
                    a[i] = n[i] * (0.25f + wave) * 2.2f;
                }
                a = seamless(a);
                break;
            }
            case S_MSG: { // zarter Hinweiston
                a = buf(0.5f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    float f = t < 0.12f ? 880 : 1174.66f;
                    a[i] = (float) Math.sin(2 * Math.PI * f * t) * env(t < 0.12f ? t : t - 0.12f, 0.005f, 0.12f) * 0.3f;
                }
                break;
            }
            case S_SPLASH: { // Angel ins Wasser: Platschen
                a = buf(0.45f);
                float[] n = noise(0.45f, r);
                lowpass(n, 0.3f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] = n[i] * env(t, 0.003f, 0.07f) * 1.1f
                            + (float) Math.sin(2 * Math.PI * (600 + 900 * t) * t) * env(t, 0.02f, 0.06f) * 0.25f;
                }
                break;
            }
            case S_BOW: { // Bogensehne
                a = buf(0.4f);
                float[] n = noise(0.4f, r);
                highpass(n, 0.2f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    float f = 190 * (1 + 0.04f * (float) Math.sin(t * 90));
                    a[i] = (float) Math.sin(2 * Math.PI * f * t) * env(t, 0.002f, 0.09f) * 0.7f
                            + n[i] * env(t, 0.01f, 0.08f) * 0.35f;
                }
                break;
            }
            case S_SWISH: { // Sense: Rauschen, das vorbeizieht
                a = buf(0.3f);
                float[] n = noise(0.3f, r);
                highpass(n, 0.35f);
                lowpass(n, 0.5f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (0.3f * RATE);
                    a[i] = n[i] * (float) Math.pow(Math.sin(Math.PI * t), 2) * 1.6f;
                }
                break;
            }
            case S_OINK: { // Schwein grunzt
                a = buf(0.5f);
                float ph = 0;
                for (int k = 0; k < 2; k++) {
                    int at = (int) (k * 0.22f * RATE);
                    for (int i = 0; i < 0.18f * RATE && at + i < a.length; i++) {
                        float t = i / (float) RATE;
                        float f = 110 + 40 * (float) Math.sin(t * 25);
                        ph += f / RATE;
                        float saw = (ph % 1f) * 2 - 1;
                        float am = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * 28 * t);
                        a[at + i] += saw * am * (float) Math.sin(Math.PI * t / 0.18f) * 0.5f;
                    }
                }
                lowpass(a, 0.25f);
                for (int i = 0; i < a.length; i++) a[i] *= 2f;
                break;
            }
            case S_SOB: { // Schluchzen: zwei Hickser, dann ein langes Huuuu
                a = buf(1.25f);
                float ph = 0;
                float[][] parts = {{0f, 0.11f, 560, 500}, {0.16f, 0.11f, 590, 520}, {0.34f, 0.85f, 620, 300}};
                for (float[] q : parts) {
                    int at = (int) (q[0] * RATE), len = (int) (q[1] * RATE);
                    for (int i = 0; i < len && at + i < a.length; i++) {
                        float t = i / (float) RATE, fr = t / q[1];
                        float f = q[2] + (q[3] - q[2]) * fr + 22 * (float) Math.sin(2 * Math.PI * 7 * t);
                        ph += f / RATE;
                        float v = (float) (Math.sin(2 * Math.PI * ph) + 0.55 * Math.sin(4 * Math.PI * ph) + 0.3 * Math.sin(6 * Math.PI * ph));
                        float e = (float) Math.pow(Math.sin(Math.PI * Math.min(1, fr * (fr < 0.2f ? 1 : 1))), 0.6);
                        a[at + i] += v * e * 0.35f;
                    }
                }
                float[] br = noise(1.25f, r);
                lowpass(br, 0.12f);
                for (int i = 0; i < a.length; i++) a[i] += br[i] * 0.25f * Math.min(1, Math.abs(a[i]) * 3);
                lowpass(a, 0.4f);
                break;
            }
            case S_CLASH: { // Schwerter: metallisches Klirren
                a = buf(0.45f);
                float[] fr = {1830, 2470, 3120, 4410};
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE, v = 0;
                    for (int k = 0; k < fr.length; k++) v += (float) Math.sin(2 * Math.PI * fr[k] * t + k) * (1f / (k + 1));
                    a[i] = v * (float) Math.exp(-t * 11) * 0.5f;
                }
                float[] nz = noise(0.03f, r);
                add(a, nz, 0, 0.8f);
                break;
            }
            case S_DIE: { // Aufstöhnen und Aufprall
                a = buf(0.8f);
                float ph = 0;
                for (int i = 0; i < 0.45f * RATE; i++) {
                    float t = i / (float) RATE, f = 220 - 120 * t / 0.45f;
                    ph += f / RATE;
                    float v = (float) (Math.sin(2 * Math.PI * ph) + 0.5 * Math.sin(4 * Math.PI * ph) + 0.3 * Math.sin(6 * Math.PI * ph));
                    a[i] += v * (float) Math.sin(Math.PI * t / 0.45f) * 0.35f;
                }
                float[] th = noise(0.18f, r);
                lowpass(th, 0.08f);
                for (int i = 0; i < th.length; i++) th[i] *= (float) Math.exp(-i / (float) RATE * 25) * 3f;
                add(a, th, (int) (0.5f * RATE), 1f);
                lowpass(a, 0.5f);
                break;
            }
            case S_HORN: { // tiefes Kriegshorn
                a = buf(1.4f);
                float ph = 0;
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    float f = t < 0.5f ? 146 : 174;
                    ph += f / RATE;
                    float saw = (ph % 1f) * 2 - 1;
                    float e = Math.min(1, t / 0.06f) * (t > 1.2f ? (1.4f - t) / 0.2f : 1) * (t > 0.46f && t < 0.52f ? 0.4f : 1);
                    a[i] = saw * e * 0.5f;
                }
                lowpass(a, 0.18f);
                lowpass(a, 0.3f);
                break;
            }
            case S_FIRE: { // Feuer prasselt
                a = noise(1.6f, r);
                lowpass(a, 0.3f);
                for (int i = 0; i < a.length; i++) {
                    float t = i / (float) RATE;
                    a[i] *= 0.25f * Math.min(1, t * 4) * Math.min(1, (1.6f - t) * 3);
                }
                for (int k = 0; k < 26; k++) {
                    int at = r.nextInt(a.length - 400);
                    for (int i = 0; i < 300; i++) a[at + i] += (r.nextFloat() * 2 - 1) * (float) Math.exp(-i / 40f) * 0.9f;
                }
                break;
            }
            default:
                a = buf(0.01f);
        }
        return toPcm(a);
    }

    /** Ende in den Anfang überblenden und abschneiden, damit die Schleife nicht knackt. */
    private static float[] seamless(float[] a) {
        int x = RATE / 4, len = a.length - x;
        for (int i = 0; i < x; i++) {
            float f = i / (float) x;
            a[i] = a[i] * f + a[len + i] * (1 - f);
        }
        return java.util.Arrays.copyOf(a, len);
    }

    private static short[] toPcm(float[] a) {
        float max = 0.001f;
        for (float v : a) max = Math.max(max, Math.abs(v));
        float g = max > 0.95f ? 0.95f / max : 1f;
        short[] o = new short[a.length];
        for (int i = 0; i < a.length; i++) o[i] = (short) Math.max(-32767, Math.min(32767, a[i] * g * 32767));
        return o;
    }

    /** WAV-Datei als Bytes. */
    public static byte[] wav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLen).put(new byte[]{'W', 'A', 'V', 'E'});
        bb.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
        bb.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLen);
        for (short s : pcm) bb.putShort(s);
        return b;
    }
}
