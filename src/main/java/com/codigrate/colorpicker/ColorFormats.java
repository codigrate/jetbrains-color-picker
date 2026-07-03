package com.codigrate.colorpicker;

import java.awt.Color;

/**
 * Color conversions and wheel math, ported from the Codigrate color tool
 * (company-profile color.service.ts) so the values here match the website
 * exactly, rounding included.
 */
public final class ColorFormats {

    private ColorFormats() {
    }

    // ---- basic formats -------------------------------------------------------

    public static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static String toRgb(Color c) {
        return String.format("rgb(%d, %d, %d)", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static String toRgbPercent(Color c) {
        return String.format("rgb(%d%%, %d%%, %d%%)",
                Math.round(c.getRed() / 255.0 * 100),
                Math.round(c.getGreen() / 255.0 * 100),
                Math.round(c.getBlue() / 255.0 * 100));
    }

    public static String toHsl(Color c) {
        int[] hsl = hslOf(c);
        return String.format("hsl(%d, %d%%, %d%%)", hsl[0], hsl[1], hsl[2]);
    }

    /** Rounded integer HSV (h 0..360, s/v 0..100). */
    public static int[] hsvOf(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double d = max - min;
        double h = 0;
        if (d != 0) {
            if (max == r) {
                h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
            } else if (max == g) {
                h = ((b - r) / d + 2) * 60;
            } else {
                h = ((r - g) / d + 4) * 60;
            }
        }
        double s = max == 0 ? 0 : d / max;
        return new int[]{(int) Math.round(h), (int) Math.round(s * 100), (int) Math.round(max * 100)};
    }

    public static String toHsv(Color c) {
        int[] hsv = hsvOf(c);
        return String.format("hsv(%d, %d%%, %d%%)", hsv[0], hsv[1], hsv[2]);
    }

    /** HWB: the HSL hue plus whiteness and blackness percentages. */
    public static String toHwb(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        int hue = hslOf(c)[0];
        long w = Math.round(Math.min(r, Math.min(g, b)) * 100);
        long bl = Math.round((1 - Math.max(r, Math.max(g, b))) * 100);
        return String.format("hwb(%d %d%% %d%%)", hue, w, bl);
    }

    /** Rounded integer CMYK percentages. */
    public static int[] cmykOf(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        double k = 1 - Math.max(r, Math.max(g, b));
        double cy = 0, m = 0, y = 0;
        if (k < 1) {
            cy = (1 - r - k) / (1 - k);
            m = (1 - g - k) / (1 - k);
            y = (1 - b - k) / (1 - k);
        }
        return new int[]{
                (int) Math.round(cy * 100), (int) Math.round(m * 100),
                (int) Math.round(y * 100), (int) Math.round(k * 100)};
    }

    public static String toCmyk(Color c) {
        int[] cmyk = cmykOf(c);
        return String.format("cmyk(%d%%, %d%%, %d%%, %d%%)", cmyk[0], cmyk[1], cmyk[2], cmyk[3]);
    }

    // ---- number bases ----------------------------------------------------------

    public static String toDecimal(Color c) {
        return String.valueOf(c.getRGB() & 0xFFFFFF);
    }

    /** Space-separated per-channel octal. */
    public static String toOctal(Color c) {
        return Integer.toOctalString(c.getRed()) + " "
                + Integer.toOctalString(c.getGreen()) + " "
                + Integer.toOctalString(c.getBlue());
    }

    /** Space-separated 8-bit binary per channel. */
    public static String toBinary(Color c) {
        return pad8(c.getRed()) + " " + pad8(c.getGreen()) + " " + pad8(c.getBlue());
    }

    private static String pad8(int v) {
        String s = Integer.toBinaryString(v);
        return "0".repeat(8 - s.length()) + s;
    }

    /** Nearest web-safe color (each channel snapped to a 0/51/.../255 step). */
    public static Color webSafe(Color c) {
        return new Color(
                Math.round(c.getRed() / 51f) * 51,
                Math.round(c.getGreen() / 51f) * 51,
                Math.round(c.getBlue() / 51f) * 51);
    }

    // ---- perceptual and CIE spaces ----------------------------------------------

    /** Linear-light sRGB channels [0..1] (the site's gamma curve, 0.03928 knee). */
    private static double[] linearRgb(Color c) {
        double[] out = new double[3];
        int[] ch = {c.getRed(), c.getGreen(), c.getBlue()};
        for (int i = 0; i < 3; i++) {
            double v = ch[i] / 255.0;
            out[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return out;
    }

    /** CIE XYZ on the 0..1 scale (sRGB D65 matrix, matching the site). */
    private static double[] xyzOf(Color c) {
        double[] rgb = linearRgb(c);
        return new double[]{
                rgb[0] * 0.4124564 + rgb[1] * 0.3575761 + rgb[2] * 0.1804375,
                rgb[0] * 0.2126729 + rgb[1] * 0.7151522 + rgb[2] * 0.0721750,
                rgb[0] * 0.0193339 + rgb[1] * 0.1191920 + rgb[2] * 0.9503041,
        };
    }

    /** Unrounded CIE-Lab values, for distance math (RAL matching). */
    public static double[] labValues(Color c) {
        return labOf(c);
    }

    private static double[] labOf(Color c) {
        double[] xyz = xyzOf(c);
        double xn = xyz[0] / 0.95047;
        double yn = xyz[1] / 1.00000;
        double zn = xyz[2] / 1.08883;
        double fx = xn > 0.008856 ? Math.cbrt(xn) : (903.3 * xn + 16) / 116;
        double fy = yn > 0.008856 ? Math.cbrt(yn) : (903.3 * yn + 16) / 116;
        double fz = zn > 0.008856 ? Math.cbrt(zn) : (903.3 * zn + 16) / 116;
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double[] oklabOf(Color c) {
        double[] rgb = linearRgb(c);
        double l = 0.4122214708 * rgb[0] + 0.5363325363 * rgb[1] + 0.0514459929 * rgb[2];
        double m = 0.2119034982 * rgb[0] + 0.6806995451 * rgb[1] + 0.1073969566 * rgb[2];
        double s = 0.0883024619 * rgb[0] + 0.2817188376 * rgb[1] + 0.6299787005 * rgb[2];
        double l3 = Math.cbrt(l), m3 = Math.cbrt(m), s3 = Math.cbrt(s);
        return new double[]{
                0.2104542553 * l3 + 0.7936177850 * m3 - 0.0040720468 * s3,
                1.9779984951 * l3 - 2.4285922050 * m3 + 0.4505937099 * s3,
                0.0259040371 * l3 + 0.7827717662 * m3 - 0.8086757660 * s3,
        };
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    private static String num(double v) {
        // Trim a trailing ".0" so values read like the website's (e.g. "240" not "240.0").
        String s = String.valueOf(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String toOklab(Color c) {
        double[] ok = oklabOf(c);
        return String.format("oklab(%s %s %s)", num(round(ok[0], 3)), num(round(ok[1], 3)), num(round(ok[2], 3)));
    }

    public static String toOklch(Color c) {
        double[] ok = oklabOf(c);
        double chroma = Math.sqrt(ok[1] * ok[1] + ok[2] * ok[2]);
        double h = Math.toDegrees(Math.atan2(ok[2], ok[1]));
        if (h < 0) {
            h += 360;
        }
        return String.format("oklch(%s %s %s)", num(round(ok[0], 3)), num(round(chroma, 3)), num(round(h, 1)));
    }

    public static String toLab(Color c) {
        double[] lab = labOf(c);
        return String.format("lab(%s %s %s)", num(round(lab[0], 3)), num(round(lab[1], 3)), num(round(lab[2], 3)));
    }

    public static String toLch(Color c) {
        double[] lab = labOf(c);
        double chroma = Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
        double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        if (h < 0) {
            h += 360;
        }
        return String.format("lch(%s %s %s)", num(round(lab[0], 2)), num(round(chroma, 2)), num(round(h, 1)));
    }

    public static String toXyz(Color c) {
        double[] xyz = xyzOf(c);
        return String.format("xyz(%s, %s, %s)", num(round(xyz[0], 3)), num(round(xyz[1], 3)), num(round(xyz[2], 3)));
    }

    /** CIE xyY: luminance Y (0..100) plus chromaticity coordinates x, y. */
    public static String toYxy(Color c) {
        double[] xyz = xyzOf(c);
        double x = xyz[0] * 100, y = xyz[1] * 100, z = xyz[2] * 100;
        double sum = x + y + z;
        if (sum == 0) {
            sum = 1;
        }
        return String.format("yxy(%s, %s, %s)", num(round(y, 2)), num(round(x / sum, 4)), num(round(y / sum, 4)));
    }

    /** Hunter Lab (1958 formulation), from D65 XYZ scaled to 0..100. */
    public static String toHunterLab(Color c) {
        double[] xyz = xyzOf(c);
        double x = xyz[0] * 100, y = xyz[1] * 100, z = xyz[2] * 100;
        if (y <= 0) {
            return "0, 0, 0";
        }
        double sq = Math.sqrt(y);
        return String.format("%s, %s, %s",
                num(round(10 * sq, 2)),
                num(round(17.5 * ((1.02 * x - y) / sq), 2)),
                num(round(7 * ((y - 0.847 * z) / sq), 2)));
    }

    // ---- HSL wheel math (harmony, ramps, temperature) -----------------------------

    /** Rounded integer HSL, like the site's hexToHsl (h 0..360, s/l 0..100). */
    public static int[] hslOf(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double h = 0, s = 0;
        double l = (max + min) / 2;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h *= 60;
        }
        return new int[]{(int) Math.round(h), (int) Math.round(s * 100), (int) Math.round(l * 100)};
    }

    public static Color fromHsl(double h, double s, double l) {
        h = ((h % 360) + 360) % 360;
        s = Math.max(0, Math.min(100, s)) / 100.0;
        l = Math.max(0, Math.min(100, l)) / 100.0;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return new Color(
                (int) Math.round((r + m) * 255),
                (int) Math.round((g + m) * 255),
                (int) Math.round((b + m) * 255));
    }

    /** The same color with its hue rotated on the HSL wheel (harmony schemes). */
    public static Color rotateHue(Color c, double degrees) {
        int[] hsl = hslOf(c);
        return fromHsl(hsl[0] + degrees, hsl[1], hsl[2]);
    }

    /** Linear blend toward another color; f=0 stays put, f=1 is the target. */
    public static Color mix(Color from, Color to, double f) {
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f));
    }

    /**
     * The site's tint/shade/tone ramp: n steps interpolated toward the end color,
     * starting at the color itself and stopping just short of the end.
     */
    public static Color[] ramp(Color from, Color to, int n) {
        Color[] out = new Color[n];
        for (int i = 0; i < n; i++) {
            out[i] = mix(from, to, (double) i / n);
        }
        return out;
    }

    /**
     * The site's analogous chip: the base in the middle, flanked by its outer
     * neighbours from a 5-color, 11-degree-step analogous ramp (indices 0/2/4).
     */
    public static Color[] analogousTrio(Color c) {
        return new Color[]{rotateHue(c, -22), c, rotateHue(c, 22)};
    }

    /**
     * The site's monochrome chip: a darker and a lighter lightness step of the
     * same hue around the base (outer pair of a 5-color ladder; darker side
     * steps by min(8, l/2.5), lighter side by min(8, (100-l)/2)).
     */
    public static Color[] monochromeTrio(Color c) {
        int[] hsl = hslOf(c);
        double down = Math.min(8, hsl[2] / 2.5);
        double up = Math.min(8, (100.0 - hsl[2]) / 2);
        return new Color[]{
                fromHsl(hsl[0], hsl[1], Math.max(hsl[2] - 2 * down, 0)),
                c,
                fromHsl(hsl[0], hsl[1], Math.min(hsl[2] + 2 * up, 100)),
        };
    }

    /** Warm hue bands per the site: [0, 90) and [270, 360]. */
    public static boolean isWarm(Color c) {
        int h = hslOf(c)[0];
        return h < 90 || h >= 270;
    }

    /** Hues stepped toward red (site math: shortest way to hue 0, 11 colors). */
    public static Color[] warmer(Color c, int n) {
        int[] hsl = hslOf(c);
        double diff = hsl[0];
        double clockwise = -1;
        if (diff > 180) {
            diff = 360 - diff;
            clockwise = 1;
        }
        double step = Math.min(9, (diff * clockwise) / 12);
        Color[] out = new Color[n];
        for (int i = 0; i < n; i++) {
            out[i] = fromHsl(hsl[0] + i * step, hsl[1], hsl[2]);
        }
        return out;
    }

    /** Hues stepped toward cyan (site math: toward hue 180, 11 colors). */
    public static Color[] cooler(Color c, int n) {
        int[] hsl = hslOf(c);
        double step = Math.min(9, (180.0 - hsl[0]) / 12);
        Color[] out = new Color[n];
        for (int i = 0; i < n; i++) {
            out[i] = fromHsl(hsl[0] + i * step, hsl[1], hsl[2]);
        }
        return out;
    }

    /** Parses "#RRGGBB"; returns null when the value is malformed. */
    public static Color fromHex(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return null;
        }
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
