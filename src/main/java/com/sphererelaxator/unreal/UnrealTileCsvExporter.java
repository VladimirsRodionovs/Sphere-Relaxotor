package com.sphererelaxator.unreal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sphererelaxator.mesh.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UnrealTileCsvExporter {
    private static final Pattern VECTOR3_PATTERN = Pattern.compile("\\(X=([-0-9.Ee]+),Y=([-0-9.Ee]+),Z=([-0-9.Ee]+)\\)");
    private static final Pattern VECTOR2_PATTERN = Pattern.compile("\\(X=([-0-9.Ee]+),Y=([-0-9.Ee]+)\\)");
    private static final Pattern TANGENT_PATTERN = Pattern.compile(
            "\\(TangentX=\\(X=([-0-9.Ee]+),Y=([-0-9.Ee]+),Z=([-0-9.Ee]+)\\),bFlipTangentY=(True|False)\\)"
    );

    public void export(JsonNode root, Path outputPrefix) throws IOException {
        if (!UnrealFormatProcessor.isUnrealFormat(root)) {
            throw new IllegalArgumentException("Input is not Unreal-like format (expected array with Vertiches/Triangles).");
        }
        ArrayNode items = (ArrayNode) root;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            if (!(item instanceof ObjectNode node)) {
                continue;
            }
            String suffix = items.size() > 1 ? "_item" + i : "";
            exportOne(node, appendSuffix(outputPrefix, suffix));
        }
    }

    public void exportRaw(Path outputPrefix,
                          List<Vec3> vertices,
                          int[] triangles,
                          List<Vec3> normals,
                          List<double[]> uvs,
                          List<Vec3> tangents) throws IOException {
        List<Vec2> uvList = null;
        if (uvs != null) {
            uvList = new ArrayList<>(uvs.size());
            for (double[] uv : uvs) {
                if (uv == null || uv.length < 2) {
                    uvList.add(null);
                } else {
                    uvList.add(new Vec2(uv[0], uv[1]));
                }
            }
        }

        List<TangentData> tangentList = null;
        if (tangents != null) {
            tangentList = new ArrayList<>(tangents.size());
            for (Vec3 tangent : tangents) {
                tangentList.add(tangent == null ? null : new TangentData(tangent, false));
            }
        }

        TileBuildData data = buildTiles(vertices, triangles, normals, uvList, tangentList);
        writeTilesCsv(outputPrefix, data);
        writeTileCentersCsv(outputPrefix, data);
        writeTileVerticesCsv(outputPrefix, data);
        writeTileVertexPositionsCsv(outputPrefix, data);
        writeTileVertexNormalsCsv(outputPrefix, data);
        writeTileVertexUvsCsv(outputPrefix, data);
        writeTileVertexTangentsCsv(outputPrefix, data);
        writeTileTrianglesCsv(outputPrefix, data);
        writeTriangleToTileCsv(outputPrefix, data);
        writeTileNeighborsCsv(outputPrefix, data);
    }

    private void exportOne(ObjectNode node, Path prefix) throws IOException {
        ArrayNode vertArray = requiredArray(node, "Vertiches");
        ArrayNode triArray = requiredArray(node, "Triangles");
        ArrayNode uvArray = tryUvArray(node);
        ArrayNode normalArray = node.has("Normals") && node.get("Normals").isArray()
                ? (ArrayNode) node.get("Normals")
                : null;
        ArrayNode tangentArray = node.has("Tangents") && node.get("Tangents").isArray()
                ? (ArrayNode) node.get("Tangents")
                : null;

        List<Vec3> vertices = parseVertices(vertArray);
        List<Vec3> normals = normalArray != null ? parseVertices(normalArray) : null;
        int[] triangles = parseTriangles(triArray);
        List<Vec2> uvs = uvArray != null ? parseUvs(uvArray) : null;
        List<TangentData> tangents = tangentArray != null ? parseTangents(tangentArray) : null;

        TileBuildData data = buildTiles(vertices, triangles, normals, uvs, tangents);
        writeTilesCsv(prefix, data);
        writeTileCentersCsv(prefix, data);
        writeTileVerticesCsv(prefix, data);
        writeTileVertexPositionsCsv(prefix, data);
        writeTileVertexNormalsCsv(prefix, data);
        writeTileVertexUvsCsv(prefix, data);
        writeTileVertexTangentsCsv(prefix, data);
        writeTileTrianglesCsv(prefix, data);
        writeTriangleToTileCsv(prefix, data);
        writeTileNeighborsCsv(prefix, data);
    }

    private static Path appendSuffix(Path prefix, String suffix) {
        Path parent = prefix.getParent();
        String base = prefix.getFileName() == null ? "tiles" : prefix.getFileName().toString();
        String name = base + suffix;
        return parent == null ? Path.of(name) : parent.resolve(name);
    }

    private static TileBuildData buildTiles(List<Vec3> vertices,
                                            int[] triangles,
                                            List<Vec3> normals,
                                            List<Vec2> uvs,
                                            List<TangentData> tangents) {
        List<TileInfo> tiles = new ArrayList<>();
        int triangleCount = triangles.length / 3;
        int[] triangleToTile = new int[triangleCount];
        for (int i = 0; i < triangleToTile.length; i++) {
            triangleToTile[i] = -1;
        }

        int i = 0;
        int triIndex = 0;
        int tileId = 0;
        while (i + 2 < triangles.length) {
            int center = triangles[i];
            List<int[]> pairs = new ArrayList<>();
            List<Integer> tileTriangles = new ArrayList<>();
            while (i + 2 < triangles.length && triangles[i] == center) {
                pairs.add(new int[]{triangles[i + 1], triangles[i + 2]});
                tileTriangles.add(triIndex);
                triangleToTile[triIndex++] = tileId;
                i += 3;
            }
            List<Integer> ring = buildRingFromPairs(pairs);
            String type = ring.size() == 5 ? "PENT" : "HEX";
            Vec3 centerPos = vertices.get(center);
            tiles.add(new TileInfo(tileId, type, center, centerPos, ring, tileTriangles));
            tileId++;
        }

        Map<String, List<Integer>> edgeToTiles = new LinkedHashMap<>();
        for (TileInfo t : tiles) {
            int n = t.ringVertexIndices.size();
            for (int k = 0; k < n; k++) {
                int a = t.ringVertexIndices.get(k);
                int b = t.ringVertexIndices.get((k + 1) % n);
                String key = edgeKeyByPosition(vertices.get(a), vertices.get(b));
                edgeToTiles.computeIfAbsent(key, x -> new ArrayList<>(2)).add(t.tileId);
            }
        }

        Map<Integer, Set<Integer>> neighbors = new HashMap<>();
        for (TileInfo t : tiles) {
            neighbors.put(t.tileId, new HashSet<>());
        }
        for (List<Integer> sharedRaw : edgeToTiles.values()) {
            List<Integer> shared = sharedRaw.stream().distinct().toList();
            if (shared.size() < 2) {
                continue;
            }
            for (int a = 0; a < shared.size(); a++) {
                for (int b = a + 1; b < shared.size(); b++) {
                    int ta = shared.get(a);
                    int tb = shared.get(b);
                    neighbors.get(ta).add(tb);
                    neighbors.get(tb).add(ta);
                }
            }
        }
        return new TileBuildData(tiles, triangleToTile, neighbors, triangles, vertices, normals, uvs, tangents);
    }

    private static String edgeKeyByPosition(Vec3 a, Vec3 b) {
        String ka = String.format(Locale.US, "%.6f,%.6f,%.6f", a.x(), a.y(), a.z());
        String kb = String.format(Locale.US, "%.6f,%.6f,%.6f", b.x(), b.y(), b.z());
        return ka.compareTo(kb) <= 0 ? ka + "|" + kb : kb + "|" + ka;
    }

    private static List<Integer> buildRingFromPairs(List<int[]> pairs) {
        if (pairs.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> nextByCurrent = new HashMap<>();
        for (int[] p : pairs) {
            nextByCurrent.put(p[1], p[0]);
        }
        int start = pairs.get(0)[1];
        List<Integer> ring = new ArrayList<>();
        ring.add(start);
        int current = start;
        for (int k = 0; k < pairs.size(); k++) {
            Integer next = nextByCurrent.get(current);
            if (next == null || next == start) {
                break;
            }
            ring.add(next);
            current = next;
        }
        return ring;
    }

    private static void writeTilesCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,type,center_vertex,vertex_count,triangle_count");
        for (TileInfo t : data.tiles) {
            lines.add(String.format(
                    Locale.US,
                    "%d,%s,%d,%d,%d",
                    t.tileId, t.type, t.centerVertex,
                    t.ringVertexIndices.size(), t.triangleIndices.size()
            ));
        }
        Files.write(Path.of(prefix + "_tiles.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileCentersCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,center_vertex,center_x,center_y,center_z");
        for (TileInfo t : data.tiles) {
            lines.add(String.format(
                    Locale.US,
                    "%d,%d,%.9f,%.9f,%.9f",
                    t.tileId, t.centerVertex, t.center.x(), t.center.y(), t.center.z()
            ));
        }
        Files.write(Path.of(prefix + "_tile_centers.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileVerticesCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,vertex_order,vertex_index");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.ringVertexIndices.size(); i++) {
                int vi = t.ringVertexIndices.get(i);
                lines.add(String.format(Locale.US, "%d,%d,%d", t.tileId, i, vi));
            }
        }
        Files.write(Path.of(prefix + "_tile_vertices.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileVertexPositionsCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,vertex_order,vertex_index,x,y,z");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.ringVertexIndices.size(); i++) {
                int vi = t.ringVertexIndices.get(i);
                Vec3 p = data.vertices.get(vi);
                lines.add(String.format(
                        Locale.US, "%d,%d,%d,%.9f,%.9f,%.9f",
                        t.tileId, i, vi, p.x(), p.y(), p.z()
                ));
            }
        }
        Files.write(Path.of(prefix + "_tile_vertex_positions.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileVertexNormalsCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,vertex_order,vertex_index,nx,ny,nz");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.ringVertexIndices.size(); i++) {
                int vi = t.ringVertexIndices.get(i);
                Vec3 n = data.normals != null && vi < data.normals.size()
                        ? data.normals.get(vi)
                        : data.vertices.get(vi).normalize();
                lines.add(String.format(
                        Locale.US, "%d,%d,%d,%.9f,%.9f,%.9f",
                        t.tileId, i, vi, n.x(), n.y(), n.z()
                ));
            }
        }
        Files.write(Path.of(prefix + "_tile_vertex_normals.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileVertexUvsCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,vertex_order,vertex_index,u,v");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.ringVertexIndices.size(); i++) {
                int vi = t.ringVertexIndices.get(i);
                Vec2 uv = data.uvs != null && vi < data.uvs.size() ? data.uvs.get(vi) : null;
                String u = uv == null ? "" : String.format(Locale.US, "%.9f", uv.u);
                String v = uv == null ? "" : String.format(Locale.US, "%.9f", uv.v);
                lines.add(String.format(Locale.US, "%d,%d,%d,%s,%s", t.tileId, i, vi, u, v));
            }
        }
        Files.write(Path.of(prefix + "_tile_vertex_uv.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileVertexTangentsCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,vertex_order,vertex_index,tangent_x,tangent_y,tangent_z,flip_y");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.ringVertexIndices.size(); i++) {
                int vi = t.ringVertexIndices.get(i);
                TangentData tan = data.tangents != null && vi < data.tangents.size() ? data.tangents.get(vi) : null;
                if (tan == null) {
                    lines.add(String.format(Locale.US, "%d,%d,%d,,,,", t.tileId, i, vi));
                } else {
                    lines.add(String.format(
                            Locale.US,
                            "%d,%d,%d,%.9f,%.9f,%.9f,%s",
                            t.tileId, i, vi, tan.tangent.x(), tan.tangent.y(), tan.tangent.z(), tan.flipY ? "1" : "0"
                    ));
                }
            }
        }
        Files.write(Path.of(prefix + "_tile_vertex_tangents.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileTrianglesCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,tile_triangle_order,triangle_index,v0,v1,v2");
        for (TileInfo t : data.tiles) {
            for (int i = 0; i < t.triangleIndices.size(); i++) {
                int tri = t.triangleIndices.get(i);
                int base = tri * 3;
                lines.add(String.format(
                        Locale.US, "%d,%d,%d,%d,%d,%d",
                        t.tileId, i, tri, data.triangles[base], data.triangles[base + 1], data.triangles[base + 2]
                ));
            }
        }
        Files.write(Path.of(prefix + "_tile_triangles.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTriangleToTileCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("triangle_index,tile_id");
        for (int i = 0; i < data.triangleToTile.length; i++) {
            lines.add(i + "," + data.triangleToTile[i]);
        }
        Files.write(Path.of(prefix + "_triangle_to_tile.csv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeTileNeighborsCsv(Path prefix, TileBuildData data) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("tile_id,neighbor_tile_id");
        for (TileInfo t : data.tiles) {
            List<Integer> sorted = data.neighbors.get(t.tileId).stream().sorted().toList();
            for (int n : sorted) {
                lines.add(t.tileId + "," + n);
            }
        }
        Files.write(Path.of(prefix + "_tile_neighbors.csv"), lines, StandardCharsets.UTF_8);
    }

    private static ArrayNode requiredArray(ObjectNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Expected array field: " + key);
        }
        return (ArrayNode) value;
    }

    private static ArrayNode tryUvArray(ObjectNode node) {
        if (node.has("UV0") && node.get("UV0").isArray()) {
            return (ArrayNode) node.get("UV0");
        }
        if (node.has("UV") && node.get("UV").isArray()) {
            return (ArrayNode) node.get("UV");
        }
        if (node.has("UVs") && node.get("UVs").isArray()) {
            return (ArrayNode) node.get("UVs");
        }
        return null;
    }

    private static List<Vec3> parseVertices(ArrayNode array) {
        List<Vec3> result = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            result.add(parseVector3(n.asText()));
        }
        return result;
    }

    private static List<Vec2> parseUvs(ArrayNode array) {
        List<Vec2> result = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            result.add(parseVector2(n.asText()));
        }
        return result;
    }

    private static List<TangentData> parseTangents(ArrayNode array) {
        List<TangentData> result = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            String raw = n.asText();
            Matcher m = TANGENT_PATTERN.matcher(raw);
            if (m.find()) {
                result.add(new TangentData(
                        new Vec3(
                                Double.parseDouble(m.group(1)),
                                Double.parseDouble(m.group(2)),
                                Double.parseDouble(m.group(3))
                        ),
                        "True".equals(m.group(4))
                ));
            } else {
                result.add(null);
            }
        }
        return result;
    }

    private static int[] parseTriangles(ArrayNode array) {
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).asInt();
        }
        return result;
    }

    private static Vec3 parseVector3(String raw) {
        Matcher m = VECTOR3_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse vector3: " + raw);
        }
        return new Vec3(
                Double.parseDouble(m.group(1)),
                Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3))
        );
    }

    private static Vec2 parseVector2(String raw) {
        Matcher m = VECTOR2_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse vector2: " + raw);
        }
        return new Vec2(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
    }

    private static final class TileBuildData {
        final List<TileInfo> tiles;
        final int[] triangleToTile;
        final Map<Integer, Set<Integer>> neighbors;
        final int[] triangles;
        final List<Vec3> vertices;
        final List<Vec3> normals;
        final List<Vec2> uvs;
        final List<TangentData> tangents;

        TileBuildData(List<TileInfo> tiles,
                      int[] triangleToTile,
                      Map<Integer, Set<Integer>> neighbors,
                      int[] triangles,
                      List<Vec3> vertices,
                      List<Vec3> normals,
                      List<Vec2> uvs,
                      List<TangentData> tangents) {
            this.tiles = tiles;
            this.triangleToTile = triangleToTile;
            this.neighbors = neighbors;
            this.triangles = triangles;
            this.vertices = vertices;
            this.normals = normals;
            this.uvs = uvs;
            this.tangents = tangents;
        }
    }

    private static final class TileInfo {
        final int tileId;
        final String type;
        final int centerVertex;
        final Vec3 center;
        final List<Integer> ringVertexIndices;
        final List<Integer> triangleIndices;

        TileInfo(int tileId,
                 String type,
                 int centerVertex,
                 Vec3 center,
                 List<Integer> ringVertexIndices,
                 List<Integer> triangleIndices) {
            this.tileId = tileId;
            this.type = type;
            this.centerVertex = centerVertex;
            this.center = center;
            this.ringVertexIndices = ringVertexIndices;
            this.triangleIndices = triangleIndices;
        }
    }

    private static final class Vec2 {
        final double u;
        final double v;

        Vec2(double u, double v) {
            this.u = u;
            this.v = v;
        }
    }

    private static final class TangentData {
        final Vec3 tangent;
        final boolean flipY;

        TangentData(Vec3 tangent, boolean flipY) {
            this.tangent = tangent;
            this.flipY = flipY;
        }
    }
}
