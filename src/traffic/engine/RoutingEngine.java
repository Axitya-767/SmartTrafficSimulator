package traffic.engine;

import traffic.model.Edge;
import traffic.model.GridNode;
import traffic.model.MapData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implements Dijkstra's shortest-path algorithm over the traffic grid.
 *
 * Supports three weight modes:
 *   CONGESTION      – distance + vehicle-count penalty (original behaviour)
 *   TRAFFIC_AWARE   – congestion + traffic-light delay penalty
 *   EMERGENCY       – pure distance (ambulances ignore lights & congestion)
 */
public final class RoutingEngine {

    /** Controls which edge-weight function Dijkstra uses. */
    public enum WeightMode {
        /** distance + vehicles_on_edge × 100 */
        CONGESTION,
        /** CONGESTION + traffic-light delay at the edge's end node */
        TRAFFIC_AWARE,
        /** pure physical distance (emergency vehicles) */
        EMERGENCY
    }

    private RoutingEngine() { /* utility class */ }

    // ── Convenience wrappers ──────────────────────────────

    /**
     * Original API (backward-compatible) – uses CONGESTION weights.
     */
    public static List<Edge> findShortestPath(GridNode source, GridNode destination, MapData mapData) {
        return findShortestPath(source, destination, mapData, WeightMode.CONGESTION);
    }

    /**
     * Traffic-light-aware shortest path for normal vehicles.
     * Penalises edges whose end-node light is RED or YELLOW,
     * encouraging the vehicle to take routes through green lights.
     */
    public static List<Edge> findTrafficAwarePath(GridNode source, GridNode destination, MapData mapData) {
        return findShortestPath(source, destination, mapData, WeightMode.TRAFFIC_AWARE);
    }

    /**
     * Pure-distance shortest path for emergency vehicles.
     * Ignores congestion and traffic-light state since ambulances
     * blow through reds and force other vehicles to yield.
     */
    public static List<Edge> findEmergencyPath(GridNode source, GridNode destination, MapData mapData) {
        return findShortestPath(source, destination, mapData, WeightMode.EMERGENCY);
    }

    // ── Core Dijkstra ─────────────────────────────────────

    /**
     * Finds the shortest (lowest-weight) path from source to destination
     * according to the specified weight mode.
     *
     * @return ordered list of edges forming the path, or empty list if
     *         source == destination or no path exists.
     */
    public static List<Edge> findShortestPath(GridNode source, GridNode destination,
                                               MapData mapData, WeightMode mode) {
        if (source.equals(destination)) return Collections.emptyList();

        Map<GridNode, Double> dist    = new HashMap<>();
        Map<GridNode, Edge>   prevEdge = new HashMap<>();
        Set<GridNode>         visited  = new HashSet<>();

        for (GridNode node : mapData.getNodes()) {
            dist.put(node, Double.MAX_VALUE);
        }
        dist.put(source, 0.0);

        PriorityQueue<GridNode> pq = new PriorityQueue<>(
                Comparator.comparingDouble(n -> dist.getOrDefault(n, Double.MAX_VALUE))
        );
        pq.add(source);

        while (!pq.isEmpty()) {
            GridNode u = pq.poll();
            if (visited.contains(u)) continue;
            visited.add(u);
            if (u.equals(destination)) break;

            for (Edge edge : mapData.getOutgoingEdges(u)) {
                GridNode v = edge.getEnd();
                if (visited.contains(v)) continue;

                double w = edgeWeight(edge, mode);
                double alt = dist.get(u) + w;
                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prevEdge.put(v, edge);
                    pq.add(v);   // lazy-add; duplicates filtered by visited set
                }
            }
        }

        // ── Reconstruct path ───────────────────────────────
        List<Edge> path = new ArrayList<>();
        GridNode cur = destination;
        while (prevEdge.containsKey(cur)) {
            Edge e = prevEdge.get(cur);
            path.add(0, e);
            cur = e.getStart();
        }
        return path;
    }

    /** Select the appropriate weight function based on mode. */
    private static double edgeWeight(Edge edge, WeightMode mode) {
        switch (mode) {
            case TRAFFIC_AWARE: return edge.getTrafficAwareWeight();
            case EMERGENCY:     return edge.getEmergencyWeight();
            case CONGESTION:
            default:            return edge.getWeight();
        }
    }
}
