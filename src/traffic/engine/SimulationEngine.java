package traffic.engine;

import traffic.model.Edge;
import traffic.model.GridNode;
import traffic.model.MapData;
import traffic.model.TrafficLight;
import traffic.model.Vehicle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Core simulation engine.  On each tick():
 *  1. Advances every traffic light state machine.
 *  2. Preempts traffic lights along emergency vehicle routes (green corridor).
 *  3. Forces vehicles on the ambulance's current AND upcoming edges to yield.
 *  4. Moves every vehicle along its edge, honouring red/yellow lights.
 *  5. Detects collisions and dispatches emergency vehicles.
 *  6. Counts down wreck timers as a fallback cleanup.
 */
public class SimulationEngine {

    private static final Random RNG = new Random();

    private final MapData mapData;
    private final List<Vehicle> vehicles;
    private final List<WreckRecord> wreckRecords;
    private boolean running;
    private boolean lightsAutomated;

    /** How many edges ahead the ambulance "clears" (yield + green lights). */
    private static final int EMERGENCY_LOOK_AHEAD = 3;

    /** Tracks a wreck so it can be cleaned up after WRECK_DURATION ticks. */
    private static class WreckRecord {
        final Vehicle vehicleA;
        final Vehicle vehicleB;   // null for manual roadblocks
        final Edge edge;
        final Edge reverseEdge;   // the opposite-direction edge (also blocked)
        int ticksRemaining;
        WreckRecord(Vehicle a, Vehicle b, Edge edge, Edge reverseEdge, int ticks) {
            this.vehicleA = a; this.vehicleB = b;
            this.edge = edge;  this.reverseEdge = reverseEdge;
            this.ticksRemaining = ticks;
        }
    }
    private static final int WRECK_DURATION = 300;  // ticks until cleanup

    public SimulationEngine(MapData mapData) {
        this.mapData = mapData;
        this.vehicles = new ArrayList<>();
        this.wreckRecords = new ArrayList<>();
        this.running = false;
        this.lightsAutomated = false;
    }

    // ── Accessors ──────────────────────────────────────────
    public MapData       getMapData()           { return mapData; }
    public List<Vehicle> getVehicles()          { return vehicles; }
    public boolean       isRunning()            { return running; }
    public void          setRunning(boolean r)  { this.running = r; }
    public boolean       isLightsAutomated()    { return lightsAutomated; }

    // ── Light controls ─────────────────────────────────────
    public void toggleLightsAutomated() {
        lightsAutomated = !lightsAutomated;
        for (GridNode n : mapData.getNodes()) {
            n.getTrafficLight().setAutomated(lightsAutomated);
        }
    }

    public void syncAllLights() {
        for (GridNode n : mapData.getNodes()) {
            n.getTrafficLight().syncToGreen();
        }
    }

    // ── Vehicle spawning ───────────────────────────────────
    public Vehicle spawnVehicle(Edge edge) {
        double speed = 0.8 + RNG.nextDouble() * 1.4;
        Vehicle v = new Vehicle(edge, speed);
        assignRandomDestination(v);
        vehicles.add(v);
        return v;
    }

    /** Spawn a vehicle on the nearest edge to (x, y), projected to the correct progress. */
    public Vehicle spawnVehicleNear(double x, double y) {
        Edge nearest = findNearestEdge(x, y);
        if (nearest == null) return null;
        Vehicle v = spawnVehicle(nearest);
        // project click onto edge to set initial progress
        double dx = nearest.getEnd().getX() - nearest.getStart().getX();
        double dy = nearest.getEnd().getY() - nearest.getStart().getY();
        double lenSq = dx * dx + dy * dy;
        if (lenSq > 0) {
            double t = ((x - nearest.getStart().getX()) * dx
                      + (y - nearest.getStart().getY()) * dy) / lenSq;
            v.setProgress(Math.max(0.0, Math.min(0.95, t)));
        }
        return v;
    }

    private void assignRandomDestination(Vehicle vehicle) {
        List<GridNode> nodes = mapData.getNodes();
        GridNode origin = vehicle.getCurrentEdge().getEnd();
        GridNode dest;
        int attempts = 0;
        do {
            dest = nodes.get(RNG.nextInt(nodes.size()));
            attempts++;
        } while (dest.equals(origin) && attempts < 100);

        vehicle.setDestination(dest);
        List<Edge> route = RoutingEngine.findTrafficAwarePath(origin, dest, mapData);
        vehicle.setRoute(route);
    }

    /**
     * Reroute a vehicle to a specific destination chosen by the user.
     * Computes a new Dijkstra path from the vehicle's current edge endpoint.
     */
    public void reassignDestination(Vehicle vehicle, GridNode newDest) {
        GridNode origin = vehicle.getCurrentEdge().getEnd();
        vehicle.setDestination(newDest);
        List<Edge> route = RoutingEngine.findTrafficAwarePath(origin, newDest, mapData);
        vehicle.setRoute(route);
    }

