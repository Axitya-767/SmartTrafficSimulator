# Smart Traffic Simulator

A Java Swing desktop app that simulates city traffic on a 6×6 grid of roads and intersections.
You can watch vehicles drive around, control traffic lights, cause accidents, and see emergency vehicles respond in real time.

---

## 🗺️ 1. The Road Grid

**What it does:**
Draws a city-style grid of intersections (nodes) connected by two-lane roads (edges).
Every connection is bidirectional — there is one road going left and a separate one going right.

**How it was implemented:**
The `MapData` class creates a 6×6 grid of `GridNode` objects arranged by row and column.
Each pair of adjacent nodes gets two `Edge` objects (one in each direction), stored in an
adjacency map so the engine can quickly look up "what roads lead out of this intersection?"

---

## 🚗 2. Vehicles

**What it does:**
Vehicles drive around the city following a planned route. Each vehicle has a colour, a speed,
and a destination node. They stay in the right lane and show a small dot that turns green
(moving), red (stopped at a light), or yellow (pulled over for an ambulance).

**How it was implemented:**
Each `Vehicle` stores its current road (`currentEdge`), how far along that road it is
(`progress`, from 0.0 to 1.0), and a list of upcoming roads to follow (`route`).
Every simulation tick, progress is incremented by `speed / roadLength`. When progress
reaches 1.0, the vehicle moves onto the next road in its list.

---

## 🚦 3. Traffic Lights

**What it does:**
Every intersection has its own traffic light that cycles Green → Yellow → Red → Green.
- **Green**: vehicles drive through freely
- **Yellow**: vehicles close to the intersection stop
- **Red**: vehicles stop and wait

Lights can be automated (cycle on their own) or manually clicked by the user to change state.
There's also a "Sync All Green" button to reset every light to green at once.

**How it was implemented:**
Each `GridNode` contains a `TrafficLight` state machine with three durations (180 ticks green,
50 ticks yellow, 180 ticks red). Lights across the grid are staggered by a phase offset
(`(row + col) % 3 × 70 ticks`) so adjacent intersections aren't all green at the same time.
In `updateVehicle()`, once a vehicle is 82% along its road, it checks the light at the far
end and sets `shouldStop = true` if it's red or late-yellow.

---

## 🧭 4. Dijkstra's Shortest Path (Smart Routing)

**What it does:**
Instead of driving randomly, every vehicle calculates the smartest route to its destination.
The routing engine finds the path that minimises a weighted "cost" — not just raw distance,
but also how congested roads are and what the traffic lights are doing.

**How it was implemented (`RoutingEngine.java`):**
Standard Dijkstra's algorithm with a priority queue. Three modes control which cost formula
is used per edge:

| Mode | Who uses it | Cost formula |
|------|------------|-------------|
| **CONGESTION** | Legacy / fallback | `distance + vehicles on road × 100` |
| **TRAFFIC_AWARE** | Normal vehicles | `congestion cost + traffic light delay penalty` |
| **EMERGENCY** | Ambulances | `pure distance` only |

The **traffic light penalty** in TRAFFIC_AWARE mode is dynamic:
- A freshly-turned-red light adds up to **270 cost units** (big detour)
- A light about to turn green adds **near zero** (worth risking)
- Yellow adds **80** (moderate, try to avoid)

This means vehicles naturally prefer green-light roads, behaving more like real GPS navigation.

---

## 💥 5. Vehicle Collisions

**What it does:**
When two vehicles get within 10 pixels of each other on the same road, a crash happens.
Both vehicles freeze as red X marks, the road segment turns bright red and is blocked,
and an ambulance is automatically dispatched.

**How it was implemented:**
Every tick, `detectCollisions()` compares all non-emergency, non-wrecked vehicle pairs on
the same edge. On a hit, `applyCollision()`:
1. Marks both vehicles as `WRECKED` (speed → 0)
2. Blocks the road **in both directions** (the normal A→B edge and the reverse B→A edge)
3. Reroutes all other vehicles that were planning to use the blocked road
4. Also reroutes vehicles already sitting on the blocked road (sends them toward the far end)
5. Spawns an emergency vehicle

You can also **right-click** any road to manually trigger a roadblock.

