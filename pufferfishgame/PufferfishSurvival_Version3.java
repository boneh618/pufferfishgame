import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.awt.geom.*;
import java.io.*;

public class PufferfishSurvival_Version3 extends JPanel implements ActionListener, KeyListener {

    // Window
    static final int WIDTH = 900, HEIGHT = 650;
    Timer timer = new Timer(16, this);

    // Highscore
    int highscore = 0;
    final String HIGHSCORE_FILE = "highscore.txt";

    // Game state
    enum State { MENU, PLAYING, GAME_OVER }
    State state = State.MENU;

    // Global tick for animations
    long tick = 0;

    // Screen shake
    int shakeFrames = 0;
    int shakeIntensity = 0;

    // Combo system
    int combo = 0;
    int comboTimer = 0;

    // Flash on damage
    int damageFlash = 0;

    // Particles
    java.util.List<Particle> particles = new ArrayList<>();

    // Floating score popups
    java.util.List<ScorePopup> popups = new ArrayList<>();

    // ========== INNER CLASSES ==========

    class Pufferfish {
        double x = WIDTH / 2.0, y = HEIGHT - 100;
        int radius = 18;
        int lives = 3;
        int score = 0;
        boolean puffed = false;
        int puffCooldown = 0;
        // Dash
        boolean dashing = false;
        int dashTimer = 0;
        int dashCooldown = 0;
        double dashDx = 0, dashDy = 0;
        // Invincibility frames after hit
        int iFrames = 0;
        // Shield power-up
        boolean shielded = false;
        int shieldTimer = 0;
        // Speed boost power-up
        boolean speedBoosted = false;
        int speedTimer = 0;

        void move(int mdx, int mdy) {
            if (dashing) return; // dash overrides normal movement
            double speed = puffed ? 1.5 : 3.5;
            if (speedBoosted) speed *= 1.8;
            x = Math.max(radius, Math.min(WIDTH - radius, x + mdx * speed));
            y = Math.max(radius + 60, Math.min(HEIGHT - radius, y + mdy * speed));
        }

        void update() {
            if (puffCooldown > 0) puffCooldown--;
            else puffed = false;
            if (iFrames > 0) iFrames--;
            if (dashCooldown > 0) dashCooldown--;
            if (shieldTimer > 0) { shieldTimer--; if (shieldTimer == 0) shielded = false; }
            if (speedTimer > 0) { speedTimer--; if (speedTimer == 0) speedBoosted = false; }
            // Dash movement
            if (dashing) {
                x += dashDx * 12;
                y += dashDy * 12;
                x = Math.max(radius, Math.min(WIDTH - radius, x));
                y = Math.max(radius + 60, Math.min(HEIGHT - radius, y));
                dashTimer--;
                if (dashTimer <= 0) dashing = false;
            }
        }
    }

    class Enemy {
        double x, y, speed;
        int dir;
        String pattern;
        int time = 0;
        int type; // 0=normal, 1=fast, 2=big
        double baseY;
        Enemy(double y, double speed, String pattern, int dir, int type) {
            this.y = y;
            this.baseY = y;
            this.speed = speed;
            this.pattern = pattern;
            this.dir = dir;
            this.type = type;
            this.x = dir == 1 ? -30 : WIDTH + 30;
        }
        void update() {
            x += dir * speed;
            if (pattern.equals("sine"))
                y = baseY + 40 * Math.sin(time / 18.0);
            else if (pattern.equals("zigzag"))
                y = baseY + 30 * ((time / 30 % 2 == 0) ? 1 : -1);
            time++;
        }
        int getRadius() {
            return type == 2 ? 22 : (type == 1 ? 12 : 16);
        }
        int getScore() {
            return type == 2 ? 25 : (type == 1 ? 15 : 10);
        }
    }

    class Spike {
        double x, y;
        boolean moving;
        int time = 0;
        double baseY;
        Spike(double x, double y, boolean moving) {
            this.x = x; this.y = y; this.baseY = y; this.moving = moving;
        }
        void update() {
            if (moving) {
                y = baseY + 18 * Math.sin(time / 12.0);
                time++;
            }
        }
    }

    class Collectible {
        double x, y;
        int type; // 0=food, 1=shield, 2=speed, 3=extralife
        int time = 0;
        boolean collected = false;
        Collectible(double x, double y, int type) {
            this.x = x; this.y = y; this.type = type;
        }
        void update() { time++; }
    }

    class Particle {
        double x, y, vx, vy;
        int life, maxLife;
        Color color;
        Particle(double x, double y, double vx, double vy, int life, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life; this.color = color;
        }
        void update() { x += vx; y += vy; vy += 0.05; life--; }
    }

