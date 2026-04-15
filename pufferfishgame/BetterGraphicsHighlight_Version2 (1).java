@Override
public void paintComponent(Graphics g0) {
    super.paintComponent(g0);
    Graphics2D g = (Graphics2D) g0;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // --- Gradient Background ---
    GradientPaint ocean = new GradientPaint(0, 0, new Color(64,160,255), 0, HEIGHT, new Color(16,48,92));
    g.setPaint(ocean);
    g.fillRect(0, 0, WIDTH, HEIGHT);

    // --- Bubbles (background deco) ---
    for(int i = 0; i < 30; i++) {
        int bx = (i*27)%WIDTH;
        int by = (int)(30+130*Math.sin(i*0.6+(System.currentTimeMillis()/1200.0)));
        g.setColor(new Color(210,230,249,60));
        g.fillOval(bx, by, 12, 12);
    }

    // --- Spikes (Sea Urchin style) ---
    for(Spike s: spikes) {
        int px = (int)s.x, py = (int)s.y;
        g.setColor(new Color(40,43,48));
        for(int j=0;j<10;j++) {
            double a = Math.PI*2*j/10;
            int sx = px + (int)(Math.cos(a)*20);
            int sy = py + (int)(Math.sin(a)*20);
            g.drawLine(px, py, sx, sy);
        }
        g.setColor(new Color(90,90,110));
        g.fillOval(px-8, py-8, 16, 16);
        g.setColor(new Color(200,200,255,50));
        g.drawOval(px-10, py-10, 20, 20);
    }

    // --- Enemies (Stylized Fish) ---
    for(Enemy e: enemies) {
        int ex = (int)e.x, ey = (int)e.y;

        // Tail
        g.setColor(new Color(200,60,60));
        Polygon tail = new Polygon();
        tail.addPoint(ex-18, ey);
        tail.addPoint(ex-28, ey-8);
        tail.addPoint(ex-28, ey+8);
        g.fillPolygon(tail);

        // Body gradient
        GradientPaint gradient = new GradientPaint(ex-18, ey-8, new Color(220,60,60), ex+18, ey+8, new Color(250,200,200));
        g.setPaint(gradient);
        g.fillOval(ex-18, ey-10, 38, 20);

        // Eye
        g.setColor(Color.white);
        g.fillOval(ex+10, ey-4, 8, 8);
        g.setColor(Color.black);
        g.fillOval(ex+14, ey-1, 2, 2);
    }

    // --- Pufferfish (with spikes & cartoon face) ---
    int radius = p.puffed ? 24 : 16;
    int px = p.x, py = p.y;

    for(int j=0;j<16;j++) {
        double a = Math.PI*2*j/16;
        int sx = px + (int)(Math.cos(a)*(radius+7));
        int sy = py + (int)(Math.sin(a)*(radius+7));
        g.setColor(new Color(223, 184, 31));
        g.drawLine(px, py, sx, sy);
    }

    g.setColor(new Color(254, 224, 97));
    g.fillOval(px-radius, py-radius, 2*radius, 2*radius);

    // Eyes
    g.setColor(Color.white);
    g.fillOval(px+radius/2-6, py-8, 11, 11);
    g.fillOval(px+radius/2-6, py+2, 11, 11);
    g.setColor(Color.black);
    g.fillOval(px+radius/2-2, py-4, 5, 5);
    g.fillOval(px+radius/2-2, py+6, 5, 5);

    // Smiley mouth
    g.setColor(Color.black);
    g.drawArc(px-4, py+9, 14, 5, 0, -180);
    g.setColor(new Color(214, 186, 118));
    g.fillOval(px-8, py+2, 8, 10); // Cheek

    // Puffer highlight
    g.setColor(new Color(255,255,255,80));
    g.fillOval(px-radius/2, py-radius/2-4, radius, radius/2);

    // --- GUI ---
    g.setColor(Color.white);
    g.setFont(new Font("Arial",Font.BOLD,22));
    g.drawString("Lives: "+p.lives, 16, 26);
    g.drawString("Waves: "+wave, WIDTH-120, 26);
    g.drawString("Score: "+p.score, WIDTH/2-40, 26);
    g.setFont(new Font("Arial",Font.BOLD,18));
    g.drawString("High Score: "+highscore, WIDTH/2-60, 56);

    if(gameOver) {
        g.setFont(new Font("Arial",Font.BOLD,40));
        g.setColor(new Color(255,255,255,235));
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