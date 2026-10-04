package solarcompass.blackfalcon.jan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Emoji-style planet pictures drawn in code (glossy discs with details). */
public final class PlanetIcons {

    public static final int SUN = 0, MOON = 1, MERCURY = 2, VENUS = 3, MARS = 4,
            JUPITER = 5, SATURN = 6, URANUS = 7, NEPTUNE = 8, EARTH = 9;

    private PlanetIcons() {
    }

    /** index: 0 Sun, 1 Moon, 2 Mercury, 3 Venus, 4 Mars, 5 Jupiter, 6 Saturn, 7 Uranus, 8 Neptune, 9 Earth. */
    public static Bitmap render(int index, int size, double moonFraction, boolean moonWaxing) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = size / 2f;
        float cy = size / 2f;
        float r = size * 0.36f;
        switch (index) {
            case SUN:
                sun(c, cx, cy, size);
                break;
            case MOON:
                moon(c, cx, cy, r, moonFraction, moonWaxing);
                break;
            case MERCURY:
                mercury(c, cx, cy, r * 0.9f);
                break;
            case VENUS:
                venus(c, cx, cy, r);
                break;
            case MARS:
                mars(c, cx, cy, r * 0.95f);
                break;
            case JUPITER:
                jupiter(c, cx, cy, r);
                break;
            case SATURN:
                saturn(c, cx, cy, size);
                break;
            case URANUS:
                uranus(c, cx, cy, size);
                break;
            case NEPTUNE:
                neptune(c, cx, cy, r * 0.95f);
                break;
            default:
                earth(c, cx, cy, r);
                break;
        }
        return bmp;
    }

    // ---------------------------------------------------------------- helpers

    private static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float width) {
        Paint p = paint(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        return p;
    }

    /** Light from the top-left: soft highlight, darker rim. */
    private static void shade(Canvas c, float cx, float cy, float r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
                new int[]{0x66FFFFFF, 0x00FFFFFF, 0x88000000}, new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    private static void clip(Canvas c, float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(p);
    }

    // ---------------------------------------------------------------- bodies

    private static void sun(Canvas c, float cx, float cy, int size) {
        float r = size * 0.27f;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(cx, cy, size * 0.5f,
                new int[]{0xAAFFD54F, 0x00FFD54F}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, size * 0.5f, glow);

        Paint ray = stroke(0xFFFFB300, size * 0.05f);
        ray.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30.0);
            float inner = r * 1.3f;
            float outer = r * (k % 2 == 0 ? 1.8f : 1.58f);
            c.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, ray);
        }
        c.drawCircle(cx, cy, r, paint(0xFFFFC107));
        c.drawCircle(cx - r * 0.1f, cy - r * 0.1f, r * 0.8f, paint(0xFFFFE066));
        shade(c, cx, cy, r);
    }

    private static void moon(Canvas c, float cx, float cy, float r, double frac, boolean waxing) {
        c.drawCircle(cx, cy, r, paint(0xFF2B2F3A)); // dark side
        if (frac > 0.02) {
            RectF circle = new RectF(cx - r, cy - r, cx + r, cy + r);
            float a = (float) (r * Math.abs(1.0 - 2.0 * frac));
            RectF term = new RectF(cx - a, cy - r, cx + a, cy + r);
            Path lit = new Path();
            lit.arcTo(circle, -90f, 180f, true);
            lit.arcTo(term, 90f, frac < 0.5 ? -180f : 180f, false);
            lit.close();
            c.save();
            if (!waxing) c.scale(-1f, 1f, cx, cy);
            c.drawPath(lit, paint(0xFFF3EFD9));
            c.save();
            c.clipPath(lit);
            c.drawCircle(cx - r * 0.3f, cy - r * 0.25f, r * 0.18f, paint(0x33999080));
            c.drawCircle(cx + r * 0.25f, cy + r * 0.2f, r * 0.24f, paint(0x33999080));
            c.drawCircle(cx + r * 0.1f, cy - r * 0.5f, r * 0.1f, paint(0x33999080));
            c.restore();
            c.restore();
        }
        shade(c, cx, cy, r);
        c.drawCircle(cx, cy, r, stroke(0x55FFFFFF, r * 0.04f));
    }

    private static void mercury(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, paint(0xFFB4B1AD));
        c.drawCircle(cx - r * 0.35f, cy - r * 0.15f, r * 0.2f, paint(0x55555555));
        c.drawCircle(cx + r * 0.3f, cy + r * 0.3f, r * 0.26f, paint(0x55555555));
        c.drawCircle(cx + r * 0.2f, cy - r * 0.45f, r * 0.12f, paint(0x55555555));
        c.drawCircle(cx - r * 0.2f, cy + r * 0.5f, r * 0.1f, paint(0x55555555));
        shade(c, cx, cy, r);
    }

    private static void venus(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, paint(0xFFF3D69B));
        c.save();
        clip(c, cx, cy, r);
        Paint swirl = stroke(0x66FFFFFF, r * 0.16f);
        c.drawArc(new RectF(cx - r * 1.1f, cy - r * 0.7f, cx + r * 0.9f, cy + r * 0.3f), 200f, 130f, false, swirl);
        c.drawArc(new RectF(cx - r * 0.8f, cy - r * 0.1f, cx + r * 1.2f, cy + r * 0.9f), 20f, 130f, false, swirl);
        c.drawArc(new RectF(cx - r * 0.7f, cy - r * 0.4f, cx + r * 0.7f, cy + r * 0.4f), 190f, 120f,
                false, stroke(0x55C98B2E, r * 0.1f));
        c.restore();
        shade(c, cx, cy, r);
    }

    private static void mars(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, paint(0xFFD9573A));
        c.save();
        clip(c, cx, cy, r);
        c.drawOval(new RectF(cx - r * 0.6f, cy - r * 0.1f, cx + r * 0.1f, cy + r * 0.35f), paint(0x66802A1A));
        c.drawOval(new RectF(cx + r * 0.1f, cy + r * 0.25f, cx + r * 0.7f, cy + r * 0.6f), paint(0x55802A1A));
        c.drawOval(new RectF(cx - r * 0.35f, cy - r * 1.05f, cx + r * 0.35f, cy - r * 0.7f), paint(0xDDFFFFFF));
        c.restore();
        shade(c, cx, cy, r);
    }

    private static void jupiter(Canvas c, float cx, float cy, float r) {
        c.save();
        clip(c, cx, cy, r);
        c.drawCircle(cx, cy, r, paint(0xFFE3B982));
        float[] off = {-0.62f, -0.34f, -0.06f, 0.22f, 0.5f, 0.74f};
        float[] th = {0.12f, 0.14f, 0.17f, 0.12f, 0.12f, 0.1f};
        int[] col = {0x88A0612F, 0x66FFE9C4, 0x88B8733A, 0x66F5DDB5, 0x88A0612F, 0x55FFE9C4};
        for (int i = 0; i < off.length; i++) {
            c.drawRect(cx - r, cy + off[i] * r - th[i] * r / 2f, cx + r, cy + off[i] * r + th[i] * r / 2f,
                    paint(col[i]));
        }
        c.drawOval(new RectF(cx + r * 0.1f, cy + r * 0.32f, cx + r * 0.5f, cy + r * 0.52f), paint(0xDDC0392B));
        c.restore();
        shade(c, cx, cy, r);
    }

    private static void saturn(Canvas c, float cx, float cy, int size) {
        float r = size * 0.23f;
        c.save();
        c.rotate(-20f, cx, cy);
        Paint ring = stroke(0xFFE6D3A3, r * 0.34f);
        RectF ringRect = new RectF(cx - r * 2.05f, cy - r * 0.62f, cx + r * 2.05f, cy + r * 0.62f);
        c.drawOval(ringRect, ring);                    // whole ring
        c.drawCircle(cx, cy, r, paint(0xFFEAD08A));    // planet hides the back of the ring
        c.save();
        clip(c, cx, cy, r);
        c.drawRect(cx - r, cy - r * 0.45f, cx + r, cy - r * 0.3f, paint(0x55B38F4A));
        c.drawRect(cx - r, cy + r * 0.05f, cx + r, cy + r * 0.25f, paint(0x55B38F4A));
        c.restore();
        shade(c, cx, cy, r);
        c.drawArc(ringRect, 0f, 180f, false, ring);    // front half of the ring over the planet
        c.restore();
    }

    private static void uranus(Canvas c, float cx, float cy, int size) {
        float r = size * 0.3f;
        c.save();
        c.rotate(75f, cx, cy);
        RectF ringRect = new RectF(cx - r * 1.55f, cy - r * 0.38f, cx + r * 1.55f, cy + r * 0.38f);
        Paint ring = stroke(0xAAE6FFFF, r * 0.09f);
        c.drawOval(ringRect, ring);
        c.drawCircle(cx, cy, r, paint(0xFF8FE3E8));
        shade(c, cx, cy, r);
        c.drawArc(ringRect, 0f, 180f, false, ring);
        c.restore();
    }

    private static void neptune(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, paint(0xFF3F5FD8));
        c.save();
        clip(c, cx, cy, r);
        c.drawRect(cx - r, cy - r * 0.35f, cx + r, cy - r * 0.15f, paint(0x553A8BFF));
        c.drawRect(cx - r, cy + r * 0.3f, cx + r, cy + r * 0.4f, paint(0x44A0C4FF));
        c.drawOval(new RectF(cx - r * 0.1f, cy + r * 0.0f, cx + r * 0.4f, cy + r * 0.22f), paint(0x77101A66));
        c.restore();
        shade(c, cx, cy, r);
    }

    private static void earth(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r * 1.12f, paint(0x3390CAF9)); // atmosphere glow
        c.drawCircle(cx, cy, r, paint(0xFF2E7DD1));
        c.save();
        clip(c, cx, cy, r);
        Paint land = paint(0xFF4CAF50);
        c.save();
        c.rotate(20f, cx - r * 0.25f, cy - r * 0.2f);
        c.drawOval(new RectF(cx - r * 0.6f, cy - r * 0.7f, cx + r * 0.1f, cy + r * 0.3f), land);
        c.restore();
        c.save();
        c.rotate(-25f, cx + r * 0.45f, cy + r * 0.25f);
        c.drawOval(new RectF(cx + r * 0.15f, cy - r * 0.15f, cx + r * 0.75f, cy + r * 0.65f), land);
        c.restore();
        c.drawOval(new RectF(cx - r * 0.4f, cy + r * 0.45f, cx + r * 0.2f, cy + r * 0.8f), land);
        c.drawOval(new RectF(cx - r * 0.4f, cy - r * 1.05f, cx + r * 0.4f, cy - r * 0.8f), paint(0xDDFFFFFF));
        Paint cloud = stroke(0x88FFFFFF, r * 0.1f);
        cloud.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(cx - r * 0.9f, cy - r * 0.5f, cx + r * 0.3f, cy + r * 0.2f), 200f, 100f, false, cloud);
        c.drawArc(new RectF(cx - r * 0.2f, cy + r * 0.1f, cx + r * 0.9f, cy + r * 0.8f), 20f, 90f, false, cloud);
        c.restore();
        shade(c, cx, cy, r);
    }
}