    class ScorePopup {
        double x, y;
        String text;
        int life = 40;
        Color color;
        ScorePopup(double x, double y, String text, Color color) {
            this.x = x; this.y = y; this.text = text; this.color = color;
        }
        void update() { y -= 1.2; life--; }
    }

    // Seaweed positions (generated once)
    double[] seaweedX;
    double[] seaweedH;

    Pufferfish p = new Pufferfish();
    java.util.List<Enemy> enemies = new ArrayList<>();
    java.util.List<Spike> spikes = new ArrayList<>();
    java.util.List<Collectible> collectibles = new ArrayList<>();
    int dx = 0, dy = 0;
    int wave = 1, spawnCd = 0, collectCd = 0;
    boolean newHighscore = false;
    Random rand = new Random();

    public PufferfishSurvival_Version3() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        loadHighscore();
        // Generate seaweed
        seaweedX = new double[12];
        seaweedH = new double[12];
        for (int i = 0; i < 12; i++) {
            seaweedX[i] = 30 + (WIDTH - 60.0) * i / 11;
            seaweedH[i] = 50 + rand.nextInt(60);
        }
        timer.start();
    }

    // ========== DRAWING ==========

    @Override
    public void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Screen shake offset
        int sx = 0, sy = 0;
        if (shakeFrames > 0) {
            sx = (int)(Math.random() * shakeIntensity * 2 - shakeIntensity);
            sy = (int)(Math.random() * shakeIntensity * 2 - shakeIntensity);
            g.translate(sx, sy);
        }

        drawBackground(g);

        if (state == State.MENU) {
            drawMenu(g);
        } else {
            drawSeaweed(g);
            drawCollectibles(g);
            drawSpikes(g);
            drawEnemies(g);
            drawParticles(g);
            drawPufferfish(g);
            drawPopups(g);
            drawHUD(g);
            if (damageFlash > 0) {
                g.setColor(new Color(255, 50, 50, Math.min(damageFlash * 8, 120)));
                g.fillRect(-20, -20, WIDTH + 40, HEIGHT + 40);
            }
            if (state == State.GAME_OVER) {
                drawGameOver(g);
            }
        }

        if (shakeFrames > 0) g.translate(-sx, -sy);
    }

    void drawBackground(Graphics2D g) {
        // Deep ocean gradient
        GradientPaint ocean = new GradientPaint(0, 0, new Color(30, 130, 230), 0, HEIGHT, new Color(8, 30, 70));
        g.setPaint(ocean);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Animated light rays from surface
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
        for (int i = 0; i < 6; i++) {
            int rx = (int)(120 + i * 140 + 30 * Math.sin(tick / 80.0 + i));
            Polygon ray = new Polygon();
            ray.addPoint(rx - 10, 0);
            ray.addPoint(rx + 10, 0);
            ray.addPoint(rx + 60 + i * 10, HEIGHT);
            ray.addPoint(rx - 60 - i * 10, HEIGHT);
            g.setColor(new Color(180, 220, 255));
            g.fillPolygon(ray);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // Animated bubbles background
        for (int i = 0; i < 40; i++) {
            int bx = (i * 53 + 17) % WIDTH;
            double phase = tick / 60.0 + i * 1.3;
            int by = (int)(HEIGHT - (phase * 40 % (HEIGHT + 40)));
            int size = 4 + (i % 5) * 2;
            g.setColor(new Color(200, 230, 255, 30 + (i % 3) * 15));
            g.fillOval(bx, by, size, size);
        }

        // Animated wave surface
        g.setColor(new Color(100, 200, 255, 60));
        for (int x = 0; x < WIDTH; x += 2) {
            int wy = (int)(48 + 6 * Math.sin(x / 40.0 + tick / 25.0) + 3 * Math.sin(x / 20.0 - tick / 15.0));
            g.fillRect(x, 0, 2, wy);
        }

        // Sandy bottom
        GradientPaint sand = new GradientPaint(0, HEIGHT - 40, new Color(194, 178, 128, 80), 0, HEIGHT, new Color(160, 140, 100, 120));
        g.setPaint(sand);
        g.fillRect(0, HEIGHT - 40, WIDTH, 40);
        // Sand ripples
        g.setColor(new Color(170, 155, 110, 50));
        for (int i = 0; i < WIDTH; i += 30) {
            int sw = 20 + (i * 7) % 15;
            g.fillOval(i, HEIGHT - 25 + (i * 3) % 10, sw, 6);
        }
    }

    void drawSeaweed(Graphics2D g) {
        for (int i = 0; i < seaweedX.length; i++) {
            double sx = seaweedX[i];
            double h = seaweedH[i];
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int seg = 0; seg < 5; seg++) {
                double t = seg / 5.0;
                double sway = 8 * Math.sin(tick / 30.0 + i + seg * 0.5);
                int x1 = (int)(sx + sway * t);
                int y1 = (int)(HEIGHT - 40 - h * t);
                double t2 = (seg + 1) / 5.0;
                double sway2 = 8 * Math.sin(tick / 30.0 + i + (seg + 1) * 0.5);
                int x2 = (int)(sx + sway2 * t2);
                int y2 = (int)(HEIGHT - 40 - h * t2);
                int green = 80 + (int)(80 * (1 - t));
                g.setColor(new Color(30, green, 30, 180));
                g.drawLine(x1, y1, x2, y2);
                // Leaf
                if (seg == 2 || seg == 4) {
                    int ldir = (seg + i) % 2 == 0 ? 1 : -1;
                    g.fillOval(x1 + ldir * 4, y1 - 3, 10, 6);
                }
            }
        }
        g.setStroke(new BasicStroke(1));
    }

    void drawCollectibles(Graphics2D g) {
        for (Collectible c : collectibles) {
            int cx = (int) c.x, cy = (int) c.y;
            float bob = (float)(3 * Math.sin(tick / 10.0 + c.x));
            cy += (int) bob;
            // Glow
            g.setColor(new Color(255, 255, 200, 40));
            g.fillOval(cx - 16, cy - 16, 32, 32);

            switch (c.type) {
                case 0: // Food - shrimp
                    g.setColor(new Color(255, 140, 80));
                    g.fillOval(cx - 6, cy - 8, 12, 16);
                    g.setColor(new Color(255, 180, 120));
                    g.fillOval(cx - 4, cy - 6, 8, 10);
                    g.setColor(new Color(255, 100, 50));
                    g.drawArc(cx - 3, cy + 2, 6, 8, 0, 180);
                    break;
                case 1: // Shield
                    g.setColor(new Color(80, 180, 255, 200));
                    g.fillOval(cx - 10, cy - 10, 20, 20);
                    g.setColor(Color.white);
                    g.setFont(new Font("Arial", Font.BOLD, 14));
                    g.drawString("S", cx - 4, cy + 5);
                    break;
                case 2: // Speed
                    g.setColor(new Color(80, 255, 120, 200));
                    g.fillOval(cx - 10, cy - 10, 20, 20);
                    g.setColor(Color.white);
                    g.setFont(new Font("Arial", Font.BOLD, 16));
                    g.drawString(">>", cx - 9, cy + 6);
                    break;
                case 3: // Extra life
                    g.setColor(new Color(255, 80, 120));
                    // Heart shape
                    g.fillOval(cx - 8, cy - 6, 10, 10);
                    g.fillOval(cx - 2, cy - 6, 10, 10);
                    Polygon heart = new Polygon();
                    heart.addPoint(cx - 9, cy);
                    heart.addPoint(cx, cy + 10);
                    heart.addPoint(cx + 9, cy);
                    g.fillPolygon(heart);
                    break;
            }
        }
    }

    void drawSpikes(Graphics2D g) {
        for (Spike s : spikes) {
            int px = (int) s.x, py = (int) s.y;
            // Sea urchin body
            g.setColor(new Color(30, 30, 40));
            for (int j = 0; j < 12; j++) {
                double a = Math.PI * 2 * j / 12 + Math.sin(tick / 20.0) * 0.1;
                int ex = px + (int)(Math.cos(a) * 22);
                int ey = py + (int)(Math.sin(a) * 22);
                g.setStroke(new BasicStroke(2));
                g.drawLine(px, py, ex, ey);
            }
            g.setStroke(new BasicStroke(1));
            // Body
            GradientPaint urchin = new GradientPaint(px - 8, py - 8, new Color(60, 50, 80), px + 8, py + 8, new Color(30, 25, 45));
            g.setPaint(urchin);
            g.fillOval(px - 10, py - 10, 20, 20);
            // Highlight
            g.setColor(new Color(180, 160, 220, 80));
            g.fillOval(px - 5, py - 7, 8, 5);
        }
    }

    void drawEnemies(Graphics2D g) {
        for (Enemy e : enemies) {
            int ex = (int) e.x, ey = (int) e.y;
            int r = e.getRadius();
            int flipDir = e.dir;

            Color bodyColor, tailColor, bellyColor;
            if (e.type == 1) { // Fast - blue
                bodyColor = new Color(50, 100, 200);
                tailColor = new Color(40, 80, 170);
                bellyColor = new Color(140, 180, 240);
            } else if (e.type == 2) { // Big - dark red
                bodyColor = new Color(160, 30, 30);
                tailColor = new Color(130, 20, 20);
                bellyColor = new Color(220, 120, 120);
            } else { // Normal - red/orange
                bodyColor = new Color(220, 70, 40);
                tailColor = new Color(190, 50, 30);
                bellyColor = new Color(250, 180, 140);
            }

            // Tail
            g.setColor(tailColor);
            Polygon tail = new Polygon();
            tail.addPoint(ex - flipDir * r, ey);
            tail.addPoint(ex - flipDir * (r + 14), ey - 10);
            tail.addPoint(ex - flipDir * (r + 14), ey + 10);
            g.fillPolygon(tail);

            // Body
            GradientPaint bodyGrad = new GradientPaint(ex - r, ey - r / 2, bodyColor, ex + r, ey + r / 2, bellyColor);
            g.setPaint(bodyGrad);
            g.fillOval(ex - r, ey - r * 2 / 3, r * 2, r * 4 / 3);

            // Fin on top
            g.setColor(tailColor);
            Polygon fin = new Polygon();
            fin.addPoint(ex - 4, ey - r * 2 / 3);
            fin.addPoint(ex + 4, ey - r * 2 / 3);
            fin.addPoint(ex, ey - r - 6);
            g.fillPolygon(fin);

            // Eye
            g.setColor(Color.white);
            int eyeOff = flipDir * (r / 2 + 2);
            g.fillOval(ex + eyeOff - 5, ey - 5, 10, 10);
            g.setColor(Color.black);
            g.fillOval(ex + eyeOff - 2 + flipDir * 2, ey - 2, 5, 5);
            // Angry eyebrow
            g.setStroke(new BasicStroke(2));
            g.setColor(new Color(80, 20, 20));
            g.drawLine(ex + eyeOff - 5, ey - 6 - flipDir, ex + eyeOff + 5, ey - 8 + flipDir);
            g.setStroke(new BasicStroke(1));

            // Mouth
            g.setColor(new Color(120, 30, 30));
            g.drawArc(ex + flipDir * 4 - 4, ey + 2, 8, 4, 0, 180);
        }
    }

    void drawPufferfish(Graphics2D g) {
        int radius = p.puffed ? 28 : p.radius;
        int px = (int) p.x, py = (int) p.y;

        // Blink invisible during i-frames
        if (p.iFrames > 0 && (tick / 3) % 2 == 0) {
            // Draw ghost outline
            g.setColor(new Color(254, 224, 97, 60));
            g.drawOval(px - radius - 2, py - radius - 2, (radius + 2) * 2, (radius + 2) * 2);
            return;
        }

        // Shield visual
        if (p.shielded) {
            g.setColor(new Color(80, 200, 255, (int)(40 + 20 * Math.sin(tick / 5.0))));
            g.fillOval(px - radius - 10, py - radius - 10, (radius + 10) * 2, (radius + 10) * 2);
            g.setColor(new Color(120, 220, 255, 100));
            g.setStroke(new BasicStroke(2));
            g.drawOval(px - radius - 8, py - radius - 8, (radius + 8) * 2, (radius + 8) * 2);
            g.setStroke(new BasicStroke(1));
        }

        // Speed boost trail
        if (p.speedBoosted) {
            for (int i = 1; i <= 3; i++) {
                g.setColor(new Color(100, 255, 150, 60 - i * 15));
                g.fillOval(px - radius + i * dx * 6, py - radius + i * dy * 6, radius * 2, radius * 2);
            }
        }

        // Spines when puffed
        if (p.puffed) {
            for (int j = 0; j < 20; j++) {
                double a = Math.PI * 2 * j / 20 + Math.sin(tick / 8.0) * 0.05;
                int spx = px + (int)(Math.cos(a) * (radius + 10));
                int spy = py + (int)(Math.sin(a) * (radius + 10));
                g.setColor(new Color(210, 170, 20));
                g.setStroke(new BasicStroke(2));
                g.drawLine(px + (int)(Math.cos(a) * radius), py + (int)(Math.sin(a) * radius), spx, spy);
            }
            g.setStroke(new BasicStroke(1));
        }

        // Body
        GradientPaint bodyGrad = new GradientPaint(px - radius, py - radius, new Color(255, 235, 110),
                px + radius, py + radius, new Color(230, 190, 50));
        g.setPaint(bodyGrad);
        g.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        // Belly
        g.setColor(new Color(255, 245, 200, 160));
        g.fillOval(px - radius / 2, py - radius / 4, radius, radius * 3 / 4);

        // Eyes
        int eyeSpacing = radius / 3;
        // Left eye
        g.setColor(Color.white);
        g.fillOval(px + eyeSpacing - 1, py - 9, 13, 13);
        g.setColor(new Color(20, 50, 100));
        g.fillOval(px + eyeSpacing + 3, py - 5, 7, 7);
        g.setColor(Color.white);
        g.fillOval(px + eyeSpacing + 4, py - 4, 3, 3);

        // Mouth - changes based on puff state
        if (p.puffed) {
            // O mouth (surprised)
            g.setColor(new Color(180, 120, 60));
            g.fillOval(px + 2, py + 8, 8, 10);
        } else {
            // Happy smile
            g.setColor(new Color(180, 120, 60));
            g.setStroke(new BasicStroke(2));
            g.drawArc(px - 2, py + 6, 14, 8, 0, -180);
            g.setStroke(new BasicStroke(1));
        }

        // Cheeks
        g.setColor(new Color(255, 160, 120, 90));
        g.fillOval(px - radius / 2 + 2, py + 2, 10, 7);

        // Top fin
        g.setColor(new Color(240, 200, 60));
        Polygon topFin = new Polygon();
        topFin.addPoint(px - 4, py - radius);
        topFin.addPoint(px + 4, py - radius);
        topFin.addPoint(px, py - radius - 10);
        g.fillPolygon(topFin);

        // Side fin (animated)
        double finAngle = Math.sin(tick / 6.0) * 0.3;
        g.setColor(new Color(240, 210, 80, 180));
        int fx = px - radius + 2;
        int fy = py + 2;
        g.fillOval(fx - 8 + (int)(4 * Math.sin(finAngle)), fy - 4, 12, 8);

        // Highlight
        g.setColor(new Color(255, 255, 255, 70));
        g.fillOval(px - radius / 2, py - radius / 2 - 2, radius, radius / 2);

        // Tail
        g.setColor(new Color(230, 190, 50));
        Polygon tailP = new Polygon();
        tailP.addPoint(px - radius + 2, py);
        tailP.addPoint(px - radius - 10, py - 8);
        tailP.addPoint(px - radius - 10, py + 8);
        g.fillPolygon(tailP);
    }

    void drawParticles(Graphics2D g) {
        for (Particle pt : particles) {
            float alpha = Math.max(0, (float) pt.life / pt.maxLife);
            g.setColor(new Color(pt.color.getRed(), pt.color.getGreen(), pt.color.getBlue(), (int)(alpha * 255)));
            int sz = (int)(3 + 4 * alpha);
            g.fillOval((int) pt.x - sz / 2, (int) pt.y - sz / 2, sz, sz);
        }
    }

    void drawPopups(Graphics2D g) {
        for (ScorePopup sp : popups) {
            float alpha = Math.min(1f, sp.life / 20f);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.setColor(new Color(sp.color.getRed(), sp.color.getGreen(), sp.color.getBlue(), (int)(alpha * 255)));
            g.drawString(sp.text, (int) sp.x, (int) sp.y);
        }
    }

    void drawHUD(Graphics2D g) {
        // Top bar background
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, WIDTH, 55);

        g.setColor(Color.white);
        g.setFont(new Font("Arial", Font.BOLD, 22));

        // Lives as hearts
        for (int i = 0; i < p.lives; i++) {
            drawHeart(g, 20 + i * 30, 14, 12, new Color(255, 80, 100));
        }

        // Wave
        g.drawString("Wave " + wave, WIDTH - 130, 28);

        // Score
        String scoreStr = "Score: " + p.score;
        g.drawString(scoreStr, WIDTH / 2 - 50, 28);

        // High score
        g.setFont(new Font("Arial", Font.PLAIN, 15));
        g.setColor(new Color(255, 255, 200, 180));
        g.drawString("Best: " + highscore, WIDTH / 2 - 30, 46);

        // Combo
        if (combo > 1) {
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.setColor(new Color(255, 255, 50, Math.min(255, 150 + combo * 20)));
            g.drawString("x" + combo + " COMBO!", WIDTH / 2 + 80, 28);
        }

        // Cooldown indicators
        int barY = HEIGHT - 20;
        // Puff cooldown
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(15, barY - 5, 110, 16, 8, 8);
        g.setColor(new Color(255, 220, 50));
        String puffLabel = p.puffed ? "PUFFED!" : (p.puffCooldown > 0 ? "Puff..." : "Puff [SPACE]");
        float puffPct = p.puffed ? 1f : (p.puffCooldown > 0 ? 1f - p.puffCooldown / 90f : 1f);
        g.fillRoundRect(15, barY - 5, (int)(110 * puffPct), 16, 8, 8);
        g.setColor(Color.white);
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.drawString(puffLabel, 25, barY + 7);

        // Dash cooldown
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(135, barY - 5, 110, 16, 8, 8);
        g.setColor(new Color(100, 200, 255));
        String dashLabel = p.dashing ? "DASH!" : (p.dashCooldown > 0 ? "Dash..." : "Dash [SHIFT]");
        float dashPct = p.dashing ? 1f : (p.dashCooldown > 0 ? 1f - p.dashCooldown / 60f : 1f);
        g.fillRoundRect(135, barY - 5, (int)(110 * dashPct), 16, 8, 8);
        g.setColor(Color.white);
        g.drawString(dashLabel, 145, barY + 7);

        // Active power-up timers
        int powerY = 70;
        if (p.shielded) {
            g.setColor(new Color(80, 200, 255, 180));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("SHIELD: " + (p.shieldTimer / 60 + 1) + "s", 15, powerY);
            powerY += 20;
        }
        if (p.speedBoosted) {
            g.setColor(new Color(80, 255, 120, 180));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("SPEED: " + (p.speedTimer / 60 + 1) + "s", 15, powerY);
        }
    }

    void drawHeart(Graphics2D g, int x, int y, int size, Color c) {
        g.setColor(c);
        g.fillOval(x, y, size, size);
        g.fillOval(x + size / 2, y, size, size);
        Polygon hp = new Polygon();
        hp.addPoint(x - 1, y + size / 2);
        hp.addPoint(x + size + size / 2 + 1, y + size / 2);
        hp.addPoint(x + size / 2 + size / 4, y + size + size / 2);
        g.fillPolygon(hp);
    }

    void drawMenu(Graphics2D g) {
        // Title
        g.setFont(new Font("Arial", Font.BOLD, 52));
        // Shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.drawString("Pufferfish Survival", WIDTH / 2 - 232, HEIGHT / 3 + 3);
        g.setColor(new Color(255, 240, 100));
        g.drawString("Pufferfish Survival", WIDTH / 2 - 234, HEIGHT / 3);

        // Animated pufferfish in menu
        int mpx = WIDTH / 2, mpy = HEIGHT / 2 + 10;
        int mr = (int)(28 + 4 * Math.sin(tick / 15.0));
        g.setColor(new Color(255, 235, 110));
        g.fillOval(mpx - mr, mpy - mr, mr * 2, mr * 2);
        g.setColor(Color.white);
        g.fillOval(mpx + mr / 3, mpy - 8, 12, 12);
        g.setColor(new Color(20, 50, 100));
        g.fillOval(mpx + mr / 3 + 3, mpy - 4, 6, 6);
        g.setColor(new Color(180, 120, 60));
        g.setStroke(new BasicStroke(2));
        g.drawArc(mpx - 2, mpy + 6, 14, 8, 0, -180);
        g.setStroke(new BasicStroke(1));

        // Instructions
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.setColor(Color.white);
        g.drawString("Press ENTER to Start", WIDTH / 2 - 115, HEIGHT / 2 + 80);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(new Color(200, 220, 255));
        g.drawString("WASD / Arrows = Move   |   SPACE = Puff Up   |   SHIFT = Dash", WIDTH / 2 - 250, HEIGHT / 2 + 120);
        g.drawString("Collect food for points, power-ups for abilities!", WIDTH / 2 - 195, HEIGHT / 2 + 145);

        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.setColor(new Color(255, 255, 200));
        g.drawString("High Score: " + highscore, WIDTH / 2 - 60, HEIGHT / 2 + 185);
    }

    void drawGameOver(Graphics2D g) {
        // Dim overlay
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Panel
        int pw = 400, ph = 220;
        int pxp = WIDTH / 2 - pw / 2, pyp = HEIGHT / 2 - ph / 2;
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRoundRect(pxp, pyp, pw, ph, 24, 24);
        g.setColor(new Color(19, 46, 99, 60));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(pxp, pyp, pw, ph, 24, 24);
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(19, 46, 99));
        g.setFont(new Font("Arial", Font.BOLD, 42));
        g.drawString("Game Over!", WIDTH / 2 - 120, pyp + 55);

        g.setFont(new Font("Arial", Font.BOLD, 26));
        g.drawString("Score: " + p.score, WIDTH / 2 - 50, pyp + 95);

        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(new Color(60, 60, 80));
        g.drawString("Wave reached: " + wave, WIDTH / 2 - 65, pyp + 125);
        g.drawString("High Score: " + highscore, WIDTH / 2 - 60, pyp + 155);

        if (newHighscore) {
            g.setColor(new Color(235, 180, 0));
            g.setFont(new Font("Arial", Font.BOLD, 22));
            g.drawString("NEW HIGH SCORE!", WIDTH / 2 - 95, pyp + 185);
        }

        // Restart prompt (blinking)
        if ((tick / 30) % 2 == 0) {
            g.setColor(new Color(80, 80, 120));
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString("Press R to Restart  |  ESC to Quit", WIDTH / 2 - 155, pyp + ph + 30);
        }
    }

    // ========== GAME LOGIC ==========

    @Override
    public void actionPerformed(ActionEvent e) {
        tick++;

        if (state == State.MENU) {
            repaint();
            return;
        }

        if (state == State.GAME_OVER) {
            repaint();
            return;
        }

        // Movement
        p.move(dx, dy);
        p.update();

        // Screen shake
        if (shakeFrames > 0) shakeFrames--;
        if (damageFlash > 0) damageFlash--;
        if (comboTimer > 0) { comboTimer--; if (comboTimer == 0) combo = 0; }

        // Spawn enemies
        if (spawnCd <= 0) {
            double ey = 80 + Math.random() * (HEIGHT - 160);
            String[] patterns = {"straight", "sine", "zigzag"};
            String pat = patterns[rand.nextInt(Math.min(1 + wave / 2, patterns.length))];
            int dir = rand.nextBoolean() ? 1 : -1;
            int type = 0;
            if (wave >= 3 && rand.nextDouble() < 0.2) type = 1; // fast
            if (wave >= 5 && rand.nextDouble() < 0.12) type = 2; // big

            double speed = 2 + wave * 0.4 + (type == 1 ? 2.5 : 0);
            enemies.add(new Enemy(ey, speed, pat, dir, type));

            // Maybe a second enemy at higher waves
            if (wave >= 4 && rand.nextDouble() < 0.3) {
                double ey2 = 80 + Math.random() * (HEIGHT - 160);
                enemies.add(new Enemy(ey2, 2 + wave * 0.3, "straight", -dir, 0));
            }

            if (wave > 2 && rand.nextDouble() < 0.08 + wave * 0.01)
                spikes.add(new Spike(60 + Math.random() * (WIDTH - 120), 80 + Math.random() * (HEIGHT - 160), rand.nextBoolean()));

            spawnCd = Math.max(35 - 3 * wave, 8);
        } else spawnCd--;

        // Spawn collectibles
        if (collectCd <= 0) {
            double cx = 60 + Math.random() * (WIDTH - 120);
            double cy = 80 + Math.random() * (HEIGHT - 160);
            int type;
            double r = rand.nextDouble();
            if (r < 0.55) type = 0;       // food (common)
            else if (r < 0.75) type = 1;  // shield
            else if (r < 0.92) type = 2;  // speed
            else type = 3;                 // extra life (rare)
            collectibles.add(new Collectible(cx, cy, type));
            collectCd = 120 + rand.nextInt(100);
        } else collectCd--;

        // Update all
        for (Enemy en : enemies) en.update();
        for (Spike s : spikes) s.update();
        for (Collectible c : collectibles) c.update();
        particles.forEach(Particle::update);
        popups.forEach(ScorePopup::update);
        particles.removeIf(pt -> pt.life <= 0);
        popups.removeIf(sp -> sp.life <= 0);

        // Collectible pickup
        Iterator<Collectible> cIt = collectibles.iterator();
        while (cIt.hasNext()) {
            Collectible c = cIt.next();
            if (c.time > 600) { cIt.remove(); continue; } // despawn after 10s
            double ddx = p.x - c.x, ddy = p.y - c.y;
            if (Math.sqrt(ddx * ddx + ddy * ddy) < (p.puffed ? 28 : p.radius) + 14) {
                cIt.remove();
                switch (c.type) {
                    case 0: // food
                        int points = 15;
                        p.score += points;
                        spawnParticles(c.x, c.y, 8, new Color(255, 200, 80));
                        popups.add(new ScorePopup(c.x, c.y, "+" + points, new Color(255, 220, 50)));
                        break;
                    case 1: // shield
                        p.shielded = true;
                        p.shieldTimer = 300; // 5 seconds
                        spawnParticles(c.x, c.y, 12, new Color(80, 200, 255));
                        popups.add(new ScorePopup(c.x, c.y, "SHIELD!", new Color(80, 200, 255)));
                        break;
                    case 2: // speed
                        p.speedBoosted = true;
                        p.speedTimer = 300;
                        spawnParticles(c.x, c.y, 12, new Color(80, 255, 120));
                        popups.add(new ScorePopup(c.x, c.y, "SPEED!", new Color(80, 255, 120)));
                        break;
                    case 3: // extra life
                        if (p.lives < 5) p.lives++;
                        spawnParticles(c.x, c.y, 15, new Color(255, 80, 120));
                        popups.add(new ScorePopup(c.x, c.y, "+1 LIFE!", new Color(255, 80, 120)));
                        break;
                }
            }
        }

        // Collisions - enemies
        int pRadius = p.puffed ? 28 : p.radius;
        Iterator<Enemy> enIt = enemies.iterator();
        while (enIt.hasNext()) {
            Enemy en = enIt.next();
            if (en.x < -60 || en.x > WIDTH + 60) { enIt.remove(); continue; }
            if (collide(p.x, p.y, pRadius, en.x, en.y, en.getRadius())) {
                if (p.puffed || p.dashing) {
                    int pts = en.getScore();
                    combo++;
                    comboTimer = 90;
                    pts *= Math.min(combo, 5);
                    p.score += pts;
                    spawnParticles(en.x, en.y, 15, new Color(255, 100, 60));
                    popups.add(new ScorePopup(en.x, en.y, "+" + pts, new Color(255, 255, 100)));
                    enIt.remove();
                } else if (p.shielded) {
                    spawnParticles(en.x, en.y, 8, new Color(80, 200, 255));
                    enIt.remove();
                } else if (p.iFrames <= 0) {
                    p.lives--;
                    p.iFrames = 60;
                    shakeFrames = 10;
                    shakeIntensity = 6;
                    damageFlash = 15;
                    enIt.remove();
                    if (p.lives <= 0) { endGame(); break; }
                }
            }
        }

        // Collisions - spikes
        Iterator<Spike> spIt = spikes.iterator();
        while (spIt.hasNext()) {
            Spike s = spIt.next();
            if (collide(p.x, p.y, pRadius, s.x, s.y, 14)) {
                if (p.puffed || p.dashing || p.shielded) {
                    // Destroy spike
                    spawnParticles(s.x, s.y, 10, new Color(120, 100, 160));
                    p.score += 5;
                    popups.add(new ScorePopup(s.x, s.y, "+5", new Color(200, 200, 255)));
                    spIt.remove();
                } else if (p.iFrames <= 0) {
                    p.lives--;
                    p.iFrames = 60;
                    shakeFrames = 8;
                    shakeIntensity = 5;
                    damageFlash = 12;
                    if (p.lives <= 0) { endGame(); break; }
                }
            }
        }

        // Wave progression
        int waveThreshold = 30 + wave * 20;
        if (p.score >= waveThreshold * wave) wave = Math.min(wave + 1, 30);

        // Cleanup
        enemies.removeIf(en -> en.x < -80 || en.x > WIDTH + 80);
        spikes.removeIf(spk -> spk.y < -60 || spk.y > HEIGHT + 60);
        collectibles.removeIf(c -> c.time > 650);

        repaint();
    }

    void spawnParticles(double x, double y, int count, Color color) {
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 1 + Math.random() * 3;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
                    20 + rand.nextInt(20), color));
        }
    }

    static boolean collide(double x1, double y1, double r1, double x2, double y2, double r2) {
        double ddx = x1 - x2, ddy = y1 - y2;
        return Math.sqrt(ddx * ddx + ddy * ddy) < r1 + r2;
    }

    // ========== CONTROLS ==========

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (state == State.MENU) {
            if (k == KeyEvent.VK_ENTER) {
                state = State.PLAYING;
                resetGame();
            }
            return;
        }

        if (state == State.GAME_OVER) {
            if (k == KeyEvent.VK_R) {
                resetGame();
                state = State.PLAYING;
            } else if (k == KeyEvent.VK_ESCAPE) {
                System.exit(0);
            }
            return;
        }

        // Playing controls
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) dx = -1;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) dx = 1;
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) dy = -1;
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) dy = 1;
        if (k == KeyEvent.VK_SPACE && p.puffCooldown == 0 && !p.puffed) {
            p.puffed = true;
            p.puffCooldown = 90;
        }
        // Dash
        if (k == KeyEvent.VK_SHIFT && p.dashCooldown == 0 && !p.dashing) {
            p.dashing = true;
            p.dashTimer = 6;
            p.dashCooldown = 60;
            p.dashDx = dx == 0 && dy == 0 ? 1 : dx;
            p.dashDy = dy;
            spawnParticles(p.x, p.y, 8, new Color(150, 220, 255));
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) { if (dx < 0) dx = 0; }
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) { if (dx > 0) dx = 0; }
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) { if (dy < 0) dy = 0; }
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) { if (dy > 0) dy = 0; }
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    // ========== GAME STATE ==========

    void resetGame() {
        p = new Pufferfish();
        enemies.clear();
        spikes.clear();
        collectibles.clear();
        particles.clear();
        popups.clear();
        dx = 0; dy = 0;
        wave = 1; spawnCd = 0; collectCd = 0;
        shakeFrames = 0; damageFlash = 0;
        combo = 0; comboTimer = 0;
        newHighscore = false;
    }

    void endGame() {
        state = State.GAME_OVER;
        newHighscore = false;
        if (p.score > highscore) {
            highscore = p.score;
            saveHighscore();
            newHighscore = true;
        }
    }

    // ========== HIGHSCORE ==========

    private void loadHighscore() {
        try (BufferedReader br = new BufferedReader(new FileReader(HIGHSCORE_FILE))) {
            String line = br.readLine();
            if (line != null) highscore = Integer.parseInt(line.trim());
        } catch (Exception ex) {
            highscore = 0;
        }
    }

    private void saveHighscore() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HIGHSCORE_FILE))) {
            pw.println(highscore);
        } catch (Exception ex) {
            // ignore
        }
    }

    // ========== ENTRY POINT ==========

    public static void main(String[] args) {
        JFrame f = new JFrame("Pufferfish Survival");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setResizable(false);
        PufferfishSurvival_Version3 game = new PufferfishSurvival_Version3();
        f.add(game);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
