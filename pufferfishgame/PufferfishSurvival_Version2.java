import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.awt.geom.*;
import java.io.*;

public class PufferfishSurvival extends JPanel implements ActionListener, KeyListener {

    // Window
    static final int WIDTH = 800, HEIGHT = 600;
    Timer timer = new Timer(16, this);

    // Highscore
    int highscore = 0;
    final String HIGHSCORE_FILE = "highscore.txt";

    // Player/Pufferfish
    class Pufferfish {
        int x = WIDTH / 2, y = HEIGHT - 100;
        int radius = 16;
        int lives = 3;
        int score = 0;
        boolean puffed = false;
        int cooldown = 0;
        void move(int dx, int dy) {
            double speed = puffed ? 1.2 : 3.0;
            x = (int) Math.max(radius, Math.min(WIDTH - radius, x + dx * speed));
            y = (int) Math.max(radius, Math.min(HEIGHT - radius, y + dy * speed));
        }
        void update() {
            if (cooldown > 0) cooldown--;
            else puffed = false;
        }
    }

    class Enemy {
        double x, y, speed;
        int dir;
        String pattern;
        int time = 0;
        Enemy(double y, double speed, String pattern, int dir) {
            this.y = y;
            this.speed = speed;
            this.pattern = pattern;
            this.dir = dir;
            this.x = dir == 1 ? 0 : WIDTH;
        }
        void update() {
            x += dir * speed;
            if (pattern.equals("sine"))
                y += 8 * Math.sin(time / 18.0);
            time++;
        }
    }

    class Spike {
        double x, y;
        boolean moving;
        int time = 0;
        Spike(double x, double y, boolean moving) {
            this.x = x; this.y = y; this.moving = moving;
        }
        void update() {
            if (moving) {
                y += 2 * Math.sin(time / 8.0);
                time++;
            }
        }
    }

    Pufferfish p = new Pufferfish();
    java.util.List<Enemy> enemies = new ArrayList<>();
    java.util.List<Spike> spikes = new ArrayList<>();
    int dx = 0, dy = 0;
    int wave = 1, spawnCd = 0;
    boolean running = true, gameOver = false;
    boolean newHighscore = false;

