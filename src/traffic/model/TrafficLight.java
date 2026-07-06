package traffic.model;

/**
 * Independent traffic light state machine that cycles Green → Yellow → Red → Green.
 * Can be automated (auto-cycles on tick) or manually controlled.
 */
public class TrafficLight {

    public enum State { GREEN, YELLOW, RED }

    private State state;
    private int ticksInState;
    private final int greenDuration;
    private final int yellowDuration;
    private final int redDuration;
    private boolean automated;

    public TrafficLight() {
        this(0);
    }

    /**
     * @param phaseOffset initial ticksInState to stagger lights across the grid
     */
    public TrafficLight(int phaseOffset) {
        this.state = State.GREEN;
        this.ticksInState = phaseOffset;
        this.greenDuration = 180;   // ~3 s at 60 fps
        this.yellowDuration = 50;   // ~0.8 s
        this.redDuration = 180;
        this.automated = false;
    }

    /** Advance the state machine by one tick (only when automated). */
    public void tick() {
        if (!automated) return;
        ticksInState++;
        switch (state) {
            case GREEN:
                if (ticksInState >= greenDuration) { state = State.YELLOW; ticksInState = 0; }
                break;
            case YELLOW:
                if (ticksInState >= yellowDuration) { state = State.RED; ticksInState = 0; }
                break;
            case RED:
                if (ticksInState >= redDuration) { state = State.GREEN; ticksInState = 0; }
                break;
        }
    }

    /** Manually advance to the next state in the cycle. */
    public void cycleManual() {
        switch (state) {
            case GREEN:  state = State.YELLOW; break;
            case YELLOW: state = State.RED;    break;
            case RED:    state = State.GREEN;  break;
        }
        ticksInState = 0;
    }

    /** Reset this light to GREEN (used by Sync All). */
    public void syncToGreen() {
        state = State.GREEN;
        ticksInState = 0;
    }

    // ── Accessors ──────────────────────────────────────────
    public State   getState()       { return state; }
    public boolean isAutomated()    { return automated; }
    public void    setAutomated(boolean automated) { this.automated = automated; }
    public int     getTicksInState() { return ticksInState; }
}
