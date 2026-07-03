package com.codigrate.colorpicker;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Nearest RAL Classic match by CIE-Lab distance, same data and math as the
 * website (216 colors, community open-data hex values).
 */
public final class Ral {

    private record RalColor(String code, double[] lab) {
    }

    private static volatile List<RalColor> table;

    private Ral() {
    }

    /** Display code of the closest RAL Classic color, e.g. "RAL 5012". */
    public static String nearestCode(Color c) {
        List<RalColor> ralTable = load();
        if (ralTable.isEmpty()) {
            return "";
        }
        double[] lab = ColorFormats.labValues(c);
        String best = "";
        double bestD = Double.MAX_VALUE;
        for (RalColor ral : ralTable) {
            double dl = lab[0] - ral.lab()[0];
            double da = lab[1] - ral.lab()[1];
            double db = lab[2] - ral.lab()[2];
            double d = dl * dl + da * da + db * db;
            if (d < bestD) {
                bestD = d;
                best = ral.code();
            }
        }
        return "RAL " + best;
    }

    private static List<RalColor> load() {
        List<RalColor> list = table;
        if (list != null) {
            return list;
        }
        synchronized (Ral.class) {
            if (table != null) {
                return table;
            }
            list = new ArrayList<>(220);
            try (InputStream in = Ral.class.getResourceAsStream("/data/ral-classic.txt")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab > 0) {
                            Color color = ColorFormats.fromHex("#" + line.substring(tab + 1));
                            if (color != null) {
                                list.add(new RalColor(line.substring(0, tab), ColorFormats.labValues(color)));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // RAL is informational; a missing resource must not break the picker.
            }
            table = list;
            return list;
        }
    }
}
