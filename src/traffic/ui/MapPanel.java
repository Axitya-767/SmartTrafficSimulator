package traffic.ui;

import traffic.engine.SimulationEngine;
import traffic.model.Edge;
import traffic.model.GridNode;
import traffic.model.TrafficLight;
import traffic.model.Vehicle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * Renders the traffic grid, vehicles, traffic lights, and selected-vehicle
 * route highlight.  Handles click interaction for node-cycling, vehicle
 * selection, and vehicle spawning.
 */
public class MapPanel extends JPanel {

    // ── Sizing constants ───────────────────────────────────
    private static final int NODE_RADIUS   = 16;
    private static final int VEHICLE_RADIUS = 7;
    private static final int LANE_OFFSET   = 5;   // px offset for two-lane effect

    // ── Palette ────────────────────────────────────────────
    private static final Color BG           = new Color(20, 22, 28);
    private static final Color GRID_LINE    = new Color(34, 38, 48);
    private static final Color ROAD_SURFACE = new Color(52, 58, 72);
    private static final Color ROAD_BORDER  = new Color(38, 42, 54);
    private static final Color ROAD_DASH    = new Color(90, 85, 55, 120);

    private static final Color LIGHT_GREEN  = new Color(52, 211, 100);
    private static final Color LIGHT_YELLOW = new Color(250, 204, 21);
    private static final Color LIGHT_RED    = new Color(248, 80, 80);

    private static final Color NODE_FILL    = new Color(40, 46, 60);
    private static final Color NODE_LABEL   = new Color(200, 210, 230);

    private static final Color ROUTE_COLOR  = new Color(56, 189, 248, 160);
    private static final Color ROUTE_GLOW   = new Color(56, 189, 248,  50);
    private static final Color DEST_COLOR   = new Color(244, 114, 182);
    private static final Color SELECT_RING  = new Color(250, 204, 21, 200);

    private static final Color HUD_FG       = new Color(160, 170, 195, 210);
    private static final Color STOPPED_IND  = new Color(248, 80, 80, 200);
    private static final Color MOVING_IND   = new Color(52, 211, 100, 180);
    private static final Color WRECK_COLOR  = new Color(255, 40, 40);
    private static final Color BLOCKED_ROAD = new Color(255, 30, 30, 100);
    private static final Color SIREN_RED    = new Color(255, 30, 30);
    private static final Color SIREN_BLUE   = new Color(30, 80, 255);
    private static final Color YIELD_IND    = new Color(255, 200, 40, 200);

    private final SimulationEngine engine;
    private Vehicle selectedVehicle;
    private int renderTick;  // for siren flash animation

