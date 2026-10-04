package solarcompass.blackfalcon.jan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * Top-down map of the solar system with Earth highlighted ("You are here").
 * In compass mode the map is turned so that the direction from Earth to the Sun on the map points
 * to where the Sun really is, and N / E / S / W letters turn around it, so north can be found.
 */
public class SolarView extends View {

    private static final int GOLD = 0xFFC9A227;
    private static final int VIOLET = 0xFFB388FF;
    private static final String[] NAMES = Astronomy.SOLAR_NAMES;       // Mercury Venus Earth Mars ...
    private static final int[] ICON = {2, 3, 9, 4, 5, 6, 7, 8};         // icon index per solar index
    private static final float[] ICON_DP = {20f, 24f, 32f, 22f, 34f, 40f, 28f, 28f};
    private static final double[] SEMI_MAJOR = {0.387, 0.723, 1.0, 1.524, 5.203, 9.555, 19.19, 30.07};
    private static final String[] SEASON_SHORT = {"Mar equinox", "Jun solstice", "Sep equinox", "Dec solstice"};
    private static final double[] SEASON_LON = {180.0, 270.0, 0.0, 90.0}; // Earth's heliocentric longitude then
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final float dp;
    private final float sp;
    private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint creditPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dest = new RectF();
    private final Path path = new Path();
    private final float[] xy = new float[2];

    private Bitmap[] icons;
    private double[][] helio;
    private int selected = -1;          // -1 none, -2 Sun, 0..7 solar index
    private boolean compass;
    private float heading;
    private double sunAz;
    private double[] galacticCentre;    // {azimuth, altitude} or null
    private String[] seasonLabels = new String[4];
    private String topLeft = "";

    public SolarView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;

        orbitPaint.setStyle(Paint.Style.STROKE);
        orbitPaint.setColor(0x44FFFFFF);
        orbitPaint.setStrokeWidth(1f * dp);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(11 * sp);
        labelPaint.setShadowLayer(3f * dp, 0f, 0f, Color.BLACK);

        smallPaint.setColor(0xFF9AA0A6);
        smallPaint.setTextSize(11 * sp);

        dirPaint.setTextAlign(Paint.Align.CENTER);
        dirPaint.setTypeface(Typeface.DEFAULT_BOLD);

        linePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);

        creditPaint.setColor(GOLD);
        creditPaint.setTextAlign(Paint.Align.RIGHT);
        creditPaint.setTypeface(Typeface.DEFAULT_BOLD);
        creditPaint.setTextSize(15 * sp);
    }

    public void setIcons(Bitmap[] icons) {
        this.icons = icons;
    }

    public void setData(double[][] helio, int selected, boolean compass, float heading, double sunAz,
                        double[] galacticCentre, String[] seasonLabels, String topLeft) {
        this.helio = helio;
        this.selected = selected;
        this.compass = compass;
        this.heading = heading;
        this.sunAz = sunAz;
        this.galacticCentre = galacticCentre;
        if (seasonLabels != null) this.seasonLabels = seasonLabels;
        this.topLeft = topLeft == null ? "" : topLeft;
        invalidate();
    }

    private static float mapRadius(double au, float rMax) {
        return (float) (rMax * Math.sqrt(au / 31.0));
    }

    /** Screen position of a point given its heliocentric ecliptic coordinates. */
    private void place(double x, double y, float cx, float cy, float rMax, float rot) {
        double lon = Math.toDegrees(Math.atan2(y, x));
        float r = mapRadius(Math.hypot(x, y), rMax);
        double th = Math.toRadians(90.0 - lon + rot);
        xy[0] = cx + r * (float) Math.sin(th);
        xy[1] = cy - r * (float) Math.cos(th);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(Color.BLACK);

        float cx = w / 2f;
        float cy = h / 2f;
        float rMax = Math.min(w, h) * 0.34f;

        // mandatory credit, top-right corner
        canvas.drawText("By: Black Falcon", w - 16 * dp, 16 * dp + creditPaint.getTextSize(), creditPaint);

        smallPaint.setTextAlign(Paint.Align.LEFT);
        float ty = 16 * dp + smallPaint.getTextSize();
        for (String line : topLeft.split("\n")) {
            canvas.drawText(line, 16 * dp, ty, smallPaint);
            ty += smallPaint.getTextSize() * 1.25f;
        }

        if (helio == null) return;

        // rotation of the whole map (compass mode): the Earth-to-Sun direction points to the real Sun
        double earthLon = Math.toDegrees(Math.atan2(helio[2][1], helio[2][0]));
        float rot = compass ? (float) ((sunAz - heading) + earthLon - 270.0) : 0f;

        // ----- compass ring, facing marker and the Galactic Centre -----
        if (compass) {
            float ringR = rMax + 24 * dp;
            for (int az = 0; az < 360; az += 10) {
                double th = Math.toRadians(az - heading);
                float s = (float) Math.sin(th);
                float c = (float) Math.cos(th);
                float len = (az % 30 == 0) ? 10 * dp : 5 * dp;
                linePaint.setColor(0xFFB0B6BD);
                linePaint.setStrokeWidth(1.5f * dp);
                canvas.drawLine(cx + (ringR - 12 * dp) * s, cy - (ringR - 12 * dp) * c,
                        cx + (ringR - 12 * dp + len) * s, cy - (ringR - 12 * dp + len) * c, linePaint);
            }
            for (int i = 0; i < 8; i++) {
                double th = Math.toRadians(i * 45 - heading);
                float s = (float) Math.sin(th);
                float c = (float) Math.cos(th);
                boolean cardinal = i % 2 == 0;
                dirPaint.setTextSize((cardinal ? 20 : 12) * sp);
                dirPaint.setColor(i == 0 ? 0xFFFF5252 : (cardinal ? Color.WHITE : 0xFF9AA0A6));
                float rr = ringR + 6 * dp;
                canvas.drawText(DIRS[i], cx + rr * s, cy - rr * c + dirPaint.getTextSize() * 0.35f, dirPaint);
            }
            float yTop = cy - ringR - 40 * dp;
            path.reset();
            path.moveTo(cx, yTop + 12 * dp);
            path.lineTo(cx - 7 * dp, yTop);
            path.lineTo(cx + 7 * dp, yTop);
            path.close();
            fillPaint.setColor(GOLD);
            canvas.drawPath(path, fillPaint);

            if (galacticCentre != null) { // where the centre of our galaxy is
                double th = Math.toRadians(galacticCentre[0] - heading);
                float rr = ringR + 34 * dp;
                float gx = cx + rr * (float) Math.sin(th);
                float gy = cy - rr * (float) Math.cos(th);
                boolean up = galacticCentre[1] >= 0;
                fillPaint.setColor(up ? VIOLET : 0x88B388FF);
                drawStar(canvas, gx, gy, 8 * dp);
                labelPaint.setColor(up ? VIOLET : 0xAA9A86C8);
                labelPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(up ? "Galactic Centre" : "Galactic Centre (below horizon)",
                        Math.max(70 * dp, Math.min(w - 70 * dp, gx)), gy + 20 * dp, labelPaint);
                labelPaint.setTextAlign(Paint.Align.LEFT);
                labelPaint.setColor(Color.WHITE);
            }
        }

        // ----- orbits -----
        for (int i = 0; i < 8; i++) {
            canvas.drawCircle(cx, cy, mapRadius(SEMI_MAJOR[i], rMax), orbitPaint);
        }

        // ----- vernal equinox direction (gamma) -----
        double th0 = Math.toRadians(90.0 + rot);
        float gx = cx + (rMax + 6 * dp) * (float) Math.sin(th0);
        float gy = cy - (rMax + 6 * dp) * (float) Math.cos(th0);
        linePaint.setColor(GOLD);
        linePaint.setStrokeWidth(1.2f * dp);
        linePaint.setPathEffect(new DashPathEffect(new float[]{8 * dp, 6 * dp}, 0f));
        canvas.drawLine(cx, cy, gx, gy, linePaint);
        linePaint.setPathEffect(null);
        labelPaint.setColor(GOLD);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("\u03B3 equinox direction", Math.max(60 * dp, Math.min(w - 60 * dp, gx)), gy - 6 * dp, labelPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(Color.WHITE);

        // ----- Sun -----
        drawIcon(canvas, 0, cx, cy, 38 * dp);
        labelPaint.setColor(0xFFFFD54F);
        canvas.drawText("Sun", cx + 22 * dp, cy + 26 * dp, labelPaint);
        labelPaint.setColor(Color.WHITE);

        // ----- season points on Earth's orbit -----
        for (int k = 0; k < 4; k++) {
            double lon = Math.toRadians(SEASON_LON[k]);
            place(Math.cos(lon), Math.sin(lon), cx, cy, rMax, rot);
            fillPaint.setColor(k % 2 == 0 ? 0xFF69F0AE : 0xFFFFAB40);
            canvas.drawCircle(xy[0], xy[1], 3 * dp, fillPaint);
        }

        // Earth -> Sun line (faint)
        place(helio[2][0], helio[2][1], cx, cy, rMax, rot);
        float ex = xy[0];
        float ey = xy[1];
        linePaint.setColor(0x55FFD54F);
        linePaint.setStrokeWidth(1.2f * dp);
        linePaint.setPathEffect(new DashPathEffect(new float[]{4 * dp, 4 * dp}, 0f));
        canvas.drawLine(ex, ey, cx, cy, linePaint);
        linePaint.setPathEffect(null);

        // ----- planets -----
        for (int i = 7; i >= 0; i--) {
            place(helio[i][0], helio[i][1], cx, cy, rMax, rot);
            float x = xy[0];
            float y = xy[1];
            float size = ICON_DP[i] * dp;
            drawIcon(canvas, ICON[i], x, y, size);
            if (i == 2) { // Earth: "You are here"
                linePaint.setColor(GOLD);
                linePaint.setStrokeWidth(2.5f * dp);
                canvas.drawCircle(x, y, size * 0.5f + 4 * dp, linePaint);
                labelPaint.setColor(GOLD);
                labelPaint.setFakeBoldText(true);
                canvas.drawText("Earth", x + size * 0.5f + 6 * dp, y - 2 * dp, labelPaint);
                labelPaint.setTextSize(10 * sp);
                canvas.drawText("you are here", x + size * 0.5f + 6 * dp, y + 11 * dp, labelPaint);
                labelPaint.setTextSize(11 * sp);
                labelPaint.setFakeBoldText(false);
                labelPaint.setColor(Color.WHITE);
            } else {
                labelPaint.setFakeBoldText(i == selected);
                canvas.drawText(NAMES[i], x + size * 0.5f + 3 * dp, y + 4 * dp, labelPaint);
                labelPaint.setFakeBoldText(false);
                if (i == selected) {
                    linePaint.setColor(GOLD);
                    linePaint.setStrokeWidth(2f * dp);
                    canvas.drawCircle(x, y, size * 0.5f + 3 * dp, linePaint);
                }
            }
        }

        // ----- line from Earth to the selected object, with the distance -----
        if (selected != -1 && selected != 2) {
            float tx;
            float ty2;
            double dx;
            double dy;
            double dz;
            if (selected == -2) {
                tx = cx;
                ty2 = cy;
                dx = helio[2][0];
                dy = helio[2][1];
                dz = helio[2][2];
            } else {
                place(helio[selected][0], helio[selected][1], cx, cy, rMax, rot);
                tx = xy[0];
                ty2 = xy[1];
                dx = helio[selected][0] - helio[2][0];
                dy = helio[selected][1] - helio[2][1];
                dz = helio[selected][2] - helio[2][2];
            }
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            linePaint.setColor(GOLD);
            linePaint.setStrokeWidth(1.8f * dp);
            canvas.drawLine(ex, ey, tx, ty2, linePaint);
            labelPaint.setColor(0xFFFFE08A);
            labelPaint.setFakeBoldText(true);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.format(Locale.US, "%.2f AU", dist), (ex + tx) / 2f, (ey + ty2) / 2f - 6 * dp, labelPaint);
            labelPaint.setTextAlign(Paint.Align.LEFT);
            labelPaint.setFakeBoldText(false);
            labelPaint.setColor(Color.WHITE);
        }

        // ----- legend -----
        smallPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("orbits not to scale (square-root spacing)  \u2022  green/orange dots = equinox/solstice",
                cx, h - 8 * dp, smallPaint);
        smallPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawIcon(Canvas canvas, int iconIndex, float x, float y, float size) {
        if (icons == null || iconIndex >= icons.length || icons[iconIndex] == null) return;
        dest.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
        canvas.drawBitmap(icons[iconIndex], null, dest, bitmapPaint);
    }

    /** Four-pointed star. */
    private void drawStar(Canvas canvas, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y - r);
        path.lineTo(x + r * 0.28f, y - r * 0.28f);
        path.lineTo(x + r, y);
        path.lineTo(x + r * 0.28f, y + r * 0.28f);
        path.lineTo(x, y + r);
        path.lineTo(x - r * 0.28f, y + r * 0.28f);
        path.lineTo(x - r, y);
        path.lineTo(x - r * 0.28f, y - r * 0.28f);
        path.close();
        canvas.drawPath(path, fillPaint);
    }
}
