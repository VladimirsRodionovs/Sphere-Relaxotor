package com.sphererelaxator.mesh;

import java.util.List;
import java.util.Set;

public class Mesh {
    private final Vec3[] vertices;
    private final boolean[] fixed;
    private final List<int[]> edges;
    private final List<Tile> tiles;
    private final int[][] neighbors;
    private final Set<Integer> pentagonVertices;

    public Mesh(Vec3[] vertices,
                boolean[] fixed,
                List<int[]> edges,
                List<Tile> tiles,
                int[][] neighbors,
                Set<Integer> pentagonVertices) {
        this.vertices = vertices;
        this.fixed = fixed;
        this.edges = edges;
        this.tiles = tiles;
        this.neighbors = neighbors;
        this.pentagonVertices = pentagonVertices;
    }

    public Vec3[] vertices() {
        return vertices;
    }

    public boolean[] fixed() {
        return fixed;
    }

    public List<int[]> edges() {
        return edges;
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public int[][] neighbors() {
        return neighbors;
    }

    public Set<Integer> pentagonVertices() {
        return pentagonVertices;
    }
}
