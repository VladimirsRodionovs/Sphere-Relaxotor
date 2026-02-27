package com.sphererelaxator.solver;

import com.sphererelaxator.mesh.Mesh;
import com.sphererelaxator.mesh.Tile;
import com.sphererelaxator.mesh.TileType;
import com.sphererelaxator.mesh.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class SphereRelaxator {
    public RelaxationMetrics relax(Mesh mesh, RelaxationConfig config) {
        Vec3[] vertices = mesh.vertices();
        boolean[] fixed = mesh.fixed();
        int[][] neighbors = mesh.neighbors();
        Set<Integer> pentagonVertices = mesh.pentagonVertices();
        double radius = config.radius();

        projectAllToRadius(vertices, radius);
        ForkJoinPool pool = new ForkJoinPool(Math.max(1, config.threads()));
        long startedAtNs = System.nanoTime();

        try {
            for (int iteration = 1; iteration <= config.iterations(); iteration++) {
                double targetEdgeLength = edgeMean(vertices, mesh.edges());
                Vec3[] pentagonBias = pentagonExpansionBias(vertices, mesh.tiles(), config.pentagonExpandWeight());
                Vec3[] next = new Vec3[vertices.length];

                runInPool(pool, () -> IntStream.range(0, vertices.length).parallel().forEach(i -> {
                    if (fixed[i]) {
                        next[i] = vertices[i];
                        return;
                    }
                    Vec3 current = vertices[i];
                    int[] nbs = neighbors[i];
                    if (nbs.length == 0) {
                        next[i] = current.normalize().scale(radius);
                        return;
                    }

                    Vec3 avg = new Vec3(0.0, 0.0, 0.0);
                    Vec3 spring = new Vec3(0.0, 0.0, 0.0);
                    for (int nb : nbs) {
                        Vec3 pv = vertices[nb];
                        avg = avg.add(pv);

                        Vec3 d = pv.subtract(current);
                        double len = d.length();
                        if (len > 1e-12) {
                            double diff = len - targetEdgeLength;
                            spring = spring.add(d.scale(diff / len));
                        }
                    }
                    avg = avg.scale(1.0 / nbs.length);

                    Vec3 laplacian = avg.subtract(current).scale(config.laplacianWeight());
                    Vec3 springForce = spring.scale(config.springWeight() / nbs.length);
                    Vec3 pentagonForce = pentagonVertices.contains(i) ? pentagonBias[i] : new Vec3(0.0, 0.0, 0.0);

                    Vec3 moved = current
                            .add(laplacian.scale(config.step()))
                            .add(springForce.scale(config.step()))
                            .add(pentagonForce.scale(config.step()));

                    next[i] = moved.normalize().scale(radius);
                }));

                System.arraycopy(next, 0, vertices, 0, vertices.length);

                if (config.logEvery() > 0 && iteration % config.logEvery() == 0) {
                    RelaxationMetrics metrics = collectMetrics(mesh);
                    System.out.printf(Locale.US,
                            "Iter %d: edge std=%.6f pentMean=%.6f hexMean=%.6f%n",
                            iteration, metrics.edgeStdDev(), metrics.pentagonAreaMean(), metrics.hexAreaMean());
                }
                if (config.progressEvery() > 0 && iteration % config.progressEvery() == 0) {
                    printProgress(iteration, config.iterations(), startedAtNs);
                }
            }
            return collectMetrics(mesh);
        } finally {
            pool.shutdown();
        }
    }

    private static Vec3[] pentagonExpansionBias(Vec3[] vertices, List<Tile> tiles, double weight) {
        Vec3[] bias = new Vec3[vertices.length];
        for (int i = 0; i < bias.length; i++) {
            bias[i] = new Vec3(0.0, 0.0, 0.0);
        }
        if (weight == 0.0) {
            return bias;
        }

        for (Tile tile : tiles) {
            if (tile.type() != TileType.PENTAGON) {
                continue;
            }
            Vec3 center = polygonCenter(vertices, tile.vertexIds());
            for (int idx : tile.vertexIds()) {
                Vec3 radial = vertices[idx].subtract(center).normalize().scale(weight);
                bias[idx] = bias[idx].add(radial);
            }
        }
        return bias;
    }

    private static Vec3 polygonCenter(Vec3[] vertices, List<Integer> ids) {
        Vec3 c = new Vec3(0.0, 0.0, 0.0);
        for (int i : ids) {
            c = c.add(vertices[i]);
        }
        return c.scale(1.0 / ids.size());
    }

    private static void projectAllToRadius(Vec3[] vertices, double radius) {
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = vertices[i].normalize().scale(radius);
        }
    }

    private static void runInPool(ForkJoinPool pool, Runnable action) {
        pool.submit(action).join();
    }

    private static double edgeMean(Vec3[] vertices, List<int[]> edges) {
        double sum = 0.0;
        for (int[] edge : edges) {
            sum += vertices[edge[0]].distance(vertices[edge[1]]);
        }
        return edges.isEmpty() ? 0.0 : sum / edges.size();
    }

    private static void printProgress(int iteration, int totalIterations, long startedAtNs) {
        double progress = totalIterations == 0 ? 1.0 : (double) iteration / totalIterations;
        long elapsedNs = System.nanoTime() - startedAtNs;
        double elapsedSec = elapsedNs / 1_000_000_000.0;
        double etaSec = progress <= 1e-9 ? 0.0 : elapsedSec * (1.0 - progress) / progress;
        System.out.printf(
                Locale.US,
                "Progress: %d/%d (%.1f%%), elapsed=%.1fs, eta=%.1fs%n",
                iteration, totalIterations, progress * 100.0, elapsedSec, Math.max(0.0, etaSec)
        );
    }

    public RelaxationMetrics collectMetrics(Mesh mesh) {
        Vec3[] vertices = mesh.vertices();
        List<int[]> edges = mesh.edges();
        List<Tile> tiles = mesh.tiles();

        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        double sum = 0.0;
        List<Double> lengths = new ArrayList<>(edges.size());
        for (int[] edge : edges) {
            double len = vertices[edge[0]].distance(vertices[edge[1]]);
            lengths.add(len);
            sum += len;
            min = Math.min(min, len);
            max = Math.max(max, len);
        }
        double mean = lengths.isEmpty() ? 0.0 : sum / lengths.size();
        double var = 0.0;
        for (double len : lengths) {
            double d = len - mean;
            var += d * d;
        }
        double std = lengths.isEmpty() ? 0.0 : Math.sqrt(var / lengths.size());

        double pentArea = 0.0;
        int pentCount = 0;
        double hexArea = 0.0;
        int hexCount = 0;
        for (Tile tile : tiles) {
            double a = polygonArea(vertices, tile.vertexIds());
            if (tile.type() == TileType.PENTAGON) {
                pentArea += a;
                pentCount++;
            } else {
                hexArea += a;
                hexCount++;
            }
        }

        return new RelaxationMetrics(
                min == Double.POSITIVE_INFINITY ? 0.0 : min,
                max,
                mean,
                std,
                pentCount == 0 ? 0.0 : pentArea / pentCount,
                hexCount == 0 ? 0.0 : hexArea / hexCount
        );
    }

    private static double polygonArea(Vec3[] vertices, List<Integer> ids) {
        if (ids.size() < 3) {
            return 0.0;
        }
        Vec3 a0 = vertices[ids.get(0)];
        double area = 0.0;
        for (int i = 1; i < ids.size() - 1; i++) {
            Vec3 b = vertices[ids.get(i)];
            Vec3 c = vertices[ids.get(i + 1)];
            Vec3 ab = b.subtract(a0);
            Vec3 ac = c.subtract(a0);
            area += ab.cross(ac).length() * 0.5;
        }
        return area;
    }
}