    public MapPanel(SimulationEngine engine) {
        this.engine = engine;
        setBackground(BG);
        setPreferredSize(new Dimension(960, 660));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // Right-click → manual roadblock
                    engine.triggerCollision(e.getX(), e.getY());
                    repaint();
                } else {
                    handleClick(e.getX(), e.getY());
                }
            }
        });
    }

    // ── Click handling ─────────────────────────────────────
    private void handleClick(int mx, int my) {
        // 1. Check vehicle hit first (highest priority)  →  select / deselect
        Vehicle hitVehicle = engine.findNearestVehicle(mx, my, 16);
        if (hitVehicle != null) {
            if (selectedVehicle != null) selectedVehicle.setSelected(false);
            if (selectedVehicle == hitVehicle) {
                selectedVehicle = null;       // toggle off
            } else {
                selectedVehicle = hitVehicle;  // select new
                selectedVehicle.setSelected(true);
            }
            repaint();
            return;
        }
        // 2. Check node hit
        GridNode hitNode = engine.findNearestNode(mx, my, NODE_RADIUS + 6);
        if (hitNode != null) {
            if (selectedVehicle != null) {
                // ── Vehicle is selected → reroute it to this node ──
                engine.reassignDestination(selectedVehicle, hitNode);
            } else {
                // ── No vehicle selected → cycle the traffic light ──
                hitNode.getTrafficLight().cycleManual();
            }
            repaint();
            return;
        }
        // 3. Spawn vehicle on empty area
        engine.spawnVehicleNear(mx, my);
        repaint();
    }

    // ── Rendering ──────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g2);
        drawRoads(g2);
        drawRouteHighlight(g2);
        drawIntersections(g2);
        drawVehicles(g2);
        drawHUD(g2);
        renderTick++;
    }

    // ── Background grid ────────────────────────────────────
    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID_LINE);
        g2.setStroke(new BasicStroke(1f));
        for (int x = 0; x < getWidth(); x += 40)  g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += 40)  g2.drawLine(0, y, getWidth(), y);
    }

    // ── Roads ──────────────────────────────────────────────
    private void drawRoads(Graphics2D g2) {
        // Pass 1: road borders (wider stroke)
        g2.setColor(ROAD_BORDER);
        g2.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Edge e : engine.getMapData().getEdges()) {
            if (e.getStart().getId() > e.getEnd().getId()) continue; // skip reverse
            g2.draw(line(e));
        }
        // Pass 2: road surface
        g2.setColor(ROAD_SURFACE);
        g2.setStroke(new BasicStroke(18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Edge e : engine.getMapData().getEdges()) {
            if (e.getStart().getId() > e.getEnd().getId()) continue;
            g2.draw(line(e));
        }
        // Pass 3: dashed center line
        g2.setColor(ROAD_DASH);
        float[] dash = {8f, 12f};
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
        for (Edge e : engine.getMapData().getEdges()) {
            if (e.getStart().getId() > e.getEnd().getId()) continue;
            g2.draw(line(e));
        }

        // Pass 4: congestion heat overlay
        for (Edge e : engine.getMapData().getEdges()) {
            if (e.getStart().getId() > e.getEnd().getId()) continue;
            int vc = e.getVehicleCount();
            // also count reverse edge
            for (Edge rev : engine.getMapData().getEdges()) {
                if (rev.getStart().equals(e.getEnd()) && rev.getEnd().equals(e.getStart())) {
                    vc += rev.getVehicleCount();
                    break;
                }
            }
            if (vc > 0) {
                int alpha = Math.min(80, vc * 25);
                g2.setColor(new Color(255, 60, 40, alpha));
                g2.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(line(e));
            }
        }

        // Pass 5: blocked-edge overlay (bright red flash)
        for (Edge e : engine.getMapData().getEdges()) {
            if (!e.isBlocked()) continue;
            if (e.getStart().getId() > e.getEnd().getId()) continue;
            g2.setColor(BLOCKED_ROAD);
            g2.setStroke(new BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line(e));
            // draw X marks along the blocked road
            double mx = (e.getStart().getX() + e.getEnd().getX()) / 2;
            double my = (e.getStart().getY() + e.getEnd().getY()) / 2;
            g2.setColor(WRECK_COLOR);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int s = 8;
            g2.drawLine((int)(mx - s), (int)(my - s), (int)(mx + s), (int)(my + s));
            g2.drawLine((int)(mx + s), (int)(my - s), (int)(mx - s), (int)(my + s));
        }
    }

    // ── Selected vehicle route highlight ───────────────────
    private void drawRouteHighlight(Graphics2D g2) {
        if (selectedVehicle == null) return;

        List<Edge> remaining = selectedVehicle.getRemainingRoute();
        // current edge
        g2.setColor(ROUTE_GLOW);
        g2.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line(selectedVehicle.getCurrentEdge()));
        g2.setColor(ROUTE_COLOR);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line(selectedVehicle.getCurrentEdge()));

        // remaining route edges
        for (Edge e : remaining) {
            g2.setColor(ROUTE_GLOW);
            g2.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line(e));
            g2.setColor(ROUTE_COLOR);
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line(e));
        }

        // destination marker
        GridNode dest = selectedVehicle.getDestination();
        if (dest != null) {
            double dx = dest.getX(), dy = dest.getY();
            // pulsing glow
            g2.setColor(new Color(DEST_COLOR.getRed(), DEST_COLOR.getGreen(), DEST_COLOR.getBlue(), 50));
            g2.fill(new Ellipse2D.Double(dx - 22, dy - 22, 44, 44));
            g2.setColor(DEST_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(dx - 18, dy - 18, 36, 36));
            // inner diamond
            int sz = 6;
            g2.fillPolygon(
                    new int[]{(int) dx, (int) dx + sz, (int) dx, (int) dx - sz},
                    new int[]{(int) dy - sz, (int) dy, (int) dy + sz, (int) dy},
                    4);
        }
    }

    // ── Intersections ──────────────────────────────────────
    private void drawIntersections(Graphics2D g2) {
        for (GridNode node : engine.getMapData().getNodes()) {
            double cx = node.getX(), cy = node.getY();
            TrafficLight.State st = node.getTrafficLight().getState();
            Color lightCol = (st == TrafficLight.State.GREEN) ? LIGHT_GREEN
                           : (st == TrafficLight.State.YELLOW) ? LIGHT_YELLOW
                           : LIGHT_RED;

            // glow ring
            g2.setColor(new Color(lightCol.getRed(), lightCol.getGreen(), lightCol.getBlue(), 35));
            g2.fill(new Ellipse2D.Double(cx - NODE_RADIUS - 6, cy - NODE_RADIUS - 6,
                    (NODE_RADIUS + 6) * 2, (NODE_RADIUS + 6) * 2));

            // node body
            g2.setColor(NODE_FILL);
            g2.fill(new Ellipse2D.Double(cx - NODE_RADIUS, cy - NODE_RADIUS,
                    NODE_RADIUS * 2, NODE_RADIUS * 2));

            // border colored by light
            g2.setColor(lightCol);
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(new Ellipse2D.Double(cx - NODE_RADIUS, cy - NODE_RADIUS,
                    NODE_RADIUS * 2, NODE_RADIUS * 2));

            // small light indicator dot
            g2.setColor(lightCol);
            g2.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));

            // label
            g2.setColor(NODE_LABEL);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            String label = String.valueOf(node.getId());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, (float) (cx - fm.stringWidth(label) / 2.0),
                    (float) (cy + NODE_RADIUS + fm.getAscent() + 2));
        }
    }

    // ── Vehicles ───────────────────────────────────────────
    private void drawVehicles(Graphics2D g2) {
        for (Vehicle v : engine.getVehicles()) {
            Edge edge = v.getCurrentEdge();
            double baseX = v.getCurrentX();
            double baseY = v.getCurrentY();

            // offset to "right lane" of road direction
            double edx = edge.getEnd().getX() - edge.getStart().getX();
            double edy = edge.getEnd().getY() - edge.getStart().getY();
            double elen = Math.sqrt(edx * edx + edy * edy);
            double perpX = 0, perpY = 0;
            if (elen > 0) {
                perpX = edy / elen * LANE_OFFSET;
                perpY = -edx / elen * LANE_OFFSET;
            }
            double vx = baseX + perpX;
            double vy = baseY + perpY;

            // ── WRECKED → draw red X ───────────────────────
            if (v.isWrecked()) {
                g2.setColor(new Color(255, 40, 40, 60));
                g2.fill(new Ellipse2D.Double(vx - 12, vy - 12, 24, 24));
                g2.setColor(WRECK_COLOR);
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int s = 7;
                g2.drawLine((int)(vx - s), (int)(vy - s), (int)(vx + s), (int)(vy + s));
                g2.drawLine((int)(vx + s), (int)(vy - s), (int)(vx - s), (int)(vy + s));
                continue;
            }

            // ── EMERGENCY → flashing siren diamond ───────────
            if (v.isEmergency()) {
                boolean flash = (renderTick / 8) % 2 == 0;
                Color siren = flash ? SIREN_RED : SIREN_BLUE;
                // outer siren glow
                g2.setColor(new Color(siren.getRed(), siren.getGreen(), siren.getBlue(), 50));
                g2.fill(new Ellipse2D.Double(vx - 16, vy - 16, 32, 32));
                // diamond body
                int d = 10;
                int[] dx = {(int) vx, (int) vx + d, (int) vx, (int) vx - d};
                int[] dy = {(int) vy - d, (int) vy, (int) vy + d, (int) vy};
                g2.setColor(Color.WHITE);
                g2.fillPolygon(dx, dy, 4);
                g2.setColor(siren);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawPolygon(dx, dy, 4);
                // inner cross (plus sign)
                g2.setColor(siren);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) vx - 4, (int) vy, (int) vx + 4, (int) vy);
                g2.drawLine((int) vx, (int) vy - 4, (int) vx, (int) vy + 4);
                continue;
            }

            // ── ACTIVE → normal rendering ──────────────────
            // selection ring
            if (v.isSelected()) {
                g2.setColor(SELECT_RING);
                g2.setStroke(new BasicStroke(2.5f));
                g2.draw(new Ellipse2D.Double(vx - VEHICLE_RADIUS - 4, vy - VEHICLE_RADIUS - 4,
                        (VEHICLE_RADIUS + 4) * 2, (VEHICLE_RADIUS + 4) * 2));
            }

            // glow
            g2.setColor(new Color(v.getColor().getRed(), v.getColor().getGreen(),
                    v.getColor().getBlue(), 45));
            g2.fill(new Ellipse2D.Double(vx - VEHICLE_RADIUS - 3, vy - VEHICLE_RADIUS - 3,
                    (VEHICLE_RADIUS + 3) * 2, (VEHICLE_RADIUS + 3) * 2));

            // body
            g2.setColor(v.getColor());
            g2.fill(new Ellipse2D.Double(vx - VEHICLE_RADIUS, vy - VEHICLE_RADIUS,
                    VEHICLE_RADIUS * 2, VEHICLE_RADIUS * 2));

            // specular highlight
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fill(new Ellipse2D.Double(vx - VEHICLE_RADIUS + 2, vy - VEHICLE_RADIUS + 2,
                    VEHICLE_RADIUS - 2, VEHICLE_RADIUS - 2));

            // stopped / moving / yielding indicator
            Color ind = v.isYielding() ? YIELD_IND
                      : v.isStopped()  ? STOPPED_IND : MOVING_IND;
            g2.setColor(ind);
            g2.fill(new Ellipse2D.Double(vx + VEHICLE_RADIUS - 2, vy - VEHICLE_RADIUS - 1, 5, 5));
        }
    }

    // ── HUD ────────────────────────────────────────────────
    private void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(HUD_FG);

        int y = getHeight() - 14;
        // Context-sensitive hint
        String hint = (selectedVehicle != null)
                ? "Click node → set dest  |  Click vehicle → deselect  |  Right-click road → roadblock"
                : "Click node → cycle light  |  Click vehicle → route  |  Right-click road → roadblock";
        long wrecks = 0; long emerg = 0;
        for (Vehicle wv : engine.getVehicles()) {
            if (wv.isWrecked()) wrecks++;
            if (wv.isEmergency()) emerg++;
        }
        String info = "Vehicles: " + engine.getVehicles().size()
                + (wrecks > 0 ? "  (" + wrecks + " wrecked)" : "")
                + (emerg > 0 ? "  (ð " + emerg + " en route)" : "")
                + "  |  " + hint;
        g2.drawString(info, 12, y);

        if (selectedVehicle != null) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.setColor(ROUTE_COLOR);
            GridNode dest = selectedVehicle.getDestination();
            String sel = "✦ Selected: " + selectedVehicle
                    + "  →  Dest: Node#" + (dest != null ? dest.getId() : "?")
                    + "  |  Remaining hops: " + selectedVehicle.getRemainingRoute().size()
                    + "  |  " + (selectedVehicle.isStopped() ? "STOPPED" : "MOVING")
                    + "  |  Click any node to reroute";
            g2.drawString(sel, 12, 22);
        }
    }

    // ── Utility ────────────────────────────────────────────
    private static Line2D.Double line(Edge e) {
        return new Line2D.Double(e.getStart().getX(), e.getStart().getY(),
                                 e.getEnd().getX(), e.getEnd().getY());
    }
}
