package com.codigrate.colorpicker;

import java.awt.Color;

/** WCAG 2 contrast math, matching the Codigrate color tool's readability checks. */
public final class Wcag {

    private Wcag() {
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double relativeLuminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    public static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** AA needs 4.5:1, AAA needs 7:1 for normal-size text. */
    public static String normalTextLevel(double ratio) {
        return ratio >= 7 ? "AAA" : ratio >= 4.5 ? "AA" : "Fail";
    }

    /** AA needs 3:1, AAA needs 4.5:1 for large text (18pt+, or 14pt bold). */
    public static String largeTextLevel(double ratio) {
        return ratio >= 4.5 ? "AAA" : ratio >= 3 ? "AA" : "Fail";
    }

    // ---- APCA (WCAG 3 draft), same 0.0.98 formula as the website ---------------

    private static double apcaY(Color c) {
        return 0.2126729 * Math.pow(c.getRed() / 255.0, 2.4)
                + 0.7151522 * Math.pow(c.getGreen() / 255.0, 2.4)
                + 0.0721750 * Math.pow(c.getBlue() / 255.0, 2.4);
    }

    /** Signed APCA lightness contrast Lc for text over a background (~ -108..106). */
    public static int apcaContrast(Color text, Color bg) {
        double blkThrs = 0.022, blkClmp = 1.414;
        double txtY = apcaY(text);
        double bgY = apcaY(bg);
        txtY = txtY > blkThrs ? txtY : txtY + Math.pow(blkThrs - txtY, blkClmp);
        bgY = bgY > blkThrs ? bgY : bgY + Math.pow(blkThrs - bgY, blkClmp);
        if (Math.abs(bgY - txtY) < 0.0005) {
            return 0;
        }
        double sapc, out;
        if (bgY > txtY) {
            sapc = (Math.pow(bgY, 0.56) - Math.pow(txtY, 0.57)) * 1.14;
            out = sapc < 0.1 ? 0 : sapc - 0.027;
        } else {
            sapc = (Math.pow(bgY, 0.65) - Math.pow(txtY, 0.62)) * 1.14;
            out = sapc > -0.1 ? 0 : sapc + 0.027;
        }
        return (int) Math.round(out * 100);
    }

    /** Plain-language APCA usage level for an Lc magnitude. */
    public static String apcaLevel(int lc) {
        int a = Math.abs(lc);
        if (a >= 75) {
            return "Body text";
        }
        if (a >= 60) {
            return "Large / bold";
        }
        if (a >= 45) {
            return "Large text";
        }
        if (a >= 30) {
            return "UI / non-text";
        }
        return "Fail";
    }
}
