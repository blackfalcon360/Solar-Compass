package solarcompass.blackfalcon.jan;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Offline sky maths: positions of the Sun, Moon and planets for any time and place.
 * Based on Paul Schlyter's low-precision method (about 1-2 arc-minutes; fine for a sky map,
 * valid for roughly the years 1900-2100). Azimuth is measured from TRUE north, clockwise.
 */
public final class Astronomy {

    public static final String[] NAMES = {
            "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"};

    /** Order used by the solar-system map. */
    public static final String[] SOLAR_NAMES = {
            "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"};

    public static final double AU_KM = 149597870.7;
    public static final double EARTH_RADIUS_KM = 6378.14;
    public static final double LIGHT_KM_PER_S = 299792.458;

    public static final class Body {
        public final String name;
        public final int index;
        public double ra;        // degrees (of date)
        public double dec;       // degrees (of date)
        public double azimuth;   // degrees from true north
        public double altitude;  // degrees above the horizon (negative = below)
        public double distance;  // AU (Moon: Earth radii)
        public double galL;      // galactic longitude as seen from Earth (degrees)
        public double galB;      // galactic latitude as seen from Earth (degrees)

        Body(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public double distanceKm() {
            return index == 1 ? distance * EARTH_RADIUS_KM : distance * AU_KM;
        }

        public double distanceAu() {
            return distanceKm() / AU_KM;
        }

        public double lightMinutes() {
            return distanceKm() / LIGHT_KM_PER_S / 60.0;
        }
    }

    // N, i, w, a, e, M : value and change per day (planets Mercury..Neptune)
    private static final double[][] EL = {
            {48.3313, 3.24587E-5, 7.0047, 5.00E-8, 29.1241, 1.01444E-5, 0.387098, 0, 0.205635, 5.59E-10, 168.6562, 4.0923344368},
            {76.6799, 2.46590E-5, 3.3946, 2.75E-8, 54.8910, 1.38374E-5, 0.723330, 0, 0.006773, -1.302E-9, 48.0052, 1.6021302244},
            {49.5574, 2.11081E-5, 1.8497, -1.78E-8, 286.5016, 2.92961E-5, 1.523688, 0, 0.093405, 2.516E-9, 18.6021, 0.5240207766},
            {100.4542, 2.76854E-5, 1.3030, -1.557E-7, 273.8777, 1.64505E-5, 5.20256, 0, 0.048498, 4.469E-9, 19.8950, 0.0830853001},
            {113.6634, 2.38980E-5, 2.4886, -1.081E-7, 339.3939, 2.97661E-5, 9.55475, 0, 0.055546, -9.499E-9, 316.9670, 0.0334442282},
            {74.0005, 1.3978E-5, 0.7733, 1.9E-8, 96.6612, 3.0565E-5, 19.18171, -1.55E-8, 0.047318, 7.45E-9, 142.5905, 0.011725806},
            {131.7806, 3.0173E-5, 1.7700, -2.55E-7, 272.8461, -6.027E-6, 30.05826, 3.313E-8, 0.008606, 2.15E-9, 260.2471, 0.005995147}
    };

    // Meeus, Astronomical Algorithms, table 27.C
    private static final double[] SA = {485, 203, 199, 182, 156, 136, 77, 74, 70, 58, 52, 50, 45, 44, 29, 18, 17, 16, 14, 12, 12, 12, 9, 8};
    private static final double[] SB = {324.96, 337.23, 342.08, 27.85, 73.14, 171.52, 222.54, 296.72, 243.58, 119.81, 297.17, 21.02,
            247.54, 325.15, 60.93, 155.12, 288.79, 198.04, 199.76, 95.39, 287.11, 320.81, 227.73, 15.45};
    private static final double[] SC = {1934.136, 32964.467, 20.186, 445267.112, 45036.886, 22518.443, 65928.934, 3034.906,
            9037.513, 33718.147, 150.678, 2281.226, 29929.562, 31555.956, 4443.417, 67555.328, 4562.452, 62894.029,
            31436.921, 14577.848, 31931.756, 34777.259, 1222.114, 16859.074};

    private Astronomy() {
    }

    // ------------------------------------------------------------------ helpers

    private static double rev(double x) {
        return x - 360.0 * Math.floor(x / 360.0);
    }

    private static double sind(double x) {
        return Math.sin(Math.toRadians(x));
    }

    private static double cosd(double x) {
        return Math.cos(Math.toRadians(x));
    }

