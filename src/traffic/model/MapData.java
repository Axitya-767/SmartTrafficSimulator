package traffic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a mathematical rows×cols grid of GridNodes connected by
 * bidirectional Edges.  Also maintains an adjacency map for fast lookups.
 */
public class MapData {

    private final List<GridNode> nodes;
    private final List<Edge> edges;
    private final Map<GridNode, List<Edge>> adjacency;
    private final int rows;
    private final int cols;

    /**
     * @param rows     number of rows in the grid
     * @param cols     number of columns in the grid
     * @param startX   pixel X of the top-left node
     * @param startY   pixel Y of the top-left node
     * @param spacingX horizontal pixel spacing between columns
     * @param spacingY vertical pixel spacing between rows
     */
    public MapData(int rows, int cols, int startX, int startY, int spacingX, int spacingY) {
        this.rows = rows;
        this.cols = cols;
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.adjacency = new HashMap<>();

        // ── Create nodes ───────────────────────────────────
        GridNode[][] grid = new GridNode[rows][cols];
        int id = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = startX + c * spacingX;
                double y = startY + r * spacingY;
                grid[r][c] = new GridNode(id++, x, y, r, c);
                nodes.add(grid[r][c]);
                adjacency.put(grid[r][c], new ArrayList<>());
            }
        }

        // ── Horizontal edges (bidirectional) ───────────────
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                link(grid[r][c], grid[r][c + 1]);
            }
        }

        // ── Vertical edges (bidirectional) ─────────────────
        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols; c++) {
                link(grid[r][c], grid[r + 1][c]);
            }
        }
    }

    private void link(GridNode a, GridNode b) {
        Edge ab = new Edge(a, b);
        Edge ba = new Edge(b, a);
        edges.add(ab);
        edges.add(ba);
        adjacency.get(a).add(ab);
        adjacency.get(b).add(ba);
    }

    // ── Accessors ──────────────────────────────────────────
    public List<GridNode> getNodes() { return Collections.unmodifiableList(nodes); }
    public List<Edge>     getEdges() { return Collections.unmodifiableList(edges); }
    public int getRows() { return rows; }
    public int getCols() { return cols; }

    /** Returns all outgoing edges from the given node. */
    public List<Edge> getOutgoingEdges(GridNode node) {
        return adjacency.getOrDefault(node, Collections.emptyList());
    }
}
