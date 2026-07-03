package com.codigrate.colorpicker;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool window content mirroring the Codigrate color tool page, in the page's
 * section order: CSS and readability (Preview), channels, conversions, harmony,
 * tint/shade/tone ramps, temperature and color vision deficiency, plus the
 * picker's own recent colors. Sections are collapsible and remember their state.
 */
public class ColorPickerPanel extends JPanel {

    private static final Icon PIPETTE = IconLoader.getIcon("/icons/pipette.svg", ColorPickerPanel.class);
    private static final Color DEFAULT_COLOR = new Color(0x3F4494);

    /** Readability badge colors: AAA green, AA amber, Fail red (same in both LAFs). */
    private static final Color LEVEL_AAA = new Color(0x3FB27A);
    private static final Color LEVEL_AA = new Color(0xD9A343);
    private static final Color LEVEL_FAIL = new Color(0xE0584F);

    /** The Pick button and the swatch stop stretching past this width, centered. */
    private static final int HERO_MAX_WIDTH = 300;

    /**
     * Preferred width reported by stretchy rows. Kept small so the content's
     * preferred width never exceeds a narrow tool window, which would flip
     * GridBagLayout into minimum-size mode and collapse fixed-height components.
     */
    private static final int ROW_PREF_WIDTH = 120;

    private final Project project;

    private final Swatch swatch = new Swatch();

    private final List<ConvRow> cssRows = new ArrayList<>();
    private final List<ConvRow> convRows = new ArrayList<>();

    // Readability rows in the page's order: both directions per surface, then
    // both directions against the complementary (the page's default pair).
    private final ContrastRow onWhiteRow = new ContrastRow("On White");
    private final ContrastRow whiteOnRow = new ContrastRow("White On");
    private final ContrastRow onBlackRow = new ContrastRow("On Black");
    private final ContrastRow blackOnRow = new ContrastRow("Black On");
    private final ContrastRow onComplementRow = new ContrastRow("On Complement");
    private final ContrastRow complementOnRow = new ContrastRow("Complement On");

    private final ChannelBar[] rgbBars = {new ChannelBar("R"), new ChannelBar("G"), new ChannelBar("B")};
    private final ChannelBar[] hslBars = {new ChannelBar("H"), new ChannelBar("S"), new ChannelBar("L")};
    private final ChannelBar[] hsvBars = {new ChannelBar("H"), new ChannelBar("S"), new ChannelBar("V")};
    private final ChannelBar[] cmykBars = {new ChannelBar("C"), new ChannelBar("M"), new ChannelBar("Y"), new ChannelBar("K")};

    // Harmony rows in the color page's order, with the page's chip layouts
    // (analogous / monochrome keep the base in the middle slot).
    private final ChipStrip analogousStrip = new ChipStrip("Analogous");
    private final ChipStrip monochromeStrip = new ChipStrip("Monochrome");
    private final ChipStrip complementStrip = new ChipStrip("Complementary");
    private final ChipStrip splitStrip = new ChipStrip("Split Complementary");
    private final ChipStrip triadicStrip = new ChipStrip("Triadic");
    private final ChipStrip tetradicStrip = new ChipStrip("Tetradic");
    private final ChipStrip harmonyTempStrip = new ChipStrip("Temperature");

    private final ChipStrip tintsStrip = new ChipStrip("Tints");
    private final ChipStrip shadesStrip = new ChipStrip("Shades");
    private final ChipStrip tonesStrip = new ChipStrip("Tones");

    private final JBLabel temperatureVerdict = new JBLabel();
    private final ChipStrip warmerStrip = new ChipStrip("Warmer");
    private final ChipStrip coolerStrip = new ChipStrip("Cooler");

    private final JPanel cvdPanel = new JPanel();

    /** The color page's CVD list: clinical groups, each mild to severe. */
    private record CvdType(String title, String note, String type, boolean anomalize) {
    }

    private record CvdGroup(String title, CvdType[] types) {
    }

