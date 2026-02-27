package com.sphererelaxator.mesh;

import com.sphererelaxator.io.MeshDocument;
import com.sphererelaxator.io.TileDto;
import com.sphererelaxator.io.VertexDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MeshBuilder {
    private MeshBuilder() {
    }

    public static Mesh fromDocument(MeshDocument document) {
        if (document.vertices == null || document.vertices.isEmpty()) {
            throw new IllegalArgumentException("Input has no vertices.");
        }
        if (document.tiles == null || document.tiles.isEmpty()) {
            throw new IllegalArgumentException("Input has no tiles.");
        }

        int vertexCount = document.vertices.size();
        Vec3[] vertices = new Vec3[vertexCount];
        boolean[] fixed = new boolean[vertexCount];
        Map<Integer, Integer> idToIndex = new HashMap<>();

        for (int i = 0; i < document.vertices.size(); i++) {
            VertexDto v = document.vertices.get(i);
            idToIndex.put(v.id, i);
            vertices[i] = new Vec3(v.x, v.y, v.z);
            fixed[i] = v.fixed;
        }

        List<Tile> tiles = new ArrayList<>(document.tiles.size());
        Set<Long> edgeKeys = new LinkedHashSet<>();
        Set<Integer> pentagonVertices = new HashSet<>();

        for (TileDto t : document.tiles) {
            List<Integer> idx = new ArrayList<>(t.vertexIds.size());
            for (Integer vertexId : t.vertexIds) {
                Integer i = idToIndex.get(vertexId);
                if (i == null) {
                    throw new IllegalArgumentException("Unknown vertex id in tile " + t.id + ": " + vertexId);
                }
                idx.add(i);
            }
            Tile tile = new Tile(t.id, TileType.from(t.type), idx);
            tiles.add(tile);

            if (tile.type() == TileType.PENTAGON) {
                pentagonVertices.addAll(idx);
            }
            int n = idx.size();
            for (int i = 0; i < n; i++) {
                int a = idx.get(i);
                int b = idx.get((i + 1) % n);
                edgeKeys.add(edgeKey(a, b));
            }
        }

        List<int[]> edges = new ArrayList<>(edgeKeys.size());
        List<Set<Integer>> neighborSets = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            neighborSets.add(new HashSet<>());
        }

        for (long key : edgeKeys) {
            int a = (int) (key >>> 32);
            int b = (int) key;
            edges.add(new int[]{a, b});
            neighborSets.get(a).add(b);
            neighborSets.get(b).add(a);
        }

        int[][] neighbors = new int[vertexCount][];
        for (int i = 0; i < vertexCount; i++) {
            Set<Integer> set = neighborSets.get(i);
            neighbors[i] = set.stream().mapToInt(Integer::intValue).toArray();
        }

        return new Mesh(vertices, fixed, edges, tiles, neighbors, pentagonVertices);
    }

    public static MeshDocument toDocument(Mesh mesh, double radius) {
        MeshDocument out = new MeshDocument();
        out.radius = radius;

        Vec3[] vertices = mesh.vertices();
        for (int i = 0; i < vertices.length; i++) {
            Vec3 p = vertices[i];
            VertexDto v = new VertexDto();
            v.id = i;
            v.x = p.x();
            v.y = p.y();
            v.z = p.z();
            v.fixed = mesh.fixed()[i];
            out.vertices.add(v);
        }

        for (Tile t : mesh.tiles()) {
            TileDto dto = new TileDto();
            dto.id = t.id();
            dto.type = t.type().name();
            dto.vertexIds = new ArrayList<>(t.vertexIds());
            out.tiles.add(dto);
        }
        return out;
    }

    private static long edgeKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }
}