---

## 🚑 6. Emergency Vehicle (Ambulance)

**What it does:**
When a crash occurs, an ambulance spawns at the nearest border of the grid and races to the
accident site. Along the way it:
- Takes the geometrically shortest path (pure Dijkstra by distance — no detours for lights or traffic)
- Travels at 2.5× the speed of normal vehicles
- Forces all vehicles on its **current road AND the next 3 roads ahead** to pull over
- Causes all traffic lights on those same upcoming roads to turn **green** instantly
- On arrival: clears the wreck, unblocks the road in both directions, and disappears

**How it was implemented:**

*Spawning:* `spawnEmergencyVehicle()` finds the border node closest to the crash, then
runs `findEmergencyPath()` (pure-distance Dijkstra) from that border node **all the way
to the wreck node**. The very first edge of that path becomes the ambulance's starting road,
so it immediately moves in the right direction with no U-turns.

*Look-ahead yielding:* Every tick, `applyEmergencyYielding()` loops through the ambulance's
current edge and its next 3 route edges, setting `yielding = true` on all normal vehicles
found there. Yielding vehicles have their speed locked to 0.

*Green corridor:* `applyEmergencyGreenCorridor()` calls `syncToGreen()` on the traffic
light of every node along those same upcoming edges, so the ambulance never hits a red.

*Arrival:* When the ambulance finishes the last edge of its route, `handleEmergencyArrival()`
finds the matching wreck record, despawns the crashed vehicles, unblocks both road directions,
and removes the ambulance from the simulation.

---

## 🖥️ 7. Visual Rendering

**What it does:**
The map is rendered in a dark-mode style with:
- Roads drawn as layered strokes (border → surface → dashed centre line)
- Red heat overlay on congested roads (more cars = more red)
- Bright red flashing overlay + X marks on blocked roads
- Intersection nodes with coloured glow rings matching their traffic light state
- Vehicles as glowing circles with a small status dot (green/red/yellow)
- Ambulances as a flashing red/blue diamond with a cross symbol
- Wrecked vehicles as red X marks
- Selected vehicle's full planned route drawn as a glowing cyan highlight

**How it was implemented:**
`MapPanel.paintComponent()` does six rendering passes in order (grid → roads → route
highlight → intersections → vehicles → HUD) using Java2D with anti-aliasing.
A `renderTick` counter increments each frame to drive the ambulance siren flash animation
(`(renderTick / 8) % 2`).

---

## 🖱️ 8. User Interactions

| Action | What happens |
|--------|-------------|
| **Click empty road** | Spawns a new vehicle at that spot |
| **Click a vehicle** | Selects it — shows its route in cyan |
| **Click a node (vehicle selected)** | Reroutes that vehicle to the clicked node |
| **Click a node (nothing selected)** | Manually cycles that intersection's traffic light |
| **Right-click any road** | Places a manual roadblock (triggers full crash response) |
| **Start / Pause button** | Starts or pauses the simulation |
| **Automate Lights button** | Toggles all lights into automatic cycling mode |
| **Sync All Green button** | Resets every light to green instantly |

---

## 📁 Files at a Glance

| File | Role |
|------|------|
| `Main.java` | Entry point — builds the 6×6 grid, seeds 10 vehicles, launches the window |
| `MapData.java` | Creates all nodes and edges; holds the adjacency map |
| `GridNode.java` | A single intersection with pixel position and traffic light |
| `TrafficLight.java` | Green/Yellow/Red state machine with configurable durations |
| `Edge.java` | A directed road segment with distance, vehicle list, and 3 weight methods |
| `Vehicle.java` | A vehicle: position, speed, route, state (active/wrecked/emergency) |
| `RoutingEngine.java` | Dijkstra's algorithm with CONGESTION / TRAFFIC_AWARE / EMERGENCY modes |
| `SimulationEngine.java` | Core game loop: lights, yielding, movement, collisions, emergency response |
| `MapPanel.java` | Draws everything; handles mouse clicks |
| `ControlPanel.java` | Bottom button bar (Start/Pause, Automate, Sync) |
| `MainFrame.java` | Window frame; wires the 60 fps Swing timer to `engine.tick()` |
