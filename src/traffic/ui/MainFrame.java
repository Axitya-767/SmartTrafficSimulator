package traffic.ui;

import traffic.engine.SimulationEngine;

import javax.swing.*;
import java.awt.*;

/**
 * Top-level window.  MapPanel in CENTER, ControlPanel in SOUTH.
 * Drives the simulation with a 16 ms Swing Timer (~60 fps).
 */
public class MainFrame extends JFrame {

    private final SimulationEngine engine;
    private final MapPanel mapPanel;
    private final ControlPanel controlPanel;
    private final Timer animTimer;

    public MainFrame(SimulationEngine engine) {
        super("Smart Traffic Simulator");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        mapPanel     = new MapPanel(engine);
        controlPanel = new ControlPanel();

        add(mapPanel,     BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // ── Button wiring ──────────────────────────────────
        controlPanel.getStartPauseBtn().addActionListener(e -> toggleRunning());

        controlPanel.getAutomateLightsBtn().addActionListener(e -> {
            engine.toggleLightsAutomated();
            controlPanel.setAutomatedState(engine.isLightsAutomated());
        });

        controlPanel.getSyncLightsBtn().addActionListener(e -> engine.syncAllLights());

        // ── Animation timer (16 ms ≈ 60 fps) ──────────────
        animTimer = new Timer(16, e -> {
            engine.tick();
            mapPanel.repaint();
        });

        pack();
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
    }

    private void toggleRunning() {
        boolean wasRunning = engine.isRunning();
        engine.setRunning(!wasRunning);
        if (engine.isRunning()) {
            animTimer.start();
        } else {
            animTimer.stop();
        }
        controlPanel.setRunningState(engine.isRunning());
    }
}
