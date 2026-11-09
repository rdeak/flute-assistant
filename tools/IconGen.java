// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Usage (JDK 11+), from the repository root: java -Djava.awt.headless=true tools/IconGen.java <output dir>
 * Then copy the mipmap-DENSITY/ic_launcher*.png files into app/src/main/res and play-listing/* into docs/release.
 *
 * Renders the Flute Assistant launcher icon to PNG at every density, plus the Play Store
 * listing assets. The picture is the app icon (the Android robot playing a flute, in a
 * 512 x 512 box) with the same shapes as @drawable/ic_app_art; the round legacy
 * icon places it as the adaptive icon does (ic_launcher_foreground: inset 23.3 % of the 108dp
 * canvas, launchers show the inner 72dp), so raster and vector icons look alike. The robot is
 * reproduced and modified from work created and shared by Google under the Creative Commons 3.0
 * Attribution License (NOTICE).
 */
public class IconGen {
    static final Color GOLD = new Color(0xFFC857);
    static final Color INK = new Color(0x2E2A44);
    /** The Android robot's green, from the brand guidelines. */
    static final Color GREEN = new Color(0x3DDC84);
    static final Color[] NOTES = {
        new Color(0xFF6B6B), new Color(0xFFA047), new Color(0xFFD93D), new Color(0x7BD67B),
        new Color(0x5FD3C8), new Color(0xB39DDB), new Color(0xF7A1C4),
    };
    static final Color PAGE = new Color(0xEAF4FF);
    static final Color TEXT = new Color(0x1F2440);
    static final Color MUTED = new Color(0x4F5878);
    static final BasicStroke OUTLINE = new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    /** The eyes' thinner outline. */
    static final BasicStroke THIN = new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** The canvas's corner radius on its 512 px icon. */
    static final double CORNER = 114.0 / 512;
    /** ic_launcher_foreground's inset: the 512 box spans this share of the 108dp canvas. */
    static final double ART_IN_CANVAS = 1 - 2 * 0.233;

    static Shape circle(double cx, double cy, double r) { return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }

    static void fillAndOutline(Graphics2D g, Shape s, Color fill) { fillAndOutline(g, s, fill, OUTLINE); }

    static void fillAndOutline(Graphics2D g, Shape s, Color fill, BasicStroke outline) {
        g.setColor(fill); g.fill(s);
        g.setColor(INK); g.setStroke(outline); g.draw(s);
    }

    /** One antenna, from (x0,y0) on the dome outward to (x1,y1): a 22 px green pill with the outline. */
    static void antenna(Graphics2D g, double x0, double y0, double x1, double y1) {
        Shape pill = new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(new Line2D.Double(x0, y0, x1, y1));
        fillAndOutline(g, pill, GREEN);
    }

    /** An eye: a white circle of radius r with the thin outline and a pupil offset by (px,py). */
    static void eye(Graphics2D g, double cx, double cy, double r, double px, double py) {
        fillAndOutline(g, circle(cx, cy, r), Color.WHITE, THIN);
        g.setColor(INK); g.fill(circle(cx + px, cy + py, r * 0.42));
    }

    /** Draws the picture in its 512 x 512 box, with no background: the shapes of @drawable/ic_app_art. */
    static void art(Graphics2D g) {
        // Body: square shoulders from 248 down to 426, 56 px bottom corners.
        Path2D body = new Path2D.Double();
        body.moveTo(94, 248); body.lineTo(286, 248); body.lineTo(286, 370);
        body.quadTo(286, 426, 230, 426); body.lineTo(150, 426); body.quadTo(94, 426, 94, 370); body.closePath();
        fillAndOutline(g, body, GREEN);
        // Antennas, 28 degrees off vertical, from 98 to 150 px out of the dome's centre (190,236).
        antenna(g, 144, 149.5, 119.6, 103.6); antenna(g, 236, 149.5, 260.4, 103.6);
        // Head: a dome of radius 104 about (190,236).
        fillAndOutline(g, new Arc2D.Double(86, 132, 208, 208, 0, 180, Arc2D.CHORD), GREEN);
        eye(g, 148, 188, 17, 6, 4); eye(g, 232, 188, 17, 6, 4);

        Graphics2D f = (Graphics2D) g.create();
        // The flute: a white pill from the face outward, tilted 3 degrees, one dot per note letter, C to B,
        // and a round hand over the keys at each side.
        f.translate(340, 222); f.rotate(Math.toRadians(3));
        fillAndOutline(f, new RoundRectangle2D.Double(-150, -20, 300, 40, 40, 40), Color.WHITE);
        for (int i = 0; i < NOTES.length; i++) { f.setColor(NOTES[i]); f.fill(circle((i - 3) * 26, 0, 9)); }
        fillAndOutline(f, circle(-52, 6, 26), GREEN); fillAndOutline(f, circle(62, 6, 26), GREEN);
        f.dispose();

        Graphics2D n = (Graphics2D) g.create();
        // One eighth note in the air, tilted 12 degrees.
        n.rotate(Math.toRadians(12), 436, 120);
        n.setColor(INK); n.setStroke(OUTLINE);
        n.draw(new Line2D.Double(448, 140, 448, 70));
        n.draw(new QuadCurve2D.Double(448, 70, 478, 84, 478, 118));
        n.fill(new Ellipse2D.Double(420, 128, 40, 30));
        n.dispose();
    }

