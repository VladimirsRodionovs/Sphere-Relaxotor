package com.sphererelaxator.generator;

import com.sphererelaxator.mesh.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FullSphereCsvGenerator {
    private static final double EPS = 1e-12;

    private FullSphereCsvGenerator() {
    }

    public static GeneratedData generate(int subdivisions, double radius) {
        IcosphereGenerator.MeshData baseMesh = IcosphereGenerator.generateMesh(subdivisions, radius);
        List<Vec3> baseVertices = baseMesh.vertices();
        List<int[]> faces = baseMesh.faces();

        List<Vec3> faceCenters = new ArrayList<>(faces.size());
        for (int[] f : faces) {
            Vec3 center = baseVertices.get(f[0])
                    .add(baseVertices.get(f[1]))
                    .add(baseVertices.get(f[2]))
                    .scale(1.0 / 3.0)
                    .normalize()
                    .scale(radius);
            faceCenters.add(center);
        }

        int baseVertexCount = baseVertices.size();
        List<Vec3> vertices = new ArrayList<>(baseVertexCount + faceCenters.size());
        vertices.addAll(baseVertices);
        vertices.addAll(faceCenters);

        List<List<Integer>> facesByVertex = new ArrayList<>(baseVertexCount);
        for (int i = 0; i < baseVertexCount; i++) {
            facesByVertex.add(new ArrayList<>());
        }
        for (int fi = 0; fi < faces.size(); fi++) {
            int[] f = faces.get(fi);
            facesByVertex.get(f[0]).add(fi);
            facesByVertex.get(f[1]).add(fi);
            facesByVertex.get(f[2]).add(fi);
        }

        List<Integer> triList = new ArrayList<>();
        for (int vi = 0; vi < baseVertexCount; vi++) {
            List<Integer> adjacentFaces = facesByVertex.get(vi);
            if (adjacentFaces.size() < 3) {
                continue;
            }
            List<Integer> orderedFaces = sortFacesAroundVertex(baseVertices.get(vi), faceCenters, adjacentFaces);
            List<Integer> ring = new ArrayList<>(orderedFaces.size());
            for (int fi : orderedFaces) {
                ring.add(baseVertexCount + fi);
            }
            for (int i = 0; i < ring.size(); i++) {
                int a = ring.get(i);
                int b = ring.get((i + 1) % ring.size());
                triList.add(vi);
                triList.add(a);
                triList.add(b);
            }
        }

        int[] triangles = new int[triList.size()];
        for (int i = 0; i < triList.size(); i++) {
            triangles[i] = triList.get(i);
        }

        List<Vec3> normals = new ArrayList<>(vertices.size());
        List<double[]> uvs = new ArrayList<>(vertices.size());
        List<Vec3> tangents = new ArrayList<>(vertices.size());
        for (Vec3 p : vertices) {
            Vec3 n = p.normalize();
            normals.add(n);
            uvs.add(sphericalUv(n));
            tangents.add(defaultTangent(n));
        }

        return new GeneratedData(vertices, triangles, normals, uvs, tangents);
    }

    private static List<Integer> sortFacesAroundVertex(Vec3 vertex,
                                                       List<Vec3> faceCenters,
                                                       List<Integer> adjacentFaces) {
        Vec3 normal = vertex.normalize();
        Vec3 ref = projected(faceCenters.get(adjacentFaces.get(0)), normal).normalize();
        if (ref.length() < EPS) {
            ref = fallbackTangent(normal);
        }
        Vec3 finalRef = ref;
        return adjacentFaces.stream()
                .map(fi -> new FaceAngle(fi, angleAround(faceCenters.get(fi), normal, finalRef)))
                .sorted(Comparator.comparingDouble(FaceAngle::angle))
                .map(FaceAngle::faceIndex)
                .toList();
    }

    private static double angleAround(Vec3 point, Vec3 normal, Vec3 ref) {
        Vec3 projected = projected(point, normal).normalize();
        if (projected.length() < EPS) {
            projected = ref;
        }
        double x = ref.dot(projected);
        double y = normal.dot(ref.cross(projected));
        return Math.atan2(y, x);
    }

    private static Vec3 projected(Vec3 p, Vec3 normal) {
        return p.subtract(normal.scale(p.dot(normal)));
    }

    private static Vec3 fallbackTangent(Vec3 normal) {
        Vec3 axis = Math.abs(normal.y()) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return axis.cross(normal).normalize();
    }

    private static Vec3 defaultTangent(Vec3 normal) {
        Vec3 t = new Vec3(0, 1, 0).cross(normal);
        if (t.length() < EPS) {
            t = new Vec3(1, 0, 0).cross(normal);
        }
        return t.normalize();
    }

    private static double[] sphericalUv(Vec3 normal) {
        double clampedY = Math.max(-1.0, Math.min(1.0, normal.y()));
        double u = Math.atan2(normal.z(), normal.x()) / (2.0 * Math.PI) + 0.5;
        double v = 0.5 - Math.asin(clampedY) / Math.PI;
        return new double[]{u, v};
    }

    public record GeneratedData(
            List<Vec3> vertices,
            int[] triangles,
            List<Vec3> normals,
            List<double[]> uvs,
            List<Vec3> tangents
    ) {
    }

    private record FaceAngle(int faceIndex, double angle) {
    }
}