    private static final CvdGroup[] CVD_GROUPS = {
            new CvdGroup("Protan (red)", new CvdType[]{
                    new CvdType("Protanomaly", "Shifted L-cone (long-wavelength) sensitivity. Reds desaturate and darken.", Cvd.PROTAN, true),
                    new CvdType("Protanopia", "No functional L-cones. Red and green hues are confused and reds appear dim.", Cvd.PROTAN, false)}),
            new CvdGroup("Deutan (green)", new CvdType[]{
                    new CvdType("Deuteranomaly", "Shifted M-cone (medium-wavelength) sensitivity. The most prevalent deficiency.", Cvd.DEUTAN, true),
                    new CvdType("Deuteranopia", "No functional M-cones. Red and green hues are confused at normal luminance.", Cvd.DEUTAN, false)}),
            new CvdGroup("Tritan (blue)", new CvdType[]{
                    new CvdType("Tritanomaly", "Shifted S-cone (short-wavelength) sensitivity. Blue and green converge.", Cvd.TRITAN, true),
                    new CvdType("Tritanopia", "No functional S-cones. Blue and yellow hues are confused.", Cvd.TRITAN, false)}),
            new CvdGroup("Monochromacy (no color)", new CvdType[]{
                    new CvdType("Achromatomaly", "Reduced function across all three cone types, yielding a heavily desaturated image.", Cvd.ACHROMA, true),
                    new CvdType("Achromatopsia", "No functional cones. Perception reduces to luminance, seen as grayscale.", Cvd.ACHROMA, false)}),
    };