    public PufferfishSurvival() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        loadHighscore();
        timer.start();
    }

    public void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        // Smooth
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Water background
        g.setColor(new Color(35,137,218));
        g.fillRect(0,0,WIDTH,HEIGHT);
        g.setColor(new Color(200,230,255));
        g.fillRect(0,0,WIDTH,32);

        // Spikes
        g.setColor(new Color(120,120,140));
        for(Spike s: spikes) {
            Polygon poly = new Polygon();
            poly.addPoint((int)s.x, (int)(s.y-10));
            poly.addPoint((int)(s.x+4), (int)(s.y+10));
            poly.addPoint((int)(s.x-4), (int)(s.y+10));
            g.fillPolygon(poly);
        }

        // Enemies
        for(Enemy e: enemies) {
            g.setColor(new Color(220,50,50));
            g.fillOval((int)e.x-18, (int)e.y-8, 36, 20);
        }

        // Draw Pufferfish
        int radius = p.puffed ? 24 : 16;
        g.setColor(new Color(254,224,97));
        g.fillOval(p.x-radius, p.y-radius, 2*radius, 2*radius);
        // Eye
        g.setColor(new Color(19,46,99));
        int eyeX = p.x + (int)(radius*0.5);
        g.fillOval(eyeX, p.y-6, 6, 6);
        g.setColor(Color.white);
        g.fillOval(eyeX, p.y-6, 2, 2);

        // GUI
        g.setColor(Color.white);
        g.setFont(new Font("Arial",Font.BOLD,22));
        g.drawString("Lives: "+p.lives, 16, 26);
        g.drawString("Waves: "+wave, WIDTH-120, 26);
        g.drawString("Score: "+p.score, WIDTH/2-40, 26);
        g.setFont(new Font("Arial",Font.BOLD,18));
        g.drawString("High Score: "+highscore, WIDTH/2-60, 56);

        // Game Over
        if(gameOver) {
            g.setFont(new Font("Arial",Font.BOLD,40));
            g.setColor(new Color(255,255,255,220));
            g.fillRoundRect(WIDTH/2-190, HEIGHT/2-90, 380, 150, 20,20);
            g.setColor(new Color(19,46,99));
            g.drawString("Game Over!", WIDTH/2-110, HEIGHT/2-35);
            g.setFont(new Font("Arial",Font.BOLD,24));
            g.drawString("Score: "+p.score, WIDTH/2-48, HEIGHT/2+2);
            g.setFont(new Font("Arial", Font.BOLD,22));
            g.drawString("High Score: " + highscore, WIDTH/2-60, HEIGHT/2+32);
            if(newHighscore) {
                g.setColor(new Color(235, 204, 0));
                g.setFont(new Font("Arial",Font.BOLD,20));
                g.drawString("NEW HIGHSCORE!", WIDTH/2-77, HEIGHT/2+57);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(!running) return;
        if(gameOver) {
            repaint();
            return;
        }
        // Movement
        p.move(dx,dy);
        p.update();

        // Spawn enemies/spikes
        if(spawnCd<=0) {
            int y = 60 + (int)(Math.random()*(HEIGHT-120));
            String pat = Math.random()<0.5?"sine":"straight";
            int dir = Math.random()<0.5?1:-1;
            enemies.add(new Enemy(y, 2+wave*0.5, pat, dir));
            if(wave > 3 && Math.random()<0.1)
                spikes.add(new Spike(40 + Math.random()*(WIDTH-80), 60 + Math.random()*(HEIGHT-120), true));
            spawnCd = Math.max(40-3*wave, 10);
        } else spawnCd--;

        // Update obstacles
        for(Enemy en: enemies) en.update();
        for(Spike s: spikes) s.update();

        // Collisions - enemies
        Iterator<Enemy> enIt = enemies.iterator();
        while(enIt.hasNext()) {
            Enemy en = enIt.next();
            if(en.x < -40 || en.x > WIDTH+40) {enIt.remove(); continue;}
            if(collide(p.x,p.y,p.puffed?24:16, (int)en.x,(int)en.y,18)) {
                if(p.puffed) {
                    enIt.remove();
                    p.score += 10;
                } else {
                    p.lives--;
                    enIt.remove();
                    if(p.lives <= 0) {
                        endGame();
                        break;
                    }
                }
            }
        }
        // Collisions - spikes
        Iterator<Spike> spIt = spikes.iterator();
        while(spIt.hasNext()) {
            Spike s = spIt.next();
            if(s.y<-20 || s.y>HEIGHT+20) {spIt.remove(); continue;}
            if(collide(p.x,p.y,p.puffed?24:16, s.x, s.y, 14)) {
                if(!p.puffed) {
                    p.lives--;
                    if(p.lives <= 0) {
                        endGame();
                        break;
                    }
                }
            }
        }
        // Increase difficulty/wave
        if(p.score/(30*wave) > 0) wave++;
        // Clean up
        spikes.removeIf(spk->(spk.y<-40 || spk.y>HEIGHT+40));
        repaint();
    }

    static boolean collide(double x1, double y1, double r1, double x2, double y2, double r2) {
        double dx = x1-x2, dy = y1-y2;
        return Math.sqrt(dx*dx+dy*dy) < r1 + r2;
    }

    // Controls
    @Override
    public void keyPressed(KeyEvent e) {
        if(gameOver) return;
        int k = e.getKeyCode();
        if(k == KeyEvent.VK_LEFT || k==KeyEvent.VK_A) dx = -1;
        if(k == KeyEvent.VK_RIGHT || k==KeyEvent.VK_D) dx = 1;
        if(k == KeyEvent.VK_UP || k==KeyEvent.VK_W) dy = -1;
        if(k == KeyEvent.VK_DOWN || k==KeyEvent.VK_S) dy = 1;
        if(k == KeyEvent.VK_SPACE && p.cooldown == 0) {
            p.puffed = true;
            p.cooldown = 50;
        }
    }
    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if(k == KeyEvent.VK_LEFT || k==KeyEvent.VK_A) if(dx<0)dx=0;
        if(k == KeyEvent.VK_RIGHT || k==KeyEvent.VK_D) if(dx>0)dx=0;
        if(k == KeyEvent.VK_UP || k==KeyEvent.VK_W) if(dy<0)dy=0;
        if(k == KeyEvent.VK_DOWN || k==KeyEvent.VK_S) if(dy>0)dy=0;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // Highscore methods
    private void loadHighscore() {
        try (BufferedReader br = new BufferedReader(new FileReader(HIGHSCORE_FILE))) {
            String line = br.readLine();
            if (line != null) highscore = Integer.parseInt(line.trim());
        } catch (Exception e) {
            highscore = 0; // No file, etc.
        }
    }
    private void saveHighscore() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HIGHSCORE_FILE))) {
            pw.println(highscore);
        } catch (Exception e) {
            // ignore
        }
    }

    private void endGame() {
        running = false; gameOver = true;
        newHighscore = false;
        if(p.score > highscore) {
            highscore = p.score;
            saveHighscore();
            newHighscore = true;
        }
        timer.stop();
        // Give time for player to see the score before closing
        new javax.swing.Timer(3000, evt->{System.exit(0);}).start();
    }

    // Entry
    public static void main(String[] args) {
        JFrame f = new JFrame("🐡 Pufferfish Survival");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setResizable(false);
        PufferfishSurvival g = new PufferfishSurvival();
        f.add(g);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}