    private static double jdOf(long utcMillis) {
        return utcMillis / 86400000.0 + 2440587.5;
    }

    private static double kepler(double m, double e) {
        double ea = m + Math.toDegrees(e * sind(m) * (1 + e * cosd(m)));
        for (int k = 0; k < 30; k++) {
            double e1 = ea - (ea - Math.toDegrees(e * sind(ea)) - m) / (1 - e * cosd(ea));
            boolean done = Math.abs(e1 - ea) < 1e-7;
            ea = e1;
            if (done) break;
        }
        return ea;
    }

    /** Sun: {ecliptic longitude, distance AU, mean anomaly, argument of perihelion}. */
    private static double[] sun(double d) {
        double w = 282.9404 + 4.70935E-5 * d;
        double e = 0.016709 - 1.151E-9 * d;
        double m = rev(356.0470 + 0.9856002585 * d);
        double ea = kepler(m, e);
        double xv = cosd(ea) - e;
        double yv = Math.sqrt(1 - e * e) * sind(ea);
        double v = Math.toDegrees(Math.atan2(yv, xv));
        double r = Math.hypot(xv, yv);
        return new double[]{rev(v + w), r, m, w};
    }

    /** Heliocentric ecliptic rectangular coordinates of a planet (0 = Mercury ... 6 = Neptune), perturbed. */
    private static double[] helioPlanet(int idx, double d) {
        double[] t = EL[idx];
        double n = t[0] + t[1] * d;
        double inc = t[2] + t[3] * d;
        double w = t[4] + t[5] * d;
        double a = t[6] + t[7] * d;
        double e = t[8] + t[9] * d;
        double m = rev(t[10] + t[11] * d);

        double ea = kepler(m, e);
        double xv = a * (cosd(ea) - e);
        double yv = a * Math.sqrt(1 - e * e) * sind(ea);
        double v = Math.toDegrees(Math.atan2(yv, xv));
        double r = Math.hypot(xv, yv);

        double xh = r * (cosd(n) * cosd(v + w) - sind(n) * sind(v + w) * cosd(inc));
        double yh = r * (sind(n) * cosd(v + w) + cosd(n) * sind(v + w) * cosd(inc));
        double zh = r * sind(v + w) * sind(inc);
        double lon = Math.toDegrees(Math.atan2(yh, xh));
        double lat = Math.toDegrees(Math.atan2(zh, Math.hypot(xh, yh)));

        // mutual perturbations of the big planets
        double mj = rev(19.8950 + 0.0830853001 * d);
        double ms = rev(316.9670 + 0.0334442282 * d);
        double mu = rev(142.5905 + 0.011725806 * d);
        if (idx == 3) { // Jupiter
            lon += -0.332 * sind(2 * mj - 5 * ms - 67.6) - 0.056 * sind(2 * mj - 2 * ms + 21)
                    + 0.042 * sind(3 * mj - 5 * ms + 21) - 0.036 * sind(mj - 2 * ms)
                    + 0.022 * cosd(mj - ms) + 0.023 * sind(2 * mj - 3 * ms + 52)
                    - 0.016 * sind(mj - 5 * ms - 69);
        } else if (idx == 4) { // Saturn
            lon += 0.812 * sind(2 * mj - 5 * ms - 67.6) - 0.229 * cosd(2 * mj - 4 * ms - 2)
                    + 0.119 * sind(mj - 2 * ms - 3) + 0.046 * sind(2 * mj - 6 * ms - 69)
                    + 0.014 * sind(mj - 3 * ms + 32);
            lat += -0.020 * cosd(2 * mj - 4 * ms - 2) + 0.018 * sind(2 * mj - 6 * ms - 49);
        } else if (idx == 5) { // Uranus
            lon += 0.040 * sind(ms - 2 * mu + 6) + 0.035 * sind(ms - 3 * mu + 33)
                    - 0.015 * sind(mj - mu + 20);
        }

        return new double[]{r * cosd(lon) * cosd(lat), r * sind(lon) * cosd(lat), r * sind(lat)};
    }

    /** Geocentric ecliptic rectangular coordinates of a planet. */
    private static double[] planet(int idx, double d) {
        double[] h = helioPlanet(idx, d);
        double[] s = sun(d);
        return new double[]{h[0] + s[1] * cosd(s[0]), h[1] + s[1] * sind(s[0]), h[2]};
    }