    /** The whole icon as the top bar shows it: the picture on a rounded golden square, [size] px. */
    static void tile(Graphics2D g, double x, double y, double size) {
        g.setColor(GOLD);
        g.fill(new RoundRectangle2D.Double(x, y, size, size, size * CORNER * 2, size * CORNER * 2));
        Graphics2D a = (Graphics2D) g.create();
        a.translate(x, y); a.scale(size / 512, size / 512);
        art(a); a.dispose();
    }

    static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /**
     * Legacy launcher icon (Android 7). The square one is the icon as designed, with 2dp of
     * breathing room; the round one masks the inner 72dp of the adaptive canvas to a circle.
     */
    static BufferedImage legacy(int size, boolean round) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        if (!round) {
            double margin = size * 2.0 / 48;
            tile(g, margin, margin, size - 2 * margin);
        } else {
            g.setColor(GOLD); g.fill(new Ellipse2D.Double(0, 0, size, size));
            double art = size * 108.0 / 72 * ART_IN_CANVAS; // the 512 box, as the adaptive icon places it
            Graphics2D a = (Graphics2D) g.create();
            a.translate((size - art) / 2, (size - art) / 2); a.scale(art / 512, art / 512);
            art(a); a.dispose();
        }
        g.dispose();
        return img;
    }

    /** Play Store icon: full-bleed square, Play applies its own rounding. */
    static BufferedImage playIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setColor(GOLD); g.fillRect(0, 0, size, size);
        g.scale(size / 512.0, size / 512.0);
        art(g);
        g.dispose();
        return img;
    }

    /** The app's sky-blue page with the icon tile on the left, the name and one line beside it. */
    static BufferedImage featureGraphic() {
        int w = 1024, h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img);
        g.setColor(PAGE); g.fillRect(0, 0, w, h);
        int tile = 300, tx = 90, ty = (h - tile) / 2;
        tile(g, tx, ty, tile);
        int textX = 440, maxWidth = w - textX - 60;
        g.setColor(TEXT);
        g.setFont(fit(g, "Flute Assistant", nunito("nunito_bold"), 86, maxWidth));
        g.drawString("Flute Assistant", textX, 222);
        g.setColor(MUTED);
        String subtitle = "Flute, metronome, tuner and notes"; // the tab bar's order
        g.setFont(fit(g, subtitle, nunito("nunito_semibold"), 38, maxWidth));
        g.drawString(subtitle, textX + 2, 284);
        // The flute's seven note colours, as on the icon.
        for (int i = 0; i < NOTES.length; i++) {
            g.setColor(NOTES[i]); g.fill(circle(textX + 18 + 44 * i, 342, 14));
        }
        g.dispose();
        return img;
    }

    /** The app's font from res/font, falling back to the system sans-serif. */
    static Font nunito(String name) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/" + name + ".ttf"));
        } catch (Exception e) {
            return new Font(Font.SANS_SERIF, Font.BOLD, 12);
        }
    }

    /** [base] at the largest size (stepping down from maxSize) at which the text fits in maxWidth. */
    static Font fit(Graphics2D g, String text, Font base, int maxSize, int maxWidth) {
        for (int size = maxSize; size > 12; size -= 2) {
            Font f = base.deriveFont((float) size);
            if (g.getFontMetrics(f).stringWidth(text) <= maxWidth) return f;
        }
        return base.deriveFont(12f);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int[][] densities = {{48, 0}, {72, 1}, {96, 2}, {144, 3}, {192, 4}};
        String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int[] d : densities) {
            File dir = new File(out, "mipmap-" + names[d[1]]); dir.mkdirs();
            ImageIO.write(legacy(d[0], false), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(legacy(d[0], true), "png", new File(dir, "ic_launcher_round.png"));
        }
        File listing = new File(out, "play-listing"); listing.mkdirs();
        ImageIO.write(playIcon(512), "png", new File(listing, "icon-512.png"));
        ImageIO.write(featureGraphic(), "png", new File(listing, "feature-graphic-1024x500.png"));
        ImageIO.write(legacy(512, false), "png", new File(out, "preview-square.png"));
        ImageIO.write(legacy(512, true), "png", new File(out, "preview-round.png"));
        System.out.println("icons written to " + out);
    }
}
