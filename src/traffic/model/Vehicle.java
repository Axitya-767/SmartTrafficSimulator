package traffic.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A vehicle that travels along edges following a pre-computed route.
 * Tracks current edge, progress (0…1), speed, route, and selection state.
 */
public class Vehicle {

    public enum State { ACTIVE, WRECKED }

    private static final Random RNG = new Random();
    private static int nextId = 0;

    private final int id;
    private Edge currentEdge;
    private double progress;      // 0.0 = at start node, 1.0 = at end node
    private double speed;         // pixels per tick
    private final Color color;

    private List<Edge> route;     // edges to follow AFTER currentEdge
    private int routeIndex;
    private GridNode destination;
    private boolean selected;
    private boolean stopped;
    private State state;
    private int wreckTicksRemaining;
    private boolean emergency;
    private boolean yielding;
    private Edge targetWreckEdge;   // only used by emergency vehicles

    public Vehicle(Edge startEdge, double speed) {
        this.id = nextId++;
        this.currentEdge = startEdge;
        this.progress = 0.0;
        this.speed = speed;
        this.color = generateColor();
        this.route = new ArrayList<>();
        this.routeIndex = 0;
        this.destination = null;
        this.selected = false;
        this.stopped = false;
        this.state = State.ACTIVE;
        this.wreckTicksRemaining = 0;
        this.emergency = false;
        this.yielding = false;
        this.targetWreckEdge = null;
        startEdge.addVehicle(this);
    }

    private static Color generateColor() {
        float hue = RNG.nextFloat();
        return Color.getHSBColor(hue, 0.65f + RNG.nextFloat() * 0.2f, 0.90f + RNG.nextFloat() * 0.10f);
    }

    // ── Position interpolation ─────────────────────────────
    public double getCurrentX() {
        double sx = currentEdge.getStart().getX();
        double ex = currentEdge.getEnd().getX();
        return sx + (ex - sx) * progress;
    }

    public double getCurrentY() {
        double sy = currentEdge.getStart().getY();
        double ey = currentEdge.getEnd().getY();
        return sy + (ey - sy) * progress;
    }

    // ── Route helpers ──────────────────────────────────────
    public boolean hasNextEdge() {
        return route != null && routeIndex < route.size();
    }

    public Edge getNextRouteEdge() {
        return hasNextEdge() ? route.get(routeIndex) : null;
    }

    public void advanceRoute() {
        routeIndex++;
    }

    /** Returns route edges from current routeIndex onward (defensive copy). */
    public List<Edge> getRemainingRoute() {
        if (route == null || routeIndex >= route.size()) return new ArrayList<>();
        return new ArrayList<>(route.subList(routeIndex, route.size()));
    }

    // ── Accessors ──────────────────────────────────────────
    public int       getId()            { return id; }
    public Edge      getCurrentEdge()   { return currentEdge; }
    public void      setCurrentEdge(Edge e) { this.currentEdge = e; }
    public double    getProgress()      { return progress; }
    public void      setProgress(double p)  { this.progress = p; }
    public double    getSpeed() {
        if (state == State.WRECKED || yielding) return 0;
        return emergency ? speed * 2.5 : speed;
    }
    public void      setSpeed(double s) { this.speed = s; }
    public Color     getColor()         { return color; }
    public List<Edge> getRoute()        { return route; }
    public void      setRoute(List<Edge> r) { this.route = r; this.routeIndex = 0; }
    public int       getRouteIndex()    { return routeIndex; }
    public GridNode  getDestination()   { return destination; }
    public void      setDestination(GridNode d) { this.destination = d; }
    public boolean   isSelected()       { return selected; }
    public void      setSelected(boolean s) { this.selected = s; }
    public boolean   isStopped()        { return stopped; }
    public void      setStopped(boolean s)  { this.stopped = s; }
    public State     getState()         { return state; }
    public boolean   isWrecked()        { return state == State.WRECKED; }
    public int       getWreckTicksRemaining() { return wreckTicksRemaining; }
    public void      setWreckTicksRemaining(int t) { this.wreckTicksRemaining = t; }
    public boolean   isEmergency()      { return emergency; }
    public void      setEmergency(boolean e) { this.emergency = e; }
    public boolean   isYielding()       { return yielding; }
    public void      setYielding(boolean y)  { this.yielding = y; }
    public Edge      getTargetWreckEdge()    { return targetWreckEdge; }
    public void      setTargetWreckEdge(Edge e) { this.targetWreckEdge = e; }

    /** Mark this vehicle as wrecked (speed becomes 0). */
    public void wreck() {
        this.state = State.WRECKED;
        this.stopped = true;
    }

    @Override
    public String toString() { return "Vehicle#" + id; }
}