    /** Moon: geocentric ecliptic rectangular coordinates in Earth radii, plus distance. */
    private static double[] moon(double d) {
        double n = rev(125.1228 - 0.0529538083 * d);
        double inc = 5.1454;
        double w = rev(318.0634 + 0.1643573223 * d);
        double a = 60.2666;
        double e = 0.054900;
        double mm = rev(115.3654 + 13.0649929509 * d);

        double ea = kepler(mm, e);
        double xv = a * (cosd(ea) - e);
        double yv = a * Math.sqrt(1 - e * e) * sind(ea);
        double v = Math.toDegrees(Math.atan2(yv, xv));
        double r = Math.hypot(xv, yv);

        double xh = r * (cosd(n) * cosd(v + w) - sind(n) * sind(v + w) * cosd(inc));
        double yh = r * (sind(n) * cosd(v + w) + cosd(n) * sind(v + w) * cosd(inc));
        double zh = r * sind(v + w) * sind(inc);
        double lon = Math.toDegrees(Math.atan2(yh, xh));
        double lat = Math.toDegrees(Math.atan2(zh, Math.hypot(xh, yh)));

        double[] s = sun(d);
        double ms = s[2];
        double ls = rev(s[2] + s[3]);
        double lm = rev(mm + w + n);
        double dd = rev(lm - ls);
        double f = rev(lm - n);

        lon += -1.274 * sind(mm - 2 * dd) + 0.658 * sind(2 * dd) - 0.186 * sind(ms)
                - 0.059 * sind(2 * mm - 2 * dd) - 0.057 * sind(mm - 2 * dd + ms)
                + 0.053 * sind(mm + 2 * dd) + 0.046 * sind(2 * dd - ms) + 0.041 * sind(mm - ms)
                - 0.035 * sind(dd) - 0.031 * sind(mm + ms) - 0.015 * sind(2 * f - 2 * dd)
                + 0.011 * sind(mm - 4 * dd);
        lat += -0.173 * sind(f - 2 * dd) - 0.055 * sind(mm - f - 2 * dd)
                - 0.046 * sind(mm + f - 2 * dd) + 0.033 * sind(f + 2 * dd) + 0.017 * sind(2 * mm + f);
        r += -0.58 * cosd(mm - 2 * dd) - 0.46 * cosd(2 * dd);

        return new double[]{r * cosd(lon) * cosd(lat), r * sind(lon) * cosd(lat), r * sind(lat), r};
    }

    /** Ecliptic (of date) rectangular -> equatorial RA/Dec in degrees. */
    private static double[] eclToEq(double x, double y, double z, double ecl) {
        double xe = x;
        double ye = y * cosd(ecl) - z * sind(ecl);
        double ze = y * sind(ecl) + z * cosd(ecl);
        return new double[]{rev(Math.toDegrees(Math.atan2(ye, xe))), Math.toDegrees(Math.atan2(ze, Math.hypot(xe, ye)))};
    }

