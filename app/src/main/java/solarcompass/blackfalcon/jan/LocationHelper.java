package solarcompass.blackfalcon.jan;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Position from the phone's GPS chip (no internet needed). The last position is saved,
 * so the app also works when GPS has no fix yet. The network provider is only a helper.
 */
public class LocationHelper implements LocationListener {

    public interface Callback {
        void onLocation(double lat, double lon);
    }

    private static final String PREFS = "location_prefs";
    private static final long GPS_FRESH_MS = 60000L;

    private final Context context;
    private final LocationManager locationManager;
    private final Callback callback;
    private final SharedPreferences prefs;

    public boolean hasSaved;
    public double lat;
    public double lon;
    private long savedTime;
    private long lastGpsFix = Long.MIN_VALUE / 2;
    private float accuracy = -1f;

    public LocationHelper(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.locationManager = context.getSystemService(LocationManager.class);
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        hasSaved = prefs.getBoolean("has", false);
        if (hasSaved) {
            lat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
            lon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
            savedTime = prefs.getLong("time", 0L);
        }
    }

    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFine() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLocationOn() {
        try {
            return locationManager != null
                    && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("MissingPermission")
    public void start() {
        if (locationManager == null || !hasPermission()) return;

        // 1) instant: the phone's last known position
        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER};
        for (String p : providers) {
            try {
                Location l = locationManager.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {
            }
        }
        if (best != null && (!hasSaved || best.getTime() > savedTime)) {
            accept(best.getLatitude(), best.getLongitude(), best.getTime());
        }

        // 2) live updates: GPS first (works offline), network only as a helper
        try {
            locationManager.removeUpdates(this);
            if (hasFine() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 0f, this);
            }
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        long now = SystemClock.elapsedRealtime();
        if (gps) {
            lastGpsFix = now;
        } else if (now - lastGpsFix < GPS_FRESH_MS) {
            return; // a recent GPS fix is better than a network guess
        }
        accuracy = l.hasAccuracy() ? l.getAccuracy() : -1f;
        accept(l.getLatitude(), l.getLongitude(), l.getTime());
    }

    private void accept(double la, double lo, long time) {
        lat = la;
        lon = lo;
        savedTime = time;
        hasSaved = true;
        prefs.edit()
                .putBoolean("has", true)
                .putLong("lat", Double.doubleToRawLongBits(la))
                .putLong("lon", Double.doubleToRawLongBits(lo))
                .putLong("time", time)
                .apply();
        callback.onLocation(la, lo);
    }

    public boolean isGpsFresh() {
        return SystemClock.elapsedRealtime() - lastGpsFix < GPS_FRESH_MS;
    }

    /** One line telling where the position comes from. */
    public String statusLine() {
        if (isGpsFresh()) {
            return accuracy > 0 ? String.format(Locale.US, "GPS \u00B1%d m", Math.round(accuracy)) : "GPS";
        }
        return hasSaved ? "Saved position (offline)" : "Searching for GPS\u2026";
    }

    /** Degrees, minutes, seconds, e.g. 33\u00B054\u203212.3\u2033 N */
    public static String dms(double value, boolean isLatitude) {
        String hemi = isLatitude ? (value >= 0 ? "N" : "S") : (value >= 0 ? "E" : "W");
        double v = Math.abs(value);
        int deg = (int) v;
        double minFull = (v - deg) * 60.0;
        int min = (int) minFull;
        double sec = (minFull - min) * 60.0;
        String secText = String.format(Locale.US, "%.1f", sec);
        if (secText.equals("60.0")) {
            secText = "0.0";
            min++;
            if (min == 60) {
                min = 0;
                deg++;
            }
        }
        return deg + "\u00B0" + min + "\u2032" + secText + "\u2033 " + hemi;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
