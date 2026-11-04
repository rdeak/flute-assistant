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
 * listing assets. The picture is the canvas's app icon (a robot over a flute, in a 512 x 512
 * box) with the same shapes as @drawable/ic_app_art; the round legacy icon places it as the
 * adaptive icon does (ic_launcher_foreground: inset 23.3 % of the 108dp canvas, launchers show
 * the inner 72dp), so raster and vector icons look alike.
 */
public class IconGen {
    static final Color GOLD = new Color(0xFFC857);
    static final Color INK = new Color(0x2E2A44);
    static final Color HEAD = new Color(0xE3EBF3);
    static final Color EARS = new Color(0xFF9F68);
    static final Color BULB = new Color(0xFF7A6B);
    static final Color FACE = new Color(0x7BE8D8);
    static final Color[] NOTES = {
        new Color(0xFF6B6B), new Color(0xFFA047), new Color(0xFFD93D), new Color(0x7BD67B),
        new Color(0x5FD3C8), new Color(0xB39DDB), new Color(0xF7A1C4),
    };
    static final Color PAGE = new Color(0xEAF4FF);
    static final Color TEXT = new Color(0x1F2440);
    static final Color MUTED = new Color(0x4F5878);
    static final BasicStroke OUTLINE = new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** The canvas's corner radius on its 512 px icon. */
    static final double CORNER = 114.0 / 512;
    /** ic_launcher_foreground's inset: the 512 box spans this share of the 108dp canvas. */
    static final double ART_IN_CANVAS = 1 - 2 * 0.233;

    static Shape circle(double cx, double cy, double r) { return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }

    static void fillAndOutline(Graphics2D g, Shape s, Color fill) {
        g.setColor(fill); g.fill(s);
        g.setColor(INK); g.setStroke(OUTLINE); g.draw(s);
    }

    /** Draws the picture in its 512 x 512 box, with no background. */
    static void art(Graphics2D g) {
        Graphics2D h = (Graphics2D) g.create();
        // The robot: rotate(-9 about 256,216), then scale 1.1 about 256,196 and down 40, as on the canvas.
        h.translate(256, 236); h.scale(1.1, 1.1); h.translate(-256, -196);
        h.rotate(Math.toRadians(-9), 256, 216);
        h.setColor(INK); h.setStroke(OUTLINE); h.draw(new Line2D.Double(256, 112, 256, 84));
        fillAndOutline(h, circle(256, 68, 20), BULB);
        fillAndOutline(h, circle(116, 216, 36), EARS);
        fillAndOutline(h, circle(396, 216, 36), EARS);
        fillAndOutline(h, new Ellipse2D.Double(256 - 132, 216 - 104, 264, 208), HEAD);
        h.setColor(INK); h.fill(new Ellipse2D.Double(256 - 94, 214 - 64, 188, 128));
        h.setColor(FACE); h.fill(circle(216, 200, 18)); h.fill(circle(296, 200, 18));
        h.setStroke(OUTLINE); h.draw(new QuadCurve2D.Double(224, 238, 256, 268, 288, 238));
        h.dispose();

        Graphics2D f = (Graphics2D) g.create();
        // The flute: a white pill with one dot per note letter, C to B, tilted -6 degrees.
        f.translate(256, 428); f.rotate(Math.toRadians(-6));
        fillAndOutline(f, new RoundRectangle2D.Double(-190, -22, 380, 44, 44, 44), Color.WHITE);
        for (int i = 0; i < NOTES.length; i++) { f.setColor(NOTES[i]); f.fill(circle(-96 + 32 * i, 0, 10)); }
        f.dispose();
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
