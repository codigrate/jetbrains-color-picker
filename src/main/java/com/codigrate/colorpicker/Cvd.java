package com.codigrate.colorpicker;

import java.awt.Color;

/**
 * Color vision deficiency simulation, ported 1:1 from the color-blind npm
 * package (HCIRN / Matthew Wickline algorithm, CC BY-SA 4.0) that powers the
 * Codigrate color tool, so the simulated values match the website.
 */
public final class Cvd {

    private Cvd() {
    }

    public static final String PROTAN = "protan";
    public static final String DEUTAN = "deutan";
    public static final String TRITAN = "tritan";
    public static final String ACHROMA = "achroma";

    private static final double GAMMA = 2.2;

    private static final double[] XYZ_TO_RGB = {
            3.240712470389558, -0.969259258688888, 0.05563600315398933,
            -1.5372626602963142, 1.875996969313966, -0.2039948802843549,
            -0.49857440415943116, 0.041556132211625726, 1.0570636917433989,
    };
    private static final double[] RGB_TO_XYZ = {
            0.41242371206635076, 0.21265606784927693, 0.019331987577444885,
            0.3575793401363035, 0.715157818248362, 0.11919267420354762,
            0.1804662232369621, 0.0721864539171564, 0.9504491124870351,
    };

    /** Confusion point (x, y) plus the color axis slope m and y-intercept yi. */
    private record ConfusionLine(double x, double y, double m, double yi) {
    }

    private static ConfusionLine lineFor(String type) {
        return switch (type) {
            case PROTAN -> new ConfusionLine(0.7465, 0.2535, 1.273463, -0.073894);
            case DEUTAN -> new ConfusionLine(1.4, -0.4, 0.968437, 0.003331);
            case TRITAN -> new ConfusionLine(0.1748, 0, 0.062921, 0.292119);
            default -> throw new IllegalArgumentException(type);
        };
    }

    /** Simulates the deficiency; anomalize=true gives the mild (-omaly) variant. */
    public static Color simulate(Color color, String type, boolean anomalize) {
        double inR = color.getRed(), inG = color.getGreen(), inB = color.getBlue();
        double outR, outG, outB;

        if (ACHROMA.equals(type)) {
            // D65 luminance in sRGB, straight on the 0..255 channels.
            double z = inR * 0.212656 + inG * 0.715158 + inB * 0.072186;
            outR = z;
            outG = z;
            outB = z;
        } else {
            ConfusionLine line = lineFor(type);

            // sRGB -> XYZ -> xyY chromaticity.
            double lr = srgbToLinear(inR), lg = srgbToLinear(inG), lb = srgbToLinear(inB);
            double x = lr * RGB_TO_XYZ[0] + lg * RGB_TO_XYZ[3] + lb * RGB_TO_XYZ[6];
            double y = lr * RGB_TO_XYZ[1] + lg * RGB_TO_XYZ[4] + lb * RGB_TO_XYZ[7];
            double z = lr * RGB_TO_XYZ[2] + lg * RGB_TO_XYZ[5] + lb * RGB_TO_XYZ[8];
            double sum = x + y + z;
            double cx = sum == 0 ? 0 : x / sum;
            double cy = sum == 0 ? 0 : y / sum;
            double bigY = y;

            // Intersect the confusion line through this color with the color axis.
            double slope = (cy - line.y()) / (cx - line.x());
            double yi = cy - cx * slope;
            double dx = (line.yi() - yi) / (slope - line.m());
            double dy = slope * dx + yi;

            // The simulated color's XYZ, then the shift toward neutral grey (D65)
            // needed to fit it back into the RGB gamut.
            double simX = dx * bigY / dy;
            double simZ = (1 - (dx + dy)) * bigY / dy;
            double ngx = 0.312713 * bigY / 0.329016;
            double ngz = 0.358271 * bigY / 0.329016;
            double dX = ngx - simX;
            double dZ = ngz - simZ;

            double dR = dX * XYZ_TO_RGB[0] + dZ * XYZ_TO_RGB[6];
            double dG = dX * XYZ_TO_RGB[1] + dZ * XYZ_TO_RGB[7];
            double dB = dX * XYZ_TO_RGB[2] + dZ * XYZ_TO_RGB[8];
            double r = simX * XYZ_TO_RGB[0] + bigY * XYZ_TO_RGB[3] + simZ * XYZ_TO_RGB[6];
            double g = simX * XYZ_TO_RGB[1] + bigY * XYZ_TO_RGB[4] + simZ * XYZ_TO_RGB[7];
            double b = simX * XYZ_TO_RGB[2] + bigY * XYZ_TO_RGB[5] + simZ * XYZ_TO_RGB[8];

            double adjR = clamp01Ratio(((r < 0 ? 0 : 1) - r) / dR);
            double adjG = clamp01Ratio(((g < 0 ? 0 : 1) - g) / dG);
            double adjB = clamp01Ratio(((b < 0 ? 0 : 1) - b) / dB);
            double adjust = Math.max(adjR, Math.max(adjG, adjB));

            outR = gammaEncode(r + adjust * dR);
            outG = gammaEncode(g + adjust * dG);
            outB = gammaEncode(b + adjust * dB);
        }

        if (anomalize) {
            double v = 1.75, n = v + 1;
            outR = (v * outR + inR) / n;
            outG = (v * outG + inG) / n;
            outB = (v * outB + inB) / n;
        }
        return new Color(channel(outR), channel(outG), channel(outB));
    }

    private static double srgbToLinear(double v255) {
        double v = v255 / 255.0;
        return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }

    private static double gammaEncode(double v) {
        return 255 * (v <= 0 ? 0 : v >= 1 ? 1 : Math.pow(v, 1 / GAMMA));
    }

    private static double clamp01Ratio(double v) {
        return (Double.isNaN(v) || v > 1 || v < 0) ? 0 : v;
    }

    private static int channel(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
