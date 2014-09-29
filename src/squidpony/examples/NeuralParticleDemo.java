package squidpony.examples;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import squidpony.SColor;
import squidpony.SColorFactory;
import squidpony.squidgrid.gui.SwingPane;
import squidpony.squidgrid.gui.TextCellFactory;
import squidpony.squidmath.NeuralParticle;
import squidpony.squidmath.PerlinNoise;
import squidpony.squidmath.RNG;

/**
 * Displays a large grid with randomly determined radius to show off the Neural Particle algorithm
 *
 * @author Eben Howard - http://squidpony.com - howard@squidpony.com
 */
public class NeuralParticleDemo {

    private static final RNG rng = new RNG();
    private static final int width = 600, height = 600;
    private static final int maxIterations = (int) Math.sqrt(width * height) * 4,
            minIterations = maxIterations / 10,
            maxSeeds = 7,
            maxRadius = 15;
    private SwingPane back, front;
    private NeuralParticle np;

    public static void main(String... args) {
        new NeuralParticleDemo().go();
    }

    private void go() {
        JFrame frame = new JFrame("Neural Particle Test");
        frame.getContentPane().setBackground(SColor.BLACK);
        TextCellFactory factory = new TextCellFactory(new Font("Arial", Font.BOLD, 6), 2, 2, true);
        back = new SwingPane(width, height, factory, null);
        TextCellFactory frontFactory = new TextCellFactory(new Font("Arial", Font.BOLD, 26), 20, 20, true);
        front = new SwingPane(width / 10, height / 10, frontFactory, null);

        JLayeredPane layers = new JLayeredPane();
        layers.setLayer(back, JLayeredPane.DEFAULT_LAYER);
        layers.setLayer(front, JLayeredPane.PALETTE_LAYER);
        layers.add(back);
        layers.add(front);
        layers.setPreferredSize(back.getPreferredSize());
        layers.setSize(back.getPreferredSize());

        frame.add(layers);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);

        frame.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    back.erase();
                    front.erase();
                    back.refresh();
                    front.refresh();
                } else {
                    calculate();
                }
            }

        });

//        calculate();
    }

    private void calculate() {
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                back.erase();
                front.erase();
                back.refresh();
                front.refresh();

                int radius = rng.between(1, maxRadius);
                front.put(2, 1, "Radius: " + radius, SColor.WHITE);
                int iterations = rng.between(minIterations, maxIterations);
                iterations /= 100;
                iterations *= 100;
                front.put(2, 2, "Iterations: " + iterations, SColor.WHITE);
                int seeds = rng.between(1, maxSeeds);
                front.put(2, 3, "Seed Points: " + seeds, SColor.WHITE);
                front.refresh();

                np = new NeuralParticle(width, height, radius, rng);
                for (int i = 0; i < seeds; i++) {
                    Point p = new Point(rng.nextInt(width), rng.nextInt(height));
                    np.add(p);
                    back.put(p.x, p.y, SColor.SCARLET);
                    back.refresh();
                }

                for (int i = 0; i < iterations; i++) {
                    Point pip = np.createPoint();
                    np.add(pip);
                    back.put(pip.x, pip.y, SColor.GREEN);
                    back.refresh();
                }
            }
        });

        t.setDaemon(true);
        t.start();
    }

}