    // ── Main tick ──────────────────────────────────────────
    public void tick() {
        if (!running) return;

        // 1. Tick all traffic lights
        for (GridNode node : mapData.getNodes()) {
            node.getTrafficLight().tick();
        }

        // 2. Emergency green corridor: preempt lights along ambulance routes
        applyEmergencyGreenCorridor();

        // 3. Yielding: reset all, then force yield on current + upcoming edges
        for (Vehicle v : vehicles) v.setYielding(false);
        applyEmergencyYielding();

        // 4. Update all ACTIVE vehicles
        for (Vehicle v : new ArrayList<>(vehicles)) {
            if (!v.isWrecked()) {
                updateVehicle(v);
            }
        }

        // 5. Collision detection
        detectCollisions();

        // 6. Wreck cleanup countdown (fallback if emergency doesn't arrive)
        tickWreckCleanup();
    }

    /**
     * For each active emergency vehicle, force the traffic lights on
     * the next few edges of its route to GREEN.  This creates a
     * "green corridor" so the ambulance never has to wait.
     */
    private void applyEmergencyGreenCorridor() {
        for (Vehicle v : vehicles) {
            if (!v.isEmergency() || v.isWrecked()) continue;

            // Current edge's end-node light → green
            v.getCurrentEdge().getEnd().getTrafficLight().syncToGreen();

            // Look-ahead: force green on the next N route edges
            List<Edge> upcoming = v.getRemainingRoute();
            int count = Math.min(EMERGENCY_LOOK_AHEAD, upcoming.size());
            for (int i = 0; i < count; i++) {
                upcoming.get(i).getEnd().getTrafficLight().syncToGreen();
            }
        }
    }

    /**
     * Vehicles on the ambulance's CURRENT edge AND on its upcoming route
     * edges (look-ahead) are forced to yield (speed → 0).  This prevents
     * the ambulance from running into slow vehicles edge after edge.
     */
    private void applyEmergencyYielding() {
        for (Vehicle v : vehicles) {
            if (!v.isEmergency() || v.isWrecked()) continue;

            // Yield on current edge
            yieldVehiclesOnEdge(v, v.getCurrentEdge());

            // Yield on upcoming edges (look-ahead)
            List<Edge> upcoming = v.getRemainingRoute();
            int count = Math.min(EMERGENCY_LOOK_AHEAD, upcoming.size());
            for (int i = 0; i < count; i++) {
                yieldVehiclesOnEdge(v, upcoming.get(i));
            }
        }
    }

    /** Force all non-emergency, non-wrecked vehicles on the given edge to yield. */
    private void yieldVehiclesOnEdge(Vehicle emergencyVehicle, Edge edge) {
        for (Vehicle other : new ArrayList<>(edge.getVehicles())) {
            if (other != emergencyVehicle && !other.isWrecked() && !other.isEmergency()) {
                other.setYielding(true);
            }
        }
    }

