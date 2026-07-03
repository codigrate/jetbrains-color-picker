package com.codigrate.colorpicker;

import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The plugin's own screen eyedropper, free of internal platform API: it
 * freezes every display into a screenshot, shows the captures fullscreen with
 * a magnifier following the cursor, and picks the pixel under a click.
 * Escape or a right click cancels.
 */
final class ScreenPipette {

    interface Callback {
        void picked(Color color);

        default void hovered(Color color) {
        }

        default void cancelled() {
        }
    }

    private ScreenPipette() {
    }

    static void pick(Callback callback) {
        List<PipetteWindow> windows = new ArrayList<>();
        try {
            Robot robot = new Robot();
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                BufferedImage capture = robot.createScreenCapture(bounds);
                windows.add(new PipetteWindow(bounds, capture, windows, callback));
            }
        } catch (AWTException | SecurityException e) {
            for (PipetteWindow window : windows) {
                window.dispose();
            }
            callback.cancelled();
            return;
        }
        for (PipetteWindow window : windows) {
            window.setVisible(true);
        }
        if (!windows.isEmpty()) {
            windows.get(0).requestFocus();
        }
    }

    /** One fullscreen frozen capture of a display, with the magnifier overlay. */
    private static final class PipetteWindow extends JWindow {
        private static final int ZOOM_PIXELS = 11;
        private static final int ZOOM_SCALE = 8;

        private final BufferedImage capture;
        private final List<PipetteWindow> group;
        private final Callback callback;
        private Point cursor;

        PipetteWindow(Rectangle bounds, BufferedImage capture, List<PipetteWindow> group, Callback callback) {
            this.capture = capture;
            this.group = group;
            this.callback = callback;

            setAlwaysOnTop(true);
            setFocusableWindowState(true);
            setBounds(bounds);

            JComponent content = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    paintCapture((Graphics2D) g, getWidth(), getHeight());
                }
            };
            content.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setContentPane(content);

            content.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    cursor = e.getPoint();
                    callback.hovered(colorAt(e.getPoint()));
                    content.repaint();
                }
            });
            content.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        Color picked = colorAt(e.getPoint());
                        closeAll();
                        callback.picked(picked);
                    } else {
                        closeAll();
                        callback.cancelled();
                    }
                }
            });
            content.registerKeyboardAction(e -> {
                closeAll();
                callback.cancelled();
            }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        private void closeAll() {
            for (PipetteWindow window : group) {
                window.dispose();
            }
        }

        private Color colorAt(Point p) {
            int x = Math.max(0, Math.min(capture.getWidth() - 1, p.x * capture.getWidth() / Math.max(1, getWidth())));
            int y = Math.max(0, Math.min(capture.getHeight() - 1, p.y * capture.getHeight() / Math.max(1, getHeight())));
            return new Color(capture.getRGB(x, y));
        }

        private void paintCapture(Graphics2D g2, int width, int height) {
            g2.drawImage(capture, 0, 0, width, height, null);
            if (cursor == null) {
                return;
            }

            // Magnifier: a zoomed pixel grid beside the cursor with the hex below.
            int size = ZOOM_PIXELS * ZOOM_SCALE;
            int mx = cursor.x + 24 + size <= width ? cursor.x + 24 : cursor.x - 24 - size;
            int my = cursor.y + 24 + size + 22 <= height ? cursor.y + 24 : cursor.y - 24 - size - 22;

            int srcX = cursor.x * capture.getWidth() / Math.max(1, width);
            int srcY = cursor.y * capture.getHeight() / Math.max(1, height);
            int half = ZOOM_PIXELS / 2;

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(capture,
                    mx, my, mx + size, my + size,
                    srcX - half, srcY - half, srcX + half + 1, srcY + half + 1, null);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mx, my, size, size);
            // The center cell being sampled.
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(1));
            g2.drawRect(mx + half * ZOOM_SCALE, my + half * ZOOM_SCALE, ZOOM_SCALE, ZOOM_SCALE);

            Color color = colorAt(cursor);
            String hex = ColorFormats.toHex(color);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, JBUI.scale(12)));
            var fm = g2.getFontMetrics();
            int tw = fm.stringWidth(hex);
            int tx = mx + (size - tw) / 2;
            int ty = my + size + fm.getAscent() + 4;
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRoundRect(tx - 6, my + size + 2, tw + 12, fm.getHeight() + 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(hex, tx, ty);
        }
    }
}
