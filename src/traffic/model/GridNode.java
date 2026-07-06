package traffic.model;

/**
 * A node in the traffic grid. Stores pixel coordinates, grid position,
 * and an independent TrafficLight state machine.
 */
public class GridNode {

    private final int id;
    private final double x;
    private final double y;
    private final int gridRow;
    private final int gridCol;
    private final TrafficLight trafficLight;

    public GridNode(int id, double x, double y, int gridRow, int gridCol) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.gridRow = gridRow;
        this.gridCol = gridCol;
        // Stagger phase so adjacent intersections are offset
        int phaseOffset = ((gridRow + gridCol) % 3) * 70;
        this.trafficLight = new TrafficLight(phaseOffset);
    }

    // ── Accessors ──────────────────────────────────────────
    public int    getId()       { return id; }
    public double getX()        { return x; }
    public double getY()        { return y; }
    public int    getGridRow()  { return gridRow; }
    public int    getGridCol()  { return gridCol; }
    public TrafficLight getTrafficLight() { return trafficLight; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((GridNode) o).id;
    }

    @Override
    public int hashCode() { return Integer.hashCode(id); }

    @Override
    public String toString() {
        return "Node#" + id + "(" + gridRow + "," + gridCol + ")";
    }
}