    // ── Collision detection ────────────────────────────────
    private void detectCollisions() {
        List<Vehicle> active = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (!v.isWrecked() && !v.isEmergency()) active.add(v);
        }
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                Vehicle a = active.get(i);
                Vehicle b = active.get(j);
                if (a.isWrecked() || b.isWrecked()) continue;
                if (a.getCurrentEdge() != b.getCurrentEdge()) continue;
                double dist = Math.hypot(a.getCurrentX() - b.getCurrentX(),
                                         a.getCurrentY() - b.getCurrentY());
                if (dist < 10) {
                    applyCollision(a, b, a.getCurrentEdge());
                }
            }
        }
    }

    private void applyCollision(Vehicle a, Vehicle b, Edge edge) {
        a.wreck();
        if (b != null) b.wreck();

        // Block both directions of this road segment
        edge.setBlocked(true);
        Edge reverse = findReverseEdge(edge);
        if (reverse != null) reverse.setBlocked(true);

        a.setWreckTicksRemaining(WRECK_DURATION);
        if (b != null) b.setWreckTicksRemaining(WRECK_DURATION);
        wreckRecords.add(new WreckRecord(a, b, edge, reverse, WRECK_DURATION));

        rerouteAffectedVehicles(edge, reverse);
        spawnEmergencyVehicle(edge);
    }

    /** Find the reverse-direction edge for a given edge (B→A for A→B). */
    private Edge findReverseEdge(Edge edge) {
        for (Edge e : mapData.getOutgoingEdges(edge.getEnd())) {
            if (e.getEnd().equals(edge.getStart())) {
                return e;
            }
        }
        return null;
    }

    // ── Emergency vehicle spawning ─────────────────────────

    /**
     * Spawns an emergency vehicle at a distant border node and routes it
     * to the wreck site using the purest shortest path.
     */
    private void spawnEmergencyVehicle(Edge wreckEdge) {
        GridNode wreckNode = wreckEdge.getStart();
        GridNode spawnNode = findNearestBorderNode(wreckNode);
        if (spawnNode == null) return;

        // Find the full shortest path from the spawn node to the wreck node.
        List<Edge> fullPath = RoutingEngine.findEmergencyPath(spawnNode, wreckNode, mapData);

        Edge startEdge;
        List<Edge> remainingRoute;

        if (!fullPath.isEmpty()) {
            // Use the first edge of the calculated shortest path as the starting edge.
            startEdge = fullPath.get(0);
            remainingRoute = new ArrayList<>(fullPath.subList(1, fullPath.size()));
        } else {
            // Fallback (rare): if already at node or no path, pick any outgoing edge.
            List<Edge> outgoing = mapData.getOutgoingEdges(spawnNode);
            if (outgoing.isEmpty()) return;
            startEdge = outgoing.get(0);
            remainingRoute = new ArrayList<>();
        }

        Vehicle ev = new Vehicle(startEdge, 2.5);
        ev.setEmergency(true);
        ev.setTargetWreckEdge(wreckEdge);
        ev.setDestination(wreckNode);
        ev.setRoute(remainingRoute);
        vehicles.add(ev);
    }

    private GridNode findNearestBorderNode(GridNode target) {
        GridNode best = null;
        double bestDist = Double.MAX_VALUE;
        int maxRow = mapData.getRows() - 1;
        int maxCol = mapData.getCols() - 1;
        for (GridNode n : mapData.getNodes()) {
            boolean isBorder = n.getGridRow() == 0 || n.getGridRow() == maxRow
                            || n.getGridCol() == 0 || n.getGridCol() == maxCol;
            if (!isBorder) continue;
            double d = Math.hypot(n.getX() - target.getX(), n.getY() - target.getY());
            // Pick a border node that is at least some distance away for visual effect
            if (d > 80 && d < bestDist) { bestDist = d; best = n; }
        }
        // fallback: any border node
        if (best == null) {
            for (GridNode n : mapData.getNodes()) {
                boolean isBorder = n.getGridRow() == 0 || n.getGridRow() == maxRow
                                || n.getGridCol() == 0 || n.getGridCol() == maxCol;
                if (isBorder) { best = n; break; }
            }
        }
        return best;
    }

    /**
     * Called externally (e.g. right-click roadblock) to simulate a collision
     * at the given (x,y).  Places a single wrecked "roadblock" vehicle.
     */
    public void triggerCollision(double x, double y) {
        Edge edge = findNearestEdge(x, y);
        if (edge == null || edge.isBlocked()) return;
        // spawn a stationary wrecked vehicle at that spot
        Vehicle wreck = new Vehicle(edge, 0);
        double dx = edge.getEnd().getX() - edge.getStart().getX();
        double dy = edge.getEnd().getY() - edge.getStart().getY();
        double lenSq = dx * dx + dy * dy;
        if (lenSq > 0) {
            double t = ((x - edge.getStart().getX()) * dx
                      + (y - edge.getStart().getY()) * dy) / lenSq;
            wreck.setProgress(Math.max(0.05, Math.min(0.95, t)));
        }
        vehicles.add(wreck);
        applyCollision(wreck, null, edge);
    }

    /**
     * Reroute all ACTIVE vehicles affected by the blocked edge(s).
     * This covers both:
     *  - Vehicles whose FUTURE route includes the blocked edge
     *  - Vehicles CURRENTLY on the blocked edge (stranded)
     */
    private void rerouteAffectedVehicles(Edge blocked, Edge blockedReverse) {
        for (Vehicle v : vehicles) {
            if (v.isWrecked()) continue;

            // Case 1: Vehicle is currently ON the blocked edge → reroute from the far end
            if (v.getCurrentEdge() == blocked || v.getCurrentEdge() == blockedReverse) {
                GridNode escape = v.getCurrentEdge().getEnd();
                GridNode dest = v.getDestination();
                if (dest != null) {
                    List<Edge> newRoute = v.isEmergency()
                            ? RoutingEngine.findEmergencyPath(escape, dest, mapData)
                            : RoutingEngine.findTrafficAwarePath(escape, dest, mapData);
                    v.setRoute(newRoute);
                }
                continue;
            }

            // Case 2: Vehicle's future route includes the blocked edge
            for (Edge re : v.getRemainingRoute()) {
                if (re == blocked || re == blockedReverse) {
                    GridNode origin = v.getCurrentEdge().getEnd();
                    GridNode dest = v.getDestination();
                    if (dest != null) {
                        List<Edge> newRoute = v.isEmergency()
                                ? RoutingEngine.findEmergencyPath(origin, dest, mapData)
                                : RoutingEngine.findTrafficAwarePath(origin, dest, mapData);
                        v.setRoute(newRoute);
                    }
                    break;
                }
            }
        }
    }

    // ── Wreck cleanup ──────────────────────────────────────
    private void tickWreckCleanup() {
        Iterator<WreckRecord> it = wreckRecords.iterator();
        while (it.hasNext()) {
            WreckRecord wr = it.next();
            wr.ticksRemaining--;
            if (wr.ticksRemaining <= 0) {
                clearWreck(wr);
                it.remove();
            }
        }
    }

    /** Shared cleanup: despawn wrecked vehicles, unblock edges. */
    private void clearWreck(WreckRecord wr) {
        despawnVehicle(wr.vehicleA);
        if (wr.vehicleB != null) despawnVehicle(wr.vehicleB);
        wr.edge.setBlocked(false);
        if (wr.reverseEdge != null) wr.reverseEdge.setBlocked(false);
    }

    private void despawnVehicle(Vehicle v) {
        v.getCurrentEdge().removeVehicle(v);
        vehicles.remove(v);
    }

    private void updateVehicle(Vehicle v) {
        // Yielding vehicles are frozen in place
        if (v.isYielding()) {
            v.setStopped(true);
            return;
        }

        Edge edge = v.getCurrentEdge();
        GridNode endNode = edge.getEnd();

        // ── Red/yellow light stop logic (emergency vehicles ignore) ──
        boolean shouldStop = false;
        if (!v.isEmergency() && v.getProgress() > 0.82) {
            TrafficLight.State light = endNode.getTrafficLight().getState();
            if (light == TrafficLight.State.RED) {
                shouldStop = true;
            } else if (light == TrafficLight.State.YELLOW && v.getProgress() > 0.88) {
                shouldStop = true;
            }
        }
        v.setStopped(shouldStop);

        if (!shouldStop) {
            double edgeLen = edge.getDistance();
            double norm = (edgeLen > 0) ? v.getSpeed() / edgeLen : 0.01;
            v.setProgress(v.getProgress() + norm);
        }

        // ── Reached end of current edge ────────────────────
        if (v.getProgress() >= 1.0) {
            edge.removeVehicle(v);

            if (v.hasNextEdge()) {
                Edge next = v.getNextRouteEdge();
                v.advanceRoute();
                v.setCurrentEdge(next);
                v.setProgress(0.0);
                next.addVehicle(v);
            } else if (v.isEmergency()) {
                // ── Emergency arrived → clear the wreck ────
                handleEmergencyArrival(v);
                return;
            } else {
                // Route exhausted → pick new destination
                List<Edge> outgoing = mapData.getOutgoingEdges(endNode);
                if (!outgoing.isEmpty()) {
                    Edge next = outgoing.get(RNG.nextInt(outgoing.size()));
                    v.setCurrentEdge(next);
                    v.setProgress(0.0);
                    next.addVehicle(v);
                    assignRandomDestination(v);
                }
            }
        }
    }

    /** Emergency vehicle arrived at wreck: clear wrecked cars, unblock, despawn self. */
    private void handleEmergencyArrival(Vehicle ev) {
        Edge target = ev.getTargetWreckEdge();
        if (target != null) {
            Iterator<WreckRecord> it = wreckRecords.iterator();
            while (it.hasNext()) {
                WreckRecord wr = it.next();
                if (wr.edge == target) {
                    clearWreck(wr);
                    it.remove();
                    break;
                }
            }
        }
        // Remove the emergency vehicle itself (clean up from its edge too)
        ev.getCurrentEdge().removeVehicle(ev);
        vehicles.remove(ev);
    }

    // ── Hit-testing helpers ────────────────────────────────
    public Edge findNearestEdge(double x, double y) {
        Edge best = null;
        double bestDist = Double.MAX_VALUE;
        for (Edge e : mapData.getEdges()) {
            double d = ptSegDist(x, y,
                    e.getStart().getX(), e.getStart().getY(),
                    e.getEnd().getX(), e.getEnd().getY());
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    public GridNode findNearestNode(double x, double y, double maxDist) {
        GridNode best = null;
        double bestDist = maxDist;
        for (GridNode n : mapData.getNodes()) {
            double d = Math.hypot(n.getX() - x, n.getY() - y);
            if (d < bestDist) { bestDist = d; best = n; }
        }
        return best;
    }

    public Vehicle findNearestVehicle(double x, double y, double maxDist) {
        Vehicle best = null;
        double bestDist = maxDist;
        for (Vehicle v : vehicles) {
            double d = Math.hypot(v.getCurrentX() - x, v.getCurrentY() - y);
            if (d < bestDist) { bestDist = d; best = v; }
        }
        return best;
    }

    private static double ptSegDist(double px, double py,
                                     double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }
}