    /** RA/Dec (degrees) -> {azimuth from true north, altitude}. */
    private static double[] horizontal(double ra, double dec, double jd, double latDeg, double lonDeg) {
        double gmst = rev(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        double lst = rev(gmst + lonDeg);
        double ha = Math.toRadians(rev(lst - ra));
        double la = Math.toRadians(latDeg);
        double de = Math.toRadians(dec);
        double alt = Math.toDegrees(Math.asin(
                Math.sin(de) * Math.sin(la) + Math.cos(de) * Math.cos(la) * Math.cos(ha)));
        double az = rev(Math.toDegrees(Math.atan2(Math.sin(ha),
                Math.cos(ha) * Math.sin(la) - Math.tan(de) * Math.cos(la))) + 180.0);
        return new double[]{az, alt};
    }

    /** Equatorial J2000 unit vector -> galactic {l, b} in degrees. */
    private static double[] galacticFromVector(double xe, double ye, double ze) {
        double gx = -0.0548755604 * xe - 0.8734370902 * ye - 0.4838350155 * ze;
        double gy = 0.4941094279 * xe - 0.4448296300 * ye + 0.7469822445 * ze;
        double gz = -0.8676661490 * xe - 0.1980763734 * ye + 0.4559837762 * ze;
        return new double[]{rev(Math.toDegrees(Math.atan2(gy, gx))), Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, gz))))};
    }

    /** Galactic {l, b} from J2000 equatorial RA/Dec (degrees). */
    public static double[] galacticFromJ2000(double raDeg, double decDeg) {
        return galacticFromVector(cosd(decDeg) * cosd(raDeg), cosd(decDeg) * sind(raDeg), sind(decDeg));
    }

    /** General precession in ecliptic longitude since J2000, degrees (t in Julian centuries). */
    private static double precession(double t) {
        return (5028.796195 * t + 1.1054348 * t * t) / 3600.0;
    }

    /** Galactic {l, b} of a direction given by geocentric ecliptic (of date) longitude/latitude. */
    private static double[] galacticFromEcliptic(double lonDate, double latDate, double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double lon0 = lonDate - precession(t);
        double eps = 23.4392911;
        double x = cosd(latDate) * cosd(lon0);
        double y = cosd(latDate) * sind(lon0);
        double z = sind(latDate);
        return galacticFromVector(x, y * cosd(eps) - z * sind(eps), y * sind(eps) + z * cosd(eps));
    }

    // ------------------------------------------------------------------ public API

    private static Body bodyAt(int i, double jd, double latDeg, double lonDeg) {
        double d = jd - 2451543.5;
        double ecl = 23.4393 - 3.563E-7 * d;

        double xg, yg, zg, dist;
        if (i == 0) {
            double[] s = sun(d);
            xg = s[1] * cosd(s[0]);
            yg = s[1] * sind(s[0]);
            zg = 0;
            dist = s[1];
        } else if (i == 1) {
            double[] m = moon(d);
            xg = m[0];
            yg = m[1];
            zg = m[2];
            dist = m[3];
        } else {
            double[] p = planet(i - 2, d);
            xg = p[0];
            yg = p[1];
            zg = p[2];
            dist = Math.sqrt(xg * xg + yg * yg + zg * zg);
        }

        double[] eq = eclToEq(xg, yg, zg, ecl);
        double[] hz = horizontal(eq[0], eq[1], jd, latDeg, lonDeg);
        double alt = hz[1];
        if (i == 1) { // the Moon is close: correct for parallax
            double parallax = Math.toDegrees(Math.asin(1.0 / dist));
            alt -= parallax * Math.cos(Math.toRadians(alt));
        }

        double lonE = Math.toDegrees(Math.atan2(yg, xg));
        double latE = Math.toDegrees(Math.atan2(zg, Math.hypot(xg, yg)));
        double[] gal = galacticFromEcliptic(lonE, latE, jd);

        Body b = new Body(NAMES[i], i);
        b.ra = eq[0];
        b.dec = eq[1];
        b.azimuth = hz[0];
        b.altitude = alt;
        b.distance = dist;
        b.galL = gal[0];
        b.galB = gal[1];
        return b;
    }

    /**
     * Positions of the Sun, Moon and the seven planets.
     *
     * @param utcMillis time as milliseconds since 1970 (UTC)
     * @param latDeg    observer latitude, north positive
     * @param lonDeg    observer longitude, east positive
     */
    public static Body[] compute(long utcMillis, double latDeg, double lonDeg) {
        double jd = jdOf(utcMillis);
        Body[] out = new Body[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            out[i] = bodyAt(i, jd, latDeg, lonDeg);
        }
        return out;
    }

    /**
     * Rise and set of one body between startMillis and endMillis.
     * Returns {rise, set, state}: times in millis (-1 = none in that window);
     * state 0 = normal, 1 = above the horizon all the time, 2 = below it all the time.
     */
    public static long[] riseSet(int idx, long startMillis, long endMillis, double latDeg, double lonDeg) {
        double h0 = idx <= 1 ? -0.833 : -0.567; // refraction (+ size for Sun/Moon)
        long step = 10L * 60L * 1000L;
        long rise = -1;
        long set = -1;
        double prev = bodyAt(idx, jdOf(startMillis), latDeg, lonDeg).altitude - h0;
        boolean anyAbove = prev > 0;
        for (long t = startMillis + step; ; t += step) {
            long tt = Math.min(t, endMillis);
            double cur = bodyAt(idx, jdOf(tt), latDeg, lonDeg).altitude - h0;
            if (cur > 0) anyAbove = true;
            if (prev <= 0 && cur > 0 && rise < 0) {
                rise = refine(idx, tt - step, tt, true, h0, latDeg, lonDeg);
            } else if (prev > 0 && cur <= 0 && set < 0) {
                set = refine(idx, tt - step, tt, false, h0, latDeg, lonDeg);
            }
            prev = cur;
            if (tt >= endMillis) break;
        }
        int state = 0;
        if (rise < 0 && set < 0) state = anyAbove ? 1 : 2;
        return new long[]{rise, set, state};
    }

    private static long refine(int idx, long lo, long hi, boolean rising, double h0, double lat, double lon) {
        for (int k = 0; k < 12; k++) {
            long mid = (lo + hi) / 2;
            double f = bodyAt(idx, jdOf(mid), lat, lon).altitude - h0;
            if (rising ? f > 0 : f <= 0) hi = mid;
            else lo = mid;
        }
        return (lo + hi) / 2;
    }

    /** Moon phase: {illuminated fraction 0..1, 1 if waxing else 0}. */
    public static double[] moonPhase(long utcMillis) {
        double d = jdOf(utcMillis) - 2451543.5;
        double[] m = moon(d);
        double moonLon = Math.toDegrees(Math.atan2(m[1], m[0]));
        double elong = rev(moonLon - sun(d)[0]);
        return new double[]{(1 - cosd(elong)) / 2.0, elong < 180.0 ? 1 : 0};
    }

    /** Heliocentric ecliptic {x, y, z} in AU for the solar-system map: index per SOLAR_NAMES (2 = Earth). */
    public static double[] heliocentric(int solarIndex, long utcMillis) {
        double d = jdOf(utcMillis) - 2451543.5;
        if (solarIndex == 2) {
            double[] s = sun(d);
            return new double[]{-s[1] * cosd(s[0]), -s[1] * sind(s[0]), 0.0};
        }
        return helioPlanet(solarIndex < 2 ? solarIndex : solarIndex - 1, d);
    }

    /** Where the Galactic Centre is in the sky: {azimuth from true north, altitude}. */
    public static double[] galacticCentreAltAz(long utcMillis, double latDeg, double lonDeg) {
        double jd = jdOf(utcMillis);
        double d = jd - 2451543.5;
        double t = (jd - 2451545.0) / 36525.0;
        double eps0 = 23.4392911;
        // l = 0, b = 0 as an equatorial J2000 vector (first row of the rotation matrix)
        double xe = -0.0548755604, ye = -0.8734370902, ze = -0.4838350155;
        double y = ye * cosd(eps0) + ze * sind(eps0);
        double z = -ye * sind(eps0) + ze * cosd(eps0);
        double lon = Math.toDegrees(Math.atan2(y, xe)) + precession(t);
        double lat = Math.toDegrees(Math.asin(z));
        double[] eq = eclToEq(cosd(lat) * cosd(lon), cosd(lat) * sind(lon), sind(lat), 23.4393 - 3.563E-7 * d);
        return horizontal(eq[0], eq[1], jd, latDeg, lonDeg);
    }

    /** March equinox, June solstice, September equinox, December solstice of a year (UTC millis). */
    public static long[] seasons(int year) {
        double y = (year - 2000) / 1000.0;
        double[] jde0 = {
                2451623.80984 + 365242.37404 * y + 0.05169 * y * y - 0.00411 * y * y * y - 0.00057 * y * y * y * y,
                2451716.56767 + 365241.62603 * y + 0.00325 * y * y + 0.00888 * y * y * y - 0.00030 * y * y * y * y,
                2451810.21715 + 365242.01767 * y - 0.11575 * y * y + 0.00337 * y * y * y + 0.00078 * y * y * y * y,
                2451900.05952 + 365242.74049 * y - 0.06223 * y * y - 0.00823 * y * y * y + 0.00032 * y * y * y * y};
        double dt = deltaT(year);
        long[] out = new long[4];
        for (int k = 0; k < 4; k++) {
            double t = (jde0[k] - 2451545.0) / 36525.0;
            double w = 35999.373 * t - 2.47;
            double dl = 1 + 0.0334 * cosd(w) + 0.0007 * cosd(2 * w);
            double s = 0;
            for (int i = 0; i < SA.length; i++) s += SA[i] * cosd(SB[i] + SC[i] * t);
            double jde = jde0[k] + 0.00001 * s / dl;
            double jdUt = jde - dt / 86400.0;
            out[k] = Math.round((jdUt - 2440587.5) * 86400000.0);
        }
        return out;
    }

    private static double deltaT(int year) {
        double t = year - 2000;
        return 62.92 + 0.32217 * t + 0.005589 * t * t;
    }

    /** Year of a time in the UTC calendar. */
    public static int utcYear(long utcMillis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(utcMillis);
        return c.get(Calendar.YEAR);
    }
}
