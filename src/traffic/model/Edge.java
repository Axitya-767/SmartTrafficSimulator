package traffic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/*import java.util.Objects;*/

/**
 * A directed edge (road segment) connecting two GridNodes.
 * Maintains its own list of vehicles currently travelling on it.
 */
public class Edge {

    private final GridNode start;
    private final GridNode end;
    private final double distance;
    private final List<Vehicle> vehicles;
    private boolean blocked;

    public Edge(GridNode start, GridNode end) {
        this.start = start;
        this.end = end;
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        this.distance = Math.sqrt(dx * dx + dy * dy);
        this.vehicles = new ArrayList<>();
        this.blocked = false;
    }

    // ── Accessors ──────────────────────────────────────────
    public GridNode getStart() {
        return start;
    }

    public GridNode getEnd() {
        return end;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * Congestion-aware weight used by RoutingEngine.
     * Formula: physical distance + (vehicles_on_edge × 100)
     */
    public double getWeight() {
        if (blocked)
            return Integer.MAX_VALUE;
        return distance + vehicles.size() * 100.0;
    }

    /**
     * Traffic-light-aware weight for normal vehicle routing.
     * Adds an estimated delay penalty when the traffic light at the
     * end node is RED or YELLOW, encouraging routes through green lights.
     *
     * Penalty values approximate the expected wait in distance-equivalent units:
     * RED → 300 (significant detour-worthy delay)
     * YELLOW → 100 (moderate – may still clear in time)
     * GREEN → 0
     */
    public double getTrafficAwareWeight() {
        if (blocked)
            return Integer.MAX_VALUE;
        double base = distance + vehicles.size() * 100.0;
        TrafficLight light = end.getTrafficLight();
        switch (light.getState()) {
            case RED:
                // Scale penalty by how long the light has been red.
                // Early in the red phase → full penalty; late → smaller penalty.
                double redRemaining = Math.max(0, 180 - light.getTicksInState());
                base += redRemaining * 1.5;
                break;
            case YELLOW:
                base += 80;
                break;
            case GREEN:
                // no penalty
                break;
        }
        return base;
    }

    /**
     * Pure-distance weight for emergency vehicle routing.
     * Emergency vehicles ignore traffic lights and force others to yield,
     * so only physical distance matters. Blocked edges are still avoided
     * UNLESS the blocked edge is the target wreck edge itself.
     */
    public double getEmergencyWeight() {
        if (blocked)
            return Integer.MAX_VALUE;
        return distance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean b) {
        this.blocked = b;
    }

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public void addVehicle(Vehicle v) {
        vehicles.add(v);
    }

    public void removeVehicle(Vehicle v) {
        vehicles.remove(v);
    }

    public int getVehicleCount() {
        return vehicles.size();
    }

    @Override
    public String toString() {
        return "Edge{" + start.getId() + "→" + end.getId()
                + " d=" + String.format("%.0f", distance)
                + " v=" + vehicles.size() + "}";
    }
}
