package com.sphererelaxator.mesh;

import java.util.List;

public record Tile(int id, TileType type, List<Integer> vertexIds) {
}
