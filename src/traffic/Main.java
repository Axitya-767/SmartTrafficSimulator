package traffic;

import traffic.engine.SimulationEngine;
import traffic.model.Edge;
import traffic.model.MapData;
import traffic.ui.MainFrame;

import javax.swing.*;
import java.util.List;
import java.util.Random;

/**
 * Entry point.  Builds a 6×6 traffic grid, seeds initial vehicles,
 * and launches the Swing UI.
 */
public class Main {

    private static final int GRID_ROWS = 6;
    private static final int GRID_COLS = 6;
    private static final int START_X   = 100;
    private static final int START_Y   = 65;
    private static final int SPACING_X = 140;
    private static final int SPACING_Y = 100;
    private static final int INITIAL_VEHICLES = 10;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // ── Build grid ─────────────────────────────────
            MapData mapData = new MapData(GRID_ROWS, GRID_COLS,
                    START_X, START_Y, SPACING_X, SPACING_Y);

            SimulationEngine engine = new SimulationEngine(mapData);

            // ── Seed vehicles on random edges ──────────────
            Random rng = new Random();
            List<Edge> edges = mapData.getEdges();
            for (int i = 0; i < INITIAL_VEHICLES; i++) {
                Edge edge = edges.get(rng.nextInt(edges.size()));
                engine.spawnVehicle(edge);
            }

            // ── Launch UI ──────────────────────────────────
            MainFrame frame = new MainFrame(engine);
            frame.setVisible(true);
        });
    }
}
