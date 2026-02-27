package com.sphererelaxator.unreal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sphererelaxator.mesh.Mesh;
import com.sphererelaxator.mesh.Tile;
import com.sphererelaxator.mesh.TileType;
import com.sphererelaxator.mesh.Vec3;
import com.sphererelaxator.solver.RelaxationConfig;
import com.sphererelaxator.solver.RelaxationMetrics;
import com.sphererelaxator.solver.SphereRelaxator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnrealFormatProcessor {
    private static final Pattern VECTOR3_PATTERN = Pattern.compile("\\(X=([-0-9.Ee]+),Y=([-0-9.Ee]+),Z=([-0-9.Ee]+)\\)");
    private static final Pattern TANGENT_PATTERN = Pattern.compile(
            "\\(TangentX=\\(X=([-0-9.Ee]+),Y=([-0-9.Ee]+),Z=([-0-9.Ee]+)\\),bFlipTangentY=(True|False)\\)"
    );

    private final ObjectMapper mapper;

    public UnrealFormatProcessor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static boolean isUnrealFormat(JsonNode root) {
        if (root == null || !root.isArray() || root.isEmpty()) {
            return false;
        }
        JsonNode first = root.get(0);
        return first != null && first.has("Vertiches") && first.has("Triangles");
    }

    public RelaxationMetrics process(JsonNode root,
                                     Path output,
                                     RelaxationConfig config,
                                     boolean emitUv) throws IOException {
        ArrayNode items = (ArrayNode) root;
        RelaxationMetrics lastMetrics = null;

        for (JsonNode item : items) {
            if (!item.isObject()) {
                continue;
            }
            ObjectNode node = (ObjectNode) item;
            ArrayNode vertArray = requiredArray(node, "Vertiches");
            ArrayNode triArray = requiredArray(node, "Triangles");
            ArrayNode tangentArray = node.has("Tangents") && node.get("Tangents").isArray()
                    ? (ArrayNode) node.get("Tangents")
                    : null;

            List<Vec3> originalVertices = parseVertices(vertArray);
            int[] triangles = parseTriangles(triArray);
            List<TangentData> tangents = tangentArray != null ? parseTangents(tangentArray) : List.of();

            BuildResult built = buildMesh(originalVertices, triangles);
            SphereRelaxator relaxator = new SphereRelaxator();
            lastMetrics = relaxator.relax(built.mesh, config);

            Vec3[] uniqueRelaxed = built.mesh.vertices();
            ArrayNode outVerts = mapper.createArrayNode();
            ArrayNode outNormals = mapper.createArrayNode();
            ArrayNode outTangents = mapper.createArrayNode();
            ArrayNode outUv = mapper.createArrayNode();

            for (int i = 0; i < originalVertices.size(); i++) {
                int u = built.originalToUnique[i];
                Vec3 p = uniqueRelaxed[u];
                Vec3 n = p.normalize();
                Vec3 t = tangentFromSpherical(n);

                boolean flip = i < tangents.size() ? tangents.get(i).flipY : true;
                outVerts.add(formatVector3(p));
                outNormals.add(formatVector3(n));
                outTangents.add(formatTangent(t, flip));
                outUv.add(formatUv(n));
            }

            node.set("Vertiches", outVerts);
            node.set("Normals", outNormals);

            boolean hasExistingUv = node.has("UV0") || node.has("UV") || node.has("UVs");
            if (emitUv || hasExistingUv) {
                String uvKey = detectUvKey(node);
                node.set(uvKey, outUv);
            }
            node.set("Tangents", outTangents);
        }

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.toFile(), root);
        return lastMetrics == null
                ? new RelaxationMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                : lastMetrics;
    }

    private static String detectUvKey(ObjectNode node) {
        if (node.has("UV0")) {
            return "UV0";
        }
        if (node.has("UV")) {
            return "UV";
        }
        if (node.has("UVs")) {
            return "UVs";
        }
        return "UV0";
    }

    private static Vec3 tangentFromSpherical(Vec3 n) {
        double lon = Math.atan2(n.z(), n.x());
        Vec3 t = new Vec3(-Math.sin(lon), 0.0, Math.cos(lon));
        Vec3 ortho = t.subtract(n.scale(t.dot(n)));
        double len = ortho.length();
        if (len < 1e-9) {
            ortho = new Vec3(0.0, 0.0, 1.0).subtract(n.scale(n.z()));
            len = ortho.length();
            if (len < 1e-9) {
                ortho = new Vec3(1.0, 0.0, 0.0);
                len = ortho.length();
            }
        }
        return ortho.scale(1.0 / len);
    }

    private static String formatUv(Vec3 n) {
        double u = 0.5 + Math.atan2(n.z(), n.x()) / (2.0 * Math.PI);
        if (u < 0.0) {
            u += 1.0;
        }
        if (u > 1.0) {
            u -= 1.0;
        }
        double v = 0.5 - Math.asin(clamp(n.y(), -1.0, 1.0)) / Math.PI;
        return String.format(Locale.US, "(X=%.6f,Y=%.6f)", u, v);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BuildResult buildMesh(List<Vec3> originalVertices, int[] triangles) {
        Map<String, Integer> keyToUnique = new HashMap<>();
        List<Vec3> unique = new ArrayList<>();
        int[] originalToUnique = new int[originalVertices.size()];

        for (int i = 0; i < originalVertices.size(); i++) {
            Vec3 p = originalVertices.get(i);
            String key = quantizedKey(p);
            Integer idx = keyToUnique.get(key);
            if (idx == null) {
                idx = unique.size();
                unique.add(p);
                keyToUnique.put(key, idx);
            }
            originalToUnique[i] = idx;
        }

        Vec3[] verts = unique.toArray(new Vec3[0]);
        boolean[] fixed = new boolean[verts.length];
        Set<Long> edgeKeys = new LinkedHashSet<>();
        List<Set<Integer>> neighborSets = new ArrayList<>(verts.length);
        for (int i = 0; i < verts.length; i++) {
            neighborSets.add(new HashSet<>());
        }

        for (int i = 0; i + 2 < triangles.length; i += 3) {
            int a = originalToUnique[triangles[i]];
            int b = originalToUnique[triangles[i + 1]];
            int c = originalToUnique[triangles[i + 2]];
            addEdge(a, b, edgeKeys, neighborSets);
            addEdge(b, c, edgeKeys, neighborSets);
            addEdge(c, a, edgeKeys, neighborSets);
        }

        List<Tile> tiles = buildTilesFromFans(triangles, originalToUnique);
        Set<Integer> pentagonVertices = new HashSet<>();
        for (Tile t : tiles) {
            if (t.type() == TileType.PENTAGON) {
                pentagonVertices.addAll(t.vertexIds());
            }
        }

        List<int[]> edges = new ArrayList<>(edgeKeys.size());
        for (long key : edgeKeys) {
            edges.add(new int[]{(int) (key >>> 32), (int) key});
        }
        int[][] neighbors = new int[verts.length][];
        for (int i = 0; i < verts.length; i++) {
            neighbors[i] = neighborSets.get(i).stream().mapToInt(Integer::intValue).toArray();
        }

        Mesh mesh = new Mesh(verts, fixed, edges, tiles, neighbors, pentagonVertices);
        return new BuildResult(mesh, originalToUnique);
    }

    private static List<Tile> buildTilesFromFans(int[] triangles, int[] originalToUnique) {
        List<Tile> tiles = new ArrayList<>();
        int i = 0;
        int tileId = 0;
        while (i + 2 < triangles.length) {
            int center = triangles[i];
            List<int[]> pairs = new ArrayList<>();
            while (i + 2 < triangles.length && triangles[i] == center) {
                pairs.add(new int[]{triangles[i + 1], triangles[i + 2]});
                i += 3;
            }
            List<Integer> originalRing = buildRingFromPairs(pairs);
            List<Integer> ring = new ArrayList<>(originalRing.size());
            for (int v : originalRing) {
                ring.add(originalToUnique[v]);
            }
            int sides = ring.size();
            TileType type = sides == 5 ? TileType.PENTAGON : TileType.HEXAGON;
            tiles.add(new Tile(tileId++, type, ring));
        }
        return tiles;
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

    private static void addEdge(int a, int b, Set<Long> edgeKeys, List<Set<Integer>> neighborSets) {
        if (a == b) {
            return;
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        long key = ((long) lo << 32) | (hi & 0xffffffffL);
        if (edgeKeys.add(key)) {
            neighborSets.get(a).add(b);
            neighborSets.get(b).add(a);
        }
    }

    private static String quantizedKey(Vec3 p) {
        return String.format(Locale.US, "%.6f|%.6f|%.6f", p.x(), p.y(), p.z());
    }

    private static List<Vec3> parseVertices(ArrayNode array) {
        List<Vec3> result = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            result.add(parseVector3(n.asText()));
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
                result.add(new TangentData(new Vec3(1.0, 0.0, 0.0), true));
            }
        }
        return result;
    }

    private static Vec3 parseVector3(String raw) {
        Matcher m = VECTOR3_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse vector: " + raw);
        }
        return new Vec3(
                Double.parseDouble(m.group(1)),
                Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3))
        );
    }

    private static String formatVector3(Vec3 v) {
        return String.format(Locale.US, "(X=%.6f,Y=%.6f,Z=%.6f)", v.x(), v.y(), v.z());
    }

    private static String formatTangent(Vec3 tangent, boolean flip) {
        return String.format(
                Locale.US,
                "(TangentX=(X=%.6f,Y=%.6f,Z=%.6f),bFlipTangentY=%s)",
                tangent.x(), tangent.y(), tangent.z(),
                flip ? "True" : "False"
        );
    }

    private static ArrayNode requiredArray(ObjectNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Expected array field: " + key);
        }
        return (ArrayNode) value;
    }

    private record BuildResult(Mesh mesh, int[] originalToUnique) {
    }

    private record TangentData(Vec3 tangent, boolean flipY) {
    }
}
