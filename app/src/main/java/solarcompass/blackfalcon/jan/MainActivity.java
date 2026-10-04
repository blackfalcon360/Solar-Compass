package solarcompass.blackfalcon.jan;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Earth among the planets: a top-down map of the solar system, distances, galactic coordinates,
 * equinox and solstice dates, and a compass mode so you can tell where north is. Works offline.
 */
public class MainActivity extends Activity
        implements HeadingProvider.Listener, LocationHelper.Callback {

    private static final int REQ_LOCATION = 1;
    private static final int SLIDER_MAX = 360;      // +-180 days
    private static final int SLIDER_CENTER = 180;

    // spinner position -> solar index (-1 none, -2 Sun, 0 Mercury, 1 Venus, 3 Mars ... 7 Neptune)
    private static final int[] SPINNER_TO_SOLAR = {-1, -2, 0, 1, 3, 4, 5, 6, 7};
    private static final String[] SPINNER_NAMES = {
            "All planets", "Sun", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"};
    // list rows: Sun, Mercury, Venus, Mars ... Neptune  (body index in Astronomy.NAMES)
    private static final int[] ROW_BODY = {0, 2, 3, 4, 5, 6, 7, 8};
    // body index -> solar index (for heliocentric positions)
    private static final int[] BODY_TO_SOLAR = {-2, -1, 0, 1, 3, 4, 5, 6, 7};
    private static final String[] SEASONS = {"March equinox", "June solstice", "September equinox", "December solstice"};
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private SolarView solarView;
    private TextView timeLabel;
    private TextView detailView;
    private TextView earthView;
    private TextView seasonsView;
    private Spinner spinner;
    private Button compassButton;
    private SeekBar seek;
    private final TextView[] rowTexts = new TextView[ROW_BODY.length];
    private final Bitmap[] icons = new Bitmap[10];

    private HeadingProvider provider;
    private LocationHelper location;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private boolean hasLoc;
    private double lat;
    private double lon;
    private float declination;
    private float trueHeading;
    private boolean compassOn = true;
    private int selectedSolar = -1;

    // cached for the compass (redrawn on every compass reading)
    private double[][] helio;
    private double sunAz;
    private double[] galacticCentre;
    private String topLeft = "";

    // time travel
    private boolean live = true;
    private long scrubBase;
    private int sliderDays;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM yyyy  HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int widthPx = getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < icons.length; i++) {
            icons[i] = PlanetIcons.render(i, 128, 0.5, true);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        solarView = new SolarView(this);
        solarView.setIcons(icons);
        solarView.setOnClickListener(v -> {
            if (!hasLoc) requestLocation();
        });
        root.addView(solarView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, widthPx));

        // ---- planet drop-down + compass switch ----
        LinearLayout pickRow = new LinearLayout(this);
        pickRow.setOrientation(LinearLayout.HORIZONTAL);
        pickRow.setGravity(Gravity.CENTER_VERTICAL);
        pickRow.setPadding(dp(8), dp(2), dp(8), 0);

        spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, SPINNER_NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Color.WHITE);
                return t;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(Color.WHITE);
                t.setBackgroundColor(0xFF1B1F27);
                return t;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSolar = SPINNER_TO_SOLAR[position];
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        pickRow.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        compassButton = smallButton("Compass: ON", v -> {
            compassOn = !compassOn;
            compassButton.setText(compassOn ? "Compass: ON" : "Compass: OFF");
            refresh();
        });
        pickRow.addView(compassButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(pickRow);

        // ---- time ----
        timeLabel = new TextView(this);
        timeLabel.setTextColor(Color.WHITE);
        timeLabel.setTextSize(14);
        timeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        timeLabel.setGravity(Gravity.CENTER);
        root.addView(timeLabel);

        seek = new SeekBar(this);
        seek.setMax(SLIDER_MAX);
        seek.setProgress(SLIDER_CENTER);
        seek.setPadding(dp(20), dp(6), dp(20), dp(6));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                enterScrub();
                sliderDays = progress - SLIDER_CENTER;
                refresh();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buttonRow(
                timeButton("\u2212 1 d", Calendar.DAY_OF_YEAR, -1), timeButton("+ 1 d", Calendar.DAY_OF_YEAR, 1),
                timeButton("\u2212 1 w", Calendar.WEEK_OF_YEAR, -1), timeButton("+ 1 w", Calendar.WEEK_OF_YEAR, 1)));
        root.addView(buttonRow(
                timeButton("\u2212 1 mo", Calendar.MONTH, -1), timeButton("+ 1 mo", Calendar.MONTH, 1),
                timeButton("\u2212 1 y", Calendar.YEAR, -1), timeButton("+ 1 y", Calendar.YEAR, 1),
                smallButton("Now", v -> {
                    live = true;
                    sliderDays = 0;
                    seek.setProgress(SLIDER_CENTER);
                    refresh();
                })));

        detailView = new TextView(this);
        detailView.setTextColor(0xFFFFE08A);
        detailView.setTextSize(13);
        detailView.setPadding(dp(14), dp(4), dp(14), dp(4));
        root.addView(detailView);

        // ---- information list ----
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        earthView = addInfoRow(list, 9);
        for (int i = 0; i < ROW_BODY.length; i++) {
            rowTexts[i] = addInfoRow(list, ROW_BODY[i]);
            final int pos = i + 1; // spinner: 1 = Sun, 2 = Mercury ...
            ((View) rowTexts[i].getParent()).setOnClickListener(v -> spinner.setSelection(pos));
        }
        seasonsView = new TextView(this);
        seasonsView.setTextColor(Color.WHITE);
        seasonsView.setTextSize(12);
        seasonsView.setPadding(dp(14), dp(10), dp(14), dp(16));
        list.addView(seasonsView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        provider = new HeadingProvider(this, this, 0.15);
        location = new LocationHelper(this, this);
        if (location.hasSaved) onLocation(location.lat, location.lon); // works offline from the last position
        requestLocation();
        refresh();
    }

    private TextView addInfoRow(LinearLayout list, int iconIndex) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(icons[iconIndex]);
        row.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(row);
        return tv;
    }

    // ------------------------------------------------------------------ location + sensors

    private void requestLocation() {
        if (location.hasPermission()) {
            location.start();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            location.start();
            refresh();
        }
    }

    @Override
    public void onLocation(double la, double lo) {
        hasLoc = true;
        lat = la;
        lon = lo;
        declination = new GeomagneticField((float) la, (float) lo, 0f,
                System.currentTimeMillis()).getDeclination();
        refresh();
    }

    @Override
    public void onHeading(float magneticDegrees) {
        trueHeading = (magneticDegrees + declination + 360f) % 360f;
        pushView();
    }

    private void pushView() {
        solarView.setData(helio, selectedSolar, compassOn && hasLoc, trueHeading, sunAz,
                compassOn && hasLoc ? galacticCentre : null, null, topLeft);
    }

    // ------------------------------------------------------------------ time

    private void enterScrub() {
        if (live) {
            live = false;
            scrubBase = System.currentTimeMillis();
        }
    }

    private void shift(int field, int amount) {
        enterScrub();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(scrubBase);
        c.add(field, amount);
        scrubBase = c.getTimeInMillis();
        refresh();
    }

    private long shownMillis() {
        return live ? System.currentTimeMillis() : scrubBase + sliderDays * 86400000L;
    }

    private String offsetText(long t) {
        long diff = t - System.currentTimeMillis();
        long abs = Math.abs(diff);
        if (abs < 3600000L) return "now";
        long days = abs / 86400000L;
        String sign = diff > 0 ? "+" : "\u2212";
        if (days >= 365) return sign + String.format(Locale.US, "%.1f years", days / 365.25);
        return sign + days + " d " + (abs % 86400000L) / 3600000L + " h";
    }

    // ------------------------------------------------------------------ the numbers

    private static String kmText(double km) {
        return km >= 1e7 ? String.format(Locale.US, "%.1f million km", km / 1e6)
                : String.format(Locale.US, "%,d km", Math.round(km));
    }

    private static String lightText(double km) {
        double sec = km / Astronomy.LIGHT_KM_PER_S;
        if (sec < 90) return String.format(Locale.US, "%.1f light-seconds", sec);
        if (sec < 7200) return String.format(Locale.US, "%.1f light-minutes", sec / 60.0);
        return String.format(Locale.US, "%.1f light-hours", sec / 3600.0);
    }

    private static String distText(double au) {
        double km = au * Astronomy.AU_KM;
        return String.format(Locale.US, "%.3f AU = %s  (%s)", au, kmText(km), lightText(km));
    }

    private static String signed(double v) {
        return String.format(Locale.US, "%+.1f", v);
    }

    private static CharSequence titled(String title, String body) {
        SpannableStringBuilder out = new SpannableStringBuilder(title + "\n" + body);
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(1.25f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    private static double length3(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private void refresh() {
        long t = shownMillis();
        String when = dateFormat.format(new Date(t)) + "   \u2022  " + (live ? "LIVE" : offsetText(t));
        int year = Astronomy.utcYear(t);
        if (year < 1900 || year > 2100) when += "   (outside 1900\u20132100: less accurate)";
        timeLabel.setText(when);

        helio = new double[8][];
        for (int i = 0; i < 8; i++) helio[i] = Astronomy.heliocentric(i, t);
        Astronomy.Body[] bodies = Astronomy.compute(t, hasLoc ? lat : 0.0, hasLoc ? lon : 0.0);
        sunAz = bodies[0].azimuth;
        galacticCentre = hasLoc ? Astronomy.galacticCentreAltAz(t, lat, lon) : null;

        // ----- Earth -----
        double sunDist = length3(helio[2]);
        double earthLon = (Math.toDegrees(Math.atan2(helio[2][1], helio[2][0])) + 360.0) % 360.0;
        StringBuilder e = new StringBuilder();
        e.append("3rd planet from the Sun\n");
        e.append("Distance from the Sun: ").append(distText(sunDist)).append('\n');
        e.append(String.format(Locale.US, "Heliocentric longitude: %.1f\u00B0   \u2022   ", earthLon));
        e.append(seasonName(t)).append('\n');
        e.append("Galactic position: the Sun is about 26,000 light-years from the Galactic Centre");
        if (galacticCentre != null) {
            e.append(String.format(Locale.US, "\nGalactic Centre now: Az %d\u00B0 %s, Alt %+d\u00B0%s",
                    Math.round(galacticCentre[0]) % 360, DIRS[((int) ((galacticCentre[0] + 22.5) / 45.0)) % 8],
                    Math.round(galacticCentre[1]), galacticCentre[1] >= 0 ? "" : " (below horizon)"));
        }
        earthView.setText(titled("Earth \u2014 you are here", e.toString()));

        // ----- Sun and the planets -----
        for (int i = 0; i < ROW_BODY.length; i++) {
            Astronomy.Body b = bodies[ROW_BODY[i]];
            StringBuilder sb = new StringBuilder();
            sb.append("Distance from Earth: ").append(distText(b.distanceAu())).append('\n');
            if (i > 0) {
                int solar = BODY_TO_SOLAR[ROW_BODY[i]];
                sb.append("Distance from Sun: ").append(distText(length3(helio[solar]))).append('\n');
            }
            sb.append("Galactic coordinates:  l ").append(String.format(Locale.US, "%.1f", b.galL))
                    .append("\u00B0   b ").append(signed(b.galB)).append("\u00B0");
            rowTexts[i].setText(titled(b.name, sb.toString()));
        }

        // ----- selected object -----
        if (selectedSolar == -1) {
            detailView.setText("Pick a planet above to draw a line from Earth to it with the distance.");
        } else {
            int bodyIdx = selectedSolar == -2 ? 0 : selectedSolar < 2 ? selectedSolar + 2 : selectedSolar + 1;
            Astronomy.Body b = bodies[bodyIdx];
            double fromSun = selectedSolar == -2 ? 0.0 : length3(helio[selectedSolar]);
            detailView.setText(b.name + ":  " + distText(b.distanceAu()) + " from Earth"
                    + (selectedSolar == -2 ? "" : "\n" + String.format(Locale.US, "%.3f AU from the Sun", fromSun))
                    + String.format(Locale.US, "\nGalactic  l %.1f\u00B0  b %s\u00B0", b.galL, signed(b.galB)));
        }

        // ----- equinoxes and solstices -----
        seasonsView.setText(seasonsText(t, year));

        // ----- top-left note on the map -----
        StringBuilder tl = new StringBuilder();
        if (!compassOn) {
            tl.append("Fixed map (compass off)");
        } else if (hasLoc) {
            tl.append("Compass mode: N/E/S/W turn with your phone\n")
                    .append(LocationHelper.dms(lat, true)).append("  ").append(LocationHelper.dms(lon, false))
                    .append('\n').append(location.statusLine());
        } else {
            tl.append("Compass mode needs GPS \u2014 ").append(location.statusLine()).append("\n(tap the map to allow location)");
        }
        topLeft = tl.toString();
        pushView();
    }

    private String seasonName(long t) {
        long[] ss = Astronomy.seasons(Astronomy.utcYear(t));
        if (t < ss[0] || t >= ss[3]) return "Northern winter";
        if (t < ss[1]) return "Northern spring";
        if (t < ss[2]) return "Northern summer";
        return "Northern autumn";
    }

    private String seasonsText(long t, int year) {
        long[] ss = Astronomy.seasons(year);
        long next = -1;
        int nextIdx = -1;
        for (int k = 0; k < 4; k++) {
            if (ss[k] > t) {
                next = ss[k];
                nextIdx = k;
                break;
            }
        }
        long[] following = null;
        if (next < 0) {
            following = Astronomy.seasons(year + 1);
            next = following[0];
            nextIdx = 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Equinoxes & solstices ").append(year).append("  (your time zone)\n");
        for (int k = 0; k < 4; k++) {
            sb.append(k == nextIdx && following == null ? "\u25B6 " : "\u2022 ")
                    .append(String.format(Locale.US, "%-18s", SEASONS[k]))
                    .append(dateFormat.format(new Date(ss[k])));
            if (k == nextIdx && following == null) {
                sb.append("   \u2190 next, in ").append(Math.max(0, Math.round((next - t) / 86400000.0))).append(" days");
            }
            sb.append('\n');
        }
        if (following != null) {
            sb.append("\u25B6 Next: ").append(SEASONS[0]).append(' ').append(year + 1).append("  ")
                    .append(dateFormat.format(new Date(next)))
                    .append("   (in ").append(Math.max(0, Math.round((next - t) / 86400000.0))).append(" days)\n");
        }
        sb.append("Equinox: day and night are about equal. Solstice: the longest or shortest day.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ life cycle

    @Override
    protected void onResume() {
        super.onResume();
        provider.start(SensorManager.SENSOR_DELAY_GAME);
        if (location.hasPermission()) location.start();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        provider.stop();
        location.stop();
        handler.removeCallbacks(ticker);
    }

    // ------------------------------------------------------------------ small UI helpers

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setOnClickListener(l);
        return b;
    }

    private Button timeButton(String text, final int field, final int amount) {
        return smallButton(text, v -> shift(field, amount));
    }

    private LinearLayout buttonRow(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        for (Button b : buttons) {
            row.addView(b, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