    private final JPanel historyChips = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(6)));
    private final JBLabel emptyHistory = new JBLabel("No colors picked yet");

    /** Commits the color to history shortly after the user settles on it via a chip. */
    private final Timer commitTimer;

    private Color current;

    public ColorPickerPanel(@Nullable Project project) {
        super(new BorderLayout());
        this.project = project;

        commitTimer = new Timer(500, e -> commitPick());
        commitTimer.setRepeats(false);

        add(new JBScrollPane(buildContent(),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

        ColorPickerService service = ColorPickerService.getInstance();
        Color last = ColorFormats.fromHex(service.getLast());
        show(last != null ? last : DEFAULT_COLOR);
        refreshHistory();
    }

    // ---- layout ---------------------------------------------------------------

    private JComponent buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(JBUI.Borders.empty(12));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        JButton pickButton = new JButton("Pick a Color", PIPETTE);
        pickButton.setHorizontalTextPosition(SwingConstants.TRAILING);
        pickButton.addActionListener(e -> startPick());
        content.add(capWidth(pickButton, HERO_MAX_WIDTH), c);

        c.gridy++;
        c.insets = JBUI.insetsTop(12);
        content.add(capWidth(swatch, HERO_MAX_WIDTH), c);

        addSection(content, c, "css", "CSS",
                "The color as copy-ready CSS: text, fill, border and shadows. Click a row to copy it.",
                buildCssRows());

        addSection(content, c, "accessibility", "Accessibility",
                "WCAG 2 contrast ratio and APCA (WCAG 3) of the color as text on common surfaces and "
                        + "its complementary, both directions. AA needs 4.5:1 normal / 3:1 large; AAA 7:1 / 4.5:1.",
                stack(onWhiteRow, whiteOnRow, onBlackRow, blackOnRow, onComplementRow, complementOnRow));

        addSection(content, c, "channels", "Color Channels",
                "The screen (RGB, HSL, HSV) and print (CMYK) channel mix for this color.",
                buildChannels());

        addSection(content, c, "conversions", "Conversions",
                "The color across every common color space. Click a row to copy it.",
                buildConversions());

        addSection(content, c, "harmony", "Harmony",
                "Harmonies built from this color on the color wheel. Click a chip to load it.",
                stack(analogousStrip, monochromeStrip, complementStrip, splitStrip, triadicStrip, tetradicStrip,
                        harmonyTempStrip));

        addSection(content, c, "ramps", "Tints, Shades & Tones",
                "A tint adds white, a shade adds black, a tone adds gray. Click a step to load it.",
                stack(tintsStrip, shadesStrip, tonesStrip));

        temperatureVerdict.setFont(JBFont.label().deriveFont(Font.BOLD));
        addSection(content, c, "temperature", "Temperature",
                "Where the color sits on the warm to cool axis. Its warmer and cooler neighbours follow.",
                stack(temperatureVerdict, warmerStrip, coolerStrip));

        cvdPanel.setOpaque(false);
        cvdPanel.setLayout(new BoxLayout(cvdPanel, BoxLayout.Y_AXIS));
        addSection(content, c, "cvd", "Color Vision Deficiency",
                "How this color is perceived under each color vision deficiency. "
                        + "Each pair shows the color and its simulation; click the simulation to load it.",
                cvdPanel);

        historyChips.setOpaque(false);
        emptyHistory.setForeground(UIUtil.getContextHelpForeground());
        addSection(content, c, "recent", "Recent Colors", null, historyChips);

        c.gridy++;
        c.insets = JBUI.insetsTop(16);
        ActionLink codigrateLink = new ActionLink("Explore in Codigrate Color Tool", e -> {
            if (current != null) {
                BrowserUtil.browse("https://codigrate.com/tools/color/" + ColorFormats.toHex(current).substring(1));
            }
        });
        codigrateLink.setToolTipText("Palettes, matching IDE themes and more on codigrate.com");
        content.add(codigrateLink, c);

        // Filler pushes everything to the top.
        c.gridy++;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = JBUI.emptyInsets();
        content.add(new JPanel() {{ setOpaque(false); }}, c);

        return content;
    }

    /** Lets the component stretch with the panel but never past maxWidth, centered. */
    private static JComponent capWidth(JComponent component, int maxWidth) {
        int max = JBUI.scale(maxWidth);
        Dimension pref = component.getPreferredSize();
        component.setPreferredSize(new Dimension(max, pref.height));
        component.setMaximumSize(new Dimension(max, pref.height));
        component.setMinimumSize(new Dimension(JBUI.scale(80), pref.height));
        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalGlue());
        box.add(component);
        box.add(Box.createHorizontalGlue());
        // A tiny preferred width keeps the surrounding GridBagLayout out of
        // minimum-size mode in narrow tool windows.
        return new JPanel(new BorderLayout()) {{
            setOpaque(false);
            add(box, BorderLayout.CENTER);
            setPreferredSize(new Dimension(JBUI.scale(ROW_PREF_WIDTH), pref.height));
        }};
    }

    private void addSection(JPanel content, GridBagConstraints c, String key, String title,
                            @Nullable String tooltip, JComponent body) {
        c.gridy++;
        c.insets = JBUI.insetsTop(16);
        content.add(new CollapsibleSection(key, title, tooltip, body), c);
    }

    private static JPanel stack(JComponent... rows) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JComponent row : rows) {
            row.setAlignmentX(LEFT_ALIGNMENT);
            panel.add(row);
            panel.add(Box.createVerticalStrut(JBUI.scale(6)));
        }
        return panel;
    }

    private static JBLabel groupHeader(String title) {
        JBLabel header = new JBLabel(title);
        header.setFont(JBFont.small().deriveFont(Font.BOLD));
        header.setForeground(UIUtil.getContextHelpForeground());
        header.setBorder(JBUI.Borders.empty(6, 0, 2, 0));
        header.setAlignmentX(LEFT_ALIGNMENT);
        return header;
    }

    /** The page Preview's copy-ready CSS rules as slim copy rows. */
    private JComponent buildCssRows() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (String label : new String[]{"Color", "Background", "Border", "Text Shadow", "Box Shadow"}) {
            ConvRow row = new ConvRow(label);
            row.setAlignmentX(LEFT_ALIGNMENT);
            cssRows.add(row);
            panel.add(row);
        }
        return panel;
    }

    /** The page's channel bars: RGB and CMYK fixed hues, HSL / HSV derived. */
    private JComponent buildChannels() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        record Group(String title, ChannelBar[] bars) {
        }
        for (Group group : new Group[]{
                new Group("RGB", rgbBars), new Group("HSL", hslBars),
                new Group("HSV", hsvBars), new Group("CMYK", cmykBars)}) {
            panel.add(groupHeader(group.title()));
            for (ChannelBar bar : group.bars()) {
                bar.setAlignmentX(LEFT_ALIGNMENT);
                panel.add(bar);
                panel.add(Box.createVerticalStrut(JBUI.scale(3)));
            }
        }
        return panel;
    }

    /** The site's conversions table: grouped rows, one per color space. */
    private JComponent buildConversions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        record Group(String title, String[] labels) {
        }
        Group[] groups = {
                new Group("Web & sRGB", new String[]{"Hex", "RGB", "RGB %", "Web-Safe", "Decimal", "Octal", "Binary"}),
                new Group("Hue-Based", new String[]{"HSL", "HSV", "HWB"}),
                new Group("Perceptual & Wide Gamut", new String[]{"OKLCH", "OKLab", "CIE-LAB", "CIE-LCH"}),
                new Group("Print", new String[]{"CMYK", "RAL"}),
                new Group("CIE Tristimulus", new String[]{"XYZ", "Yxy", "Hunter Lab"}),
        };
        for (Group group : groups) {
            panel.add(groupHeader(group.title()));
            for (String label : group.labels()) {
                ConvRow row = new ConvRow(label);
                row.setAlignmentX(LEFT_ALIGNMENT);
                convRows.add(row);
                panel.add(row);
            }
        }
        return panel;
    }

    // ---- behaviour ---------------------------------------------------------------

    /**
     * Starts the screen eyedropper straight away (no chooser dialog), like the
     * Codigrate Chrome extension: hover shows a live preview, click picks.
     */
    public void startPick() {
        ScreenPipette.pick(new ScreenPipette.Callback() {
            @Override
            public void picked(Color color) {
                show(color);
                commitPick();
            }

            @Override
            public void hovered(Color color) {
                // The pipette's magnifier already previews the pixel; keep the
                // panel cheap and only tint the swatch while hovering.
                swatch.setColor(color);
            }

            @Override
            public void cancelled() {
                if (current != null) {
                    swatch.setColor(current);
                }
            }
        });
    }

    private void commitPick() {
        if (current == null) {
            return;
        }
        ColorPickerService.getInstance().addPick(ColorFormats.toHex(current));
        refreshHistory();
    }

    private void show(Color color) {
        current = color;
        swatch.setColor(color);

        String hex = ColorFormats.toHex(color);
        String[] cssValues = {
                "color: " + hex + ";",
                "background-color: " + hex + ";",
                "border: 2px solid " + hex + ";",
                "text-shadow: 2px 2px 4px " + hex + ";",
                String.format("box-shadow: 0 8px 24px rgba(%d, %d, %d, 0.5);",
                        color.getRed(), color.getGreen(), color.getBlue()),
        };
        for (int i = 0; i < cssRows.size(); i++) {
            cssRows.get(i).setValue(cssValues[i]);
        }

        String[] values = {
                hex,
                ColorFormats.toRgb(color),
                ColorFormats.toRgbPercent(color),
                ColorFormats.toHex(ColorFormats.webSafe(color)),
                ColorFormats.toDecimal(color),
                ColorFormats.toOctal(color),
                ColorFormats.toBinary(color),
                ColorFormats.toHsl(color),
                ColorFormats.toHsv(color),
                ColorFormats.toHwb(color),
                ColorFormats.toOklch(color),
                ColorFormats.toOklab(color),
                ColorFormats.toLab(color),
                ColorFormats.toLch(color),
                ColorFormats.toCmyk(color),
                Ral.nearestCode(color),
                ColorFormats.toXyz(color),
                ColorFormats.toYxy(color),
                ColorFormats.toHunterLab(color),
        };
        for (int i = 0; i < convRows.size() && i < values.length; i++) {
            convRows.get(i).setValue(values[i]);
        }

        Color complement = ColorFormats.rotateHue(color, 180);
        onWhiteRow.update(color, Color.WHITE);
        whiteOnRow.update(Color.WHITE, color);
        onBlackRow.update(color, Color.BLACK);
        blackOnRow.update(Color.BLACK, color);
        onComplementRow.update(color, complement);
        complementOnRow.update(complement, color);

        updateChannels(color);

        // Chip counts and slots mirror the color page: analogous / monochrome are
        // base-centred trios, the rest lead with the base.
        String verdict = ColorFormats.isWarm(color) ? "Warm" : "Cool";
        analogousStrip.setColors(ColorFormats.analogousTrio(color));
        monochromeStrip.setColors(ColorFormats.monochromeTrio(color));
        complementStrip.setColors(new Color[]{color, complement});
        splitStrip.setColors(new Color[]{
                color, ColorFormats.rotateHue(color, 150), ColorFormats.rotateHue(color, 210)});
        triadicStrip.setColors(new Color[]{
                color, ColorFormats.rotateHue(color, 120), ColorFormats.rotateHue(color, 240)});
        tetradicStrip.setColors(new Color[]{
                color, ColorFormats.rotateHue(color, 60), complement, ColorFormats.rotateHue(color, 240)});
        harmonyTempStrip.setColors(new Color[]{color}, verdict);

        tintsStrip.setColors(ColorFormats.ramp(color, Color.WHITE, 11));
        shadesStrip.setColors(ColorFormats.ramp(color, Color.BLACK, 11));
        tonesStrip.setColors(ColorFormats.ramp(color, new Color(0x808080), 11));

        temperatureVerdict.setText("On the warm to cool axis this color reads " + verdict);
        warmerStrip.setColors(ColorFormats.warmer(color, 11));
        coolerStrip.setColors(ColorFormats.cooler(color, 11));

        rebuildCvd(color);

        revalidate();
        repaint();
    }

    /** Feeds the page's channel bar values: RGB 0-255, HSL / HSV mixed, CMYK %. */
    private void updateChannels(Color color) {
        rgbBars[0].update(String.valueOf(color.getRed()), color.getRed() / 255.0 * 100, new Color(0xD6493F));
        rgbBars[1].update(String.valueOf(color.getGreen()), color.getGreen() / 255.0 * 100, new Color(0x3AA35E));
        rgbBars[2].update(String.valueOf(color.getBlue()), color.getBlue() / 255.0 * 100, new Color(0x3A74E0));

        int[] hsl = ColorFormats.hslOf(color);
        hslBars[0].update(hsl[0] + "°", hsl[0] / 360.0 * 100, ColorFormats.fromHsl(hsl[0], 70, 45));
        hslBars[1].update(hsl[1] + "%", hsl[1], ColorFormats.fromHsl(hsl[0], hsl[1], 50));
        hslBars[2].update(hsl[2] + "%", hsl[2], ColorFormats.fromHsl(hsl[0], hsl[1], hsl[2]));

        int[] hsv = ColorFormats.hsvOf(color);
        hsvBars[0].update(hsv[0] + "°", hsv[0] / 360.0 * 100, ColorFormats.fromHsl(hsv[0], 70, 45));
        hsvBars[1].update(hsv[1] + "%", hsv[1], ColorFormats.fromHsl(hsv[0], hsv[1], 50));
        hsvBars[2].update(hsv[2] + "%", hsv[2], ColorFormats.fromHsl(hsv[0], hsv[1], (int) Math.round(hsv[2] / 2.0)));

        int[] cmyk = ColorFormats.cmykOf(color);
        Color[] cmykFill = {new Color(0x16B6D4), new Color(0xD63A9E), new Color(0xE6C21F), new Color(0x3A3A3A)};
        for (int i = 0; i < 4; i++) {
            cmykBars[i].update(cmyk[i] + "%", cmyk[i], cmykFill[i]);
        }
    }

    /** Rebuilds the CVD rows: per clinical group, the color beside its simulation. */
    private void rebuildCvd(Color color) {
        cvdPanel.removeAll();
        for (CvdGroup group : CVD_GROUPS) {
            cvdPanel.add(groupHeader(group.title()));
            for (CvdType type : group.types()) {
                Color sim = Cvd.simulate(color, type.type(), type.anomalize());
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), JBUI.scale(2)));
                row.setOpaque(false);
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(new Chip(color));
                row.add(new Chip(sim));
                JBLabel name = new JBLabel(type.title());
                name.setFont(JBFont.small());
                name.setToolTipText(type.note());
                row.add(name);
                cvdPanel.add(row);
            }
        }
        cvdPanel.revalidate();
        cvdPanel.repaint();
    }

    private void refreshHistory() {
        historyChips.removeAll();
        List<String> history = ColorPickerService.getInstance().getHistory();
        if (history.isEmpty()) {
            historyChips.add(emptyHistory);
        } else {
            for (String hex : history) {
                Color color = ColorFormats.fromHex(hex);
                if (color != null) {
                    historyChips.add(new Chip(color));
                }
            }
        }
        historyChips.revalidate();
        historyChips.repaint();
    }

    private void copyToClipboard(String value) {
        CopyPasteManager.getInstance().setContents(new StringSelection(value));
    }

    /** The active editor scheme's background: the surface behind code. */
    private static Color editorBackground() {
        return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    }

    /** The active editor scheme's foreground: text that reads on that surface. */
    private static Color editorForeground() {
        return EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground();
    }

    private static void paintRounded(Graphics g, JComponent on, Color fill, int arc) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, on.getWidth() - 1, on.getHeight() - 1, arc, arc);
        g2.setColor(JBColor.border());
        g2.drawRoundRect(0, 0, on.getWidth() - 1, on.getHeight() - 1, arc, arc);
        g2.dispose();
    }

    // ---- components ---------------------------------------------------------------

    /** A titled block whose body can be collapsed; the state persists across restarts. */
    private static final class CollapsibleSection extends JPanel {
        private final JComponent body;
        private final JBLabel arrow = new JBLabel();

        CollapsibleSection(String key, String title, @Nullable String tooltip, JComponent body) {
            super(new BorderLayout());
            this.body = body;
            setOpaque(false);

            arrow.setIcon(UIUtil.getTreeExpandedIcon());
            JBLabel titleLabel = new JBLabel(title);
            titleLabel.setFont(JBFont.label().deriveFont(Font.BOLD));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
            header.setOpaque(false);
            header.add(arrow);
            header.add(titleLabel);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (tooltip != null) {
                header.setToolTipText(tooltip);
                titleLabel.setToolTipText(tooltip);
            }
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setCollapsed(body.isVisible());
                    ColorPickerService.getInstance().setCollapsed(key, !body.isVisible());
                }
            });

            body.setBorder(JBUI.Borders.emptyTop(6));
            add(header, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);

            setCollapsed(ColorPickerService.getInstance().isCollapsed(key));
        }

        private void setCollapsed(boolean collapsed) {
            body.setVisible(!collapsed);
            arrow.setIcon(collapsed ? UIUtil.getTreeCollapsedIcon() : UIUtil.getTreeExpandedIcon());
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = JBUI.scale(ROW_PREF_WIDTH);
            return d;
        }
    }

    /** Large rounded preview of the current color. */
    private static final class Swatch extends JComponent {
        private Color color = DEFAULT_COLOR;

        Swatch() {
            Dimension size = new Dimension(JBUI.scale(120), JBUI.scale(72));
            setPreferredSize(size);
            // A real minimum keeps the swatch visible when the tool window is
            // narrow and layouts fall back to minimum sizes.
            setMinimumSize(new Dimension(JBUI.scale(80), JBUI.scale(72)));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRounded(g, this, color, JBUI.scale(12));
        }
    }

    /** A slim conversions row: label, monospace value, copy on click. */
    private final class ConvRow extends JPanel {
        private final JBLabel valueLabel = new JBLabel();
        private final JBLabel hint = new JBLabel(" ");
        private final Timer resetTimer;
        private boolean copied;

        ConvRow(String label) {
            super(new BorderLayout(JBUI.scale(8), 0));
            setOpaque(false);
            setBorder(JBUI.Borders.empty(3, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JBLabel keyLabel = new JBLabel(label);
            keyLabel.setFont(JBFont.small());
            keyLabel.setForeground(UIUtil.getContextHelpForeground());
            keyLabel.setPreferredSize(new Dimension(JBUI.scale(70), keyLabel.getPreferredSize().height));

            valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().getSize()));

            hint.setFont(JBFont.small());
            hint.setForeground(UIUtil.getContextHelpForeground());

            add(keyLabel, BorderLayout.WEST);
            add(valueLabel, BorderLayout.CENTER);
            add(hint, BorderLayout.EAST);

            resetTimer = new Timer(900, e -> {
                copied = false;
                hint.setText(" ");
            });
            resetTimer.setRepeats(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    String value = valueLabel.getText();
                    if (value != null && !value.isEmpty()) {
                        copyToClipboard(value);
                        copied = true;
                        hint.setText("Copied");
                        resetTimer.restart();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!copied) {
                        hint.setText("Copy");
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!copied) {
                        hint.setText(" ");
                    }
                }
            });
        }

        void setValue(String value) {
            valueLabel.setText(value);
            valueLabel.setToolTipText(value);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = JBUI.scale(ROW_PREF_WIDTH);
            return d;
        }
    }

    /** One channel bar: key, a filled track showing the share, and the value. */
    private static final class ChannelBar extends JPanel {
        private final Track track = new Track();
        private final JBLabel valueLabel = new JBLabel("", SwingConstants.RIGHT);

        ChannelBar(String key) {
            super(new BorderLayout(JBUI.scale(8), 0));
            setOpaque(false);

            JBLabel keyLabel = new JBLabel(key);
            keyLabel.setFont(JBFont.small().deriveFont(Font.BOLD));
            keyLabel.setForeground(UIUtil.getContextHelpForeground());
            keyLabel.setPreferredSize(new Dimension(JBUI.scale(14), keyLabel.getPreferredSize().height));

            valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().getSize()));
            valueLabel.setPreferredSize(new Dimension(JBUI.scale(40), valueLabel.getPreferredSize().height));

            add(keyLabel, BorderLayout.WEST);
            add(track, BorderLayout.CENTER);
            add(valueLabel, BorderLayout.EAST);
        }

        void update(String text, double pct, Color fill) {
            valueLabel.setText(text);
            track.set(pct, fill);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = JBUI.scale(ROW_PREF_WIDTH);
            return d;
        }

        private static final class Track extends JComponent {
            private double pct;
            private Color fill = DEFAULT_COLOR;

            Track() {
                setPreferredSize(new Dimension(JBUI.scale(60), JBUI.scale(14)));
            }

            void set(double pct, Color fill) {
                this.pct = Math.max(0, Math.min(100, pct));
                this.fill = fill;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = JBUI.scale(6);
                int h = JBUI.scale(10);
                int y = (getHeight() - h) / 2;
                // The empty part of the tube shows the editor scheme's background.
                g2.setColor(editorBackground());
                g2.fillRoundRect(0, y, getWidth() - 1, h - 1, arc, arc);
                int w = (int) Math.round((getWidth() - 2) * pct / 100.0);
                if (w > 0) {
                    g2.setColor(fill);
                    g2.fillRoundRect(1, y + 1, Math.max(w, JBUI.scale(4)), h - 2, arc, arc);
                }
                g2.setColor(JBColor.border());
                g2.drawRoundRect(0, y, getWidth() - 1, h - 1, arc, arc);
                g2.dispose();
            }
        }
    }

    /**
     * One readability check: an "Aa" sample of the text color on its surface,
     * the WCAG 2 ratio, and AA / AAA / Fail plus APCA badges.
     */
    private static final class ContrastRow extends JPanel {
        private final Sample sample = new Sample();
        private final JBLabel ratioLabel = new JBLabel();
        private final JBLabel normalBadge = new JBLabel();
        private final JBLabel largeBadge = new JBLabel();
        private final JBLabel apcaBadge = new JBLabel();

        private final JBLabel title;

        ContrastRow(String label) {
            super(new BorderLayout(JBUI.scale(10), 0));
            // The row sits on the editor scheme's background (painted below), so
            // the panel itself must stay transparent.
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor.border(), 1, true),
                    JBUI.Borders.empty(6, 10)));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            title = new JBLabel(label);
            title.setFont(JBFont.small().deriveFont(Font.BOLD));
            ratioLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().getSize()));
            text.add(title);
            text.add(ratioLabel);

            JPanel badges = new JPanel();
            badges.setOpaque(false);
            badges.setLayout(new BoxLayout(badges, BoxLayout.Y_AXIS));
            for (JBLabel badge : new JBLabel[]{normalBadge, largeBadge, apcaBadge}) {
                badge.setFont(JBFont.small());
                badge.setAlignmentX(RIGHT_ALIGNMENT);
                badges.add(badge);
            }

            add(sample, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(badges, BorderLayout.EAST);
        }

        void update(Color text, Color surface) {
            sample.set(text, surface);
            setToolTipText("Text " + ColorFormats.toHex(text) + " on " + ColorFormats.toHex(surface));
            // The row is painted on the editor background, so its own text
            // follows the editor scheme's foreground to stay readable.
            title.setForeground(editorForeground());
            ratioLabel.setForeground(editorForeground());
            double ratio = Wcag.contrastRatio(text, surface);
            ratioLabel.setText(String.format("%.2f:1", ratio));
            setBadge(normalBadge, "Normal · " + Wcag.normalTextLevel(ratio), Wcag.normalTextLevel(ratio));
            setBadge(largeBadge, "Large · " + Wcag.largeTextLevel(ratio), Wcag.largeTextLevel(ratio));
            int apca = Wcag.apcaContrast(text, surface);
            String apcaLevel = Wcag.apcaLevel(apca);
            setBadge(apcaBadge, "APCA Lc " + apca + " · " + apcaLevel,
                    "Fail".equals(apcaLevel) ? "Fail" : "Body text".equals(apcaLevel) ? "AAA" : "AA");
            repaint();
        }

        private static void setBadge(JBLabel badge, String text, String level) {
            badge.setText(text);
            badge.setForeground("AAA".equals(level) ? LEVEL_AAA : "AA".equals(level) ? LEVEL_AA : LEVEL_FAIL);
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRounded(g, this, editorBackground(), JBUI.scale(6));
            super.paintComponent(g);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = JBUI.scale(ROW_PREF_WIDTH);
            return d;
        }

        /** "Aa" rendered in the text color on the surface being tested. */
        private static final class Sample extends JComponent {
            private Color color = DEFAULT_COLOR;
            private Color surface = Color.WHITE;

            Sample() {
                setPreferredSize(new Dimension(JBUI.scale(42), JBUI.scale(34)));
                setMinimumSize(new Dimension(JBUI.scale(42), JBUI.scale(34)));
                setFont(JBFont.label().deriveFont(Font.BOLD));
            }

            void set(Color color, Color surface) {
                this.color = color;
                this.surface = surface;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                paintRounded(g, this, surface, JBUI.scale(6));
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setFont(getFont());
                var fm = g2.getFontMetrics();
                String s = "Aa";
                g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    /** A titled, wrapping row of clickable color chips. */
    private final class ChipStrip extends JPanel {
        private final JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(5), JBUI.scale(5)));

        ChipStrip(String label) {
            super(new BorderLayout());
            setOpaque(false);
            chips.setOpaque(false);
            JBLabel title = new JBLabel(label);
            title.setFont(JBFont.small());
            title.setForeground(UIUtil.getContextHelpForeground());
            add(title, BorderLayout.NORTH);
            add(chips, BorderLayout.CENTER);
        }

        void setColors(Color[] colors) {
            setColors(colors, null);
        }

        /** Optional note rendered after the chips (e.g. the Warm / Cool verdict). */
        void setColors(Color[] colors, @Nullable String note) {
            chips.removeAll();
            for (Color color : colors) {
                chips.add(new Chip(color));
            }
            if (note != null) {
                JBLabel noteLabel = new JBLabel(note);
                noteLabel.setFont(JBFont.small());
                chips.add(noteLabel);
            }
            chips.revalidate();
            chips.repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = JBUI.scale(ROW_PREF_WIDTH);
            return d;
        }
    }

    /** A small color square; click to load the color into the panel. */
    private final class Chip extends JComponent {
        private final Color color;

        Chip(Color color) {
            this.color = color;
            int size = JBUI.scale(20);
            setPreferredSize(new Dimension(size, size));
            setToolTipText(ColorFormats.toHex(color));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ColorPickerPanel.this.show(color);
                    commitTimer.restart();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRounded(g, this, color, JBUI.scale(6));
        }
    }
}
