package com.sphererelaxator.generator;

import com.sphererelaxator.mesh.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IcosphereGenerator {
    private IcosphereGenerator() {
    }

    public static List<Vec3> generateVertices(int subdivisions, double radius) {
        return generateMesh(subdivisions, radius).vertices();
    }

    public static MeshData generateMesh(int subdivisions, double radius) {
        if (subdivisions < 0) {
            throw new IllegalArgumentException("subdivisions must be >= 0");
        }
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        List<Vec3> vertices = new ArrayList<>();
        vertices.add(new Vec3(-1, phi, 0));
        vertices.add(new Vec3(1, phi, 0));
        vertices.add(new Vec3(-1, -phi, 0));
        vertices.add(new Vec3(1, -phi, 0));
        vertices.add(new Vec3(0, -1, phi));
        vertices.add(new Vec3(0, 1, phi));
        vertices.add(new Vec3(0, -1, -phi));
        vertices.add(new Vec3(0, 1, -phi));
        vertices.add(new Vec3(phi, 0, -1));
        vertices.add(new Vec3(phi, 0, 1));
        vertices.add(new Vec3(-phi, 0, -1));
        vertices.add(new Vec3(-phi, 0, 1));
        normalizeAll(vertices, radius);

        List<int[]> faces = new ArrayList<>();
        faces.add(new int[]{0, 11, 5});
        faces.add(new int[]{0, 5, 1});
        faces.add(new int[]{0, 1, 7});
        faces.add(new int[]{0, 7, 10});
        faces.add(new int[]{0, 10, 11});
        faces.add(new int[]{1, 5, 9});
        faces.add(new int[]{5, 11, 4});
        faces.add(new int[]{11, 10, 2});
        faces.add(new int[]{10, 7, 6});
        faces.add(new int[]{7, 1, 8});
        faces.add(new int[]{3, 9, 4});
        faces.add(new int[]{3, 4, 2});
        faces.add(new int[]{3, 2, 6});
        faces.add(new int[]{3, 6, 8});
        faces.add(new int[]{3, 8, 9});
        faces.add(new int[]{4, 9, 5});
        faces.add(new int[]{2, 4, 11});
        faces.add(new int[]{6, 2, 10});
        faces.add(new int[]{8, 6, 7});
        faces.add(new int[]{9, 8, 1});

        for (int i = 0; i < subdivisions; i++) {
            Map<Long, Integer> midpointCache = new HashMap<>();
            List<int[]> refined = new ArrayList<>(faces.size() * 4);
            for (int[] face : faces) {
                int a = face[0];
                int b = face[1];
                int c = face[2];
                int ab = midpoint(a, b, vertices, midpointCache, radius);
                int bc = midpoint(b, c, vertices, midpointCache, radius);
                int ca = midpoint(c, a, vertices, midpointCache, radius);

                refined.add(new int[]{a, ab, ca});
                refined.add(new int[]{b, bc, ab});
                refined.add(new int[]{c, ca, bc});
                refined.add(new int[]{ab, bc, ca});
            }
            faces = refined;
        }
        return new MeshData(vertices, faces);
    }

    private static int midpoint(int a,
                                int b,
                                List<Vec3> vertices,
                                Map<Long, Integer> cache,
                                double radius) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        long key = ((long) lo << 32) | (hi & 0xffffffffL);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Vec3 va = vertices.get(a);
        Vec3 vb = vertices.get(b);
        Vec3 mid = new Vec3(
                (va.x() + vb.x()) * 0.5,
                (va.y() + vb.y()) * 0.5,
                (va.z() + vb.z()) * 0.5
        ).normalize().scale(radius);
        int idx = vertices.size();
        vertices.add(mid);
        cache.put(key, idx);
        return idx;
    }

    private static void normalizeAll(List<Vec3> vertices, double radius) {
        for (int i = 0; i < vertices.size(); i++) {
            vertices.set(i, vertices.get(i).normalize().scale(radius));
        }
    }

    public record MeshData(List<Vec3> vertices, List<int[]> faces) {
    }
}
