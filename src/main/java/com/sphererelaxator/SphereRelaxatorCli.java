package com.sphererelaxator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.sphererelaxator.generator.FullSphereCsvGenerator;
import com.sphererelaxator.generator.IcosphereGenerator;
import com.sphererelaxator.io.MeshDocument;
import com.sphererelaxator.io.VertexDto;
import com.sphererelaxator.mesh.Mesh;
import com.sphererelaxator.mesh.MeshBuilder;
import com.sphererelaxator.mesh.Vec3;
import com.sphererelaxator.solver.RelaxationConfig;
import com.sphererelaxator.solver.RelaxationMetrics;
import com.sphererelaxator.solver.SphereRelaxator;
import com.sphererelaxator.unreal.UnrealFormatProcessor;
import com.sphererelaxator.unreal.UnrealTileCsvExporter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SphereRelaxatorCli {
    private SphereRelaxatorCli() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> parsed = parseArgs(args);
        String mode = parsed.getOrDefault("mode", "relax").toLowerCase(Locale.ROOT);
        boolean isIcosphereMode = "icosphere".equals(mode);
        boolean isTileCsvMode = "tilecsv".equals(mode);
        boolean isFullCsvMode = "fullcsv".equals(mode);
        boolean requiresInput = !isIcosphereMode && !isFullCsvMode;

        if (parsed.containsKey("help")
                || !parsed.containsKey("output")
                || (requiresInput && !parsed.containsKey("input"))) {
            printHelp();
            return;
        }

        Path output = Path.of(parsed.get("output"));
        if (isIcosphereMode) {
            ensureOutputPath(output);
            runIcosphereMode(parsed, output);
            return;
        }
        if (isFullCsvMode) {
            ensureOutputPath(output);
            runFullCsvMode(parsed, output);
            return;
        }
        if (isTileCsvMode) {
            ensureOutputPath(output);
            Path input = Path.of(parsed.get("input"));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(input.toFile());
            UnrealTileCsvExporter exporter = new UnrealTileCsvExporter();
            exporter.export(root, output);
            System.out.printf(Locale.US,
                    "Done. mode=tilecsv, input=%s, output_prefix=%s%n", input, output);
            return;
        }
        ensureOutputPath(output);

        Path input = Path.of(parsed.get("input"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(input.toFile());
        RelaxationConfig config;
        RelaxationMetrics metrics;

        if (UnrealFormatProcessor.isUnrealFormat(root)) {
            config = new RelaxationConfig(
                    parseInt(parsed, "iterations", 350),
                    parseDouble(parsed, "radius", 450.0),
                    parseDouble(parsed, "step", 0.24),
                    parseDouble(parsed, "laplacianWeight", 0.38),
                    parseDouble(parsed, "springWeight", 0.52),
                    parseDouble(parsed, "pentagonExpandWeight", 0.45),
                    parseInt(parsed, "threads", Runtime.getRuntime().availableProcessors()),
                    parseInt(parsed, "logEvery", 25),
                    parseInt(parsed, "progressEvery", 10)
            );
            UnrealFormatProcessor processor = new UnrealFormatProcessor(mapper);
            metrics = processor.process(root, output, config, parseBoolean(parsed, "emitUv", false));
        } else {
            MeshDocument document = mapper.treeToValue(root, MeshDocument.class);
            Mesh mesh = MeshBuilder.fromDocument(document);
            config = new RelaxationConfig(
                    parseInt(parsed, "iterations", 350),
                    parseDouble(parsed, "radius", document.radius > 0.0 ? document.radius : 1.0),
                    parseDouble(parsed, "step", 0.28),
                    parseDouble(parsed, "laplacianWeight", 0.42),
                    parseDouble(parsed, "springWeight", 0.45),
                    parseDouble(parsed, "pentagonExpandWeight", 0.35),
                    parseInt(parsed, "threads", Runtime.getRuntime().availableProcessors()),
                    parseInt(parsed, "logEvery", 25),
                    parseInt(parsed, "progressEvery", 10)
            );
            SphereRelaxator relaxator = new SphereRelaxator();
            metrics = relaxator.relax(mesh, config);
            MeshDocument out = MeshBuilder.toDocument(mesh, config.radius());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output.toFile(), out);
        }

        System.out.printf(Locale.US, "Done. Iterations=%d, radius=%.6f%n", config.iterations(), config.radius());
        System.out.printf(Locale.US, "Edge length min=%.6f max=%.6f mean=%.6f std=%.6f%n",
                metrics.edgeMin(), metrics.edgeMax(), metrics.edgeMean(), metrics.edgeStdDev());
        System.out.printf(Locale.US, "Pentagon area mean=%.6f, Hex area mean=%.6f%n",
                metrics.pentagonAreaMean(), metrics.hexAreaMean());
    }

    private static void runIcosphereMode(Map<String, String> parsed, Path output) throws Exception {
        int subdivisions = parseInt(parsed, "subdivisions", 0);
        double radius = 1.0;
        if (parsed.containsKey("radius") && Math.abs(parseDouble(parsed, "radius", 1.0) - 1.0) > 1e-9) {
            System.out.println("Notice: --radius is ignored in icosphere mode. Using fixed radius=1.");
        }
        String format = parsed.getOrDefault("format", detectOutputFormat(output));

        List<Vec3> vertices = IcosphereGenerator.generateVertices(subdivisions, radius);
        if ("json".equalsIgnoreCase(format)) {
            writeVerticesAsJson(vertices, radius, output);
        } else if ("csv".equalsIgnoreCase(format)) {
            writeVerticesAsCsv(vertices, output);
        } else {
            writeVerticesAsTxt(vertices, output);
        }
        System.out.printf(Locale.US,
                "Done. mode=icosphere, subdivisions=%d, radius=%.6f, vertices=%d, output=%s%n",
                subdivisions, radius, vertices.size(), output);
    }

    private static void runFullCsvMode(Map<String, String> parsed, Path output) throws Exception {
        int subdivisions = parseInt(parsed, "subdivisions", 0);
        double radius = 1.0;
        if (parsed.containsKey("radius") && Math.abs(parseDouble(parsed, "radius", 1.0) - 1.0) > 1e-9) {
            System.out.println("Notice: --radius is ignored in fullcsv mode. Using fixed radius=1.");
        }

        FullSphereCsvGenerator.GeneratedData generated = FullSphereCsvGenerator.generate(subdivisions, radius);
        UnrealTileCsvExporter exporter = new UnrealTileCsvExporter();
        exporter.exportRaw(
                output,
                generated.vertices(),
                generated.triangles(),
                generated.normals(),
                generated.uvs(),
                generated.tangents()
        );
        System.out.printf(
                Locale.US,
                "Done. mode=fullcsv, subdivisions=%d, radius=%.6f, vertices=%d, triangles=%d, output_prefix=%s%n",
                subdivisions,
                radius,
                generated.vertices().size(),
                generated.triangles().length / 3,
                output
        );
    }

    private static String detectOutputFormat(Path output) {
        String name = output.getFileName() == null ? "" : output.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return "csv";
        }
        return name.endsWith(".json") ? "json" : "txt";
    }

    private static void writeVerticesAsTxt(List<Vec3> vertices, Path output) throws Exception {
        List<String> lines = new ArrayList<>(vertices.size());
        for (Vec3 v : vertices) {
            lines.add(String.format(Locale.US, "%.9f %.9f %.9f", v.x(), v.y(), v.z()));
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private static void writeVerticesAsJson(List<Vec3> vertices, double radius, Path output) throws Exception {
        MeshDocument doc = new MeshDocument();
        doc.radius = radius;
        for (int i = 0; i < vertices.size(); i++) {
            Vec3 p = vertices.get(i);
            VertexDto v = new VertexDto();
            v.id = i;
            v.x = p.x();
            v.y = p.y();
            v.z = p.z();
            v.fixed = false;
            doc.vertices.add(v);
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.toFile(), doc);
    }

    private static void writeVerticesAsCsv(List<Vec3> vertices, Path output) throws Exception {
        List<String> lines = new ArrayList<>(vertices.size() + 1);
        lines.add("id,x,y,z");
        for (int i = 0; i < vertices.size(); i++) {
            Vec3 v = vertices.get(i);
            lines.add(String.format(Locale.US, "%d,%.9f,%.9f,%.9f", i, v.x(), v.y(), v.z()));
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private static void ensureOutputPath(Path output) throws Exception {
        if (Files.exists(output) && Files.isDirectory(output)) {
            throw new IllegalArgumentException("Output path is a directory, expected file: " + output);
        }
        Path parent = output.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private static int parseInt(Map<String, String> args, String key, int fallback) {
        return args.containsKey(key) ? Integer.parseInt(args.get(key)) : fallback;
    }

    private static double parseDouble(Map<String, String> args, String key, double fallback) {
        return args.containsKey(key) ? Double.parseDouble(args.get(key)) : fallback;
    }

    private static boolean parseBoolean(Map<String, String> args, String key, boolean fallback) {
        return args.containsKey(key) ? Boolean.parseBoolean(args.get(key)) : fallback;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                continue;
            }
            String key = a.substring(2);
            if (key.equals("help")) {
                map.put("help", "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for argument: " + a);
            }
            map.put(key, args[++i]);
        }
        return map;
    }

    private static void printHelp() {
        System.out.println("SphereRelaxator CLI");
        System.out.println("Usage:");
        System.out.println("  Relax mode:");
        System.out.println("    ./run_relaxator.sh --mode relax --input in.json --output out.json [options]");
        System.out.println("  Icosphere mode (vertices only):");
        System.out.println("    ./run_relaxator.sh --mode icosphere --subdivisions 4 --output vertices.csv [--format txt|csv|json]");
        System.out.println("  Full CSV mode (from scratch, no input JSON):");
        System.out.println("    ./run_relaxator.sh --mode fullcsv --subdivisions 4 --output out/sphere_data");
        System.out.println("  Tile CSV mode (Unreal-like JSON -> multiple CSV files):");
        System.out.println("    ./run_relaxator.sh --mode tilecsv --input sphere.json --output out/sphere_data");
        System.out.println("Options:");
        System.out.println("  --mode <relax|icosphere|fullcsv|tilecsv> default: relax");
        System.out.println("  --subdivisions <int>          for icosphere/fullcsv mode, default: 0");
        System.out.println("  --format <txt|csv|json>       for icosphere mode, default: by output extension");
        System.out.println("  --iterations <int>            default: 350");
        System.out.println("  --radius <double>             used in relax mode; ignored in icosphere/fullcsv (fixed 1.0)");
        System.out.println("  --step <double>               default: 0.28");
        System.out.println("  --laplacianWeight <double>    default: 0.42");
        System.out.println("  --springWeight <double>       default: 0.45");
        System.out.println("  --pentagonExpandWeight <double> default: 0.35");
        System.out.println("  --threads <int>               default: cpu count");
        System.out.println("  --logEvery <int>              default: 25");
        System.out.println("  --progressEvery <int>         default: 10");
        System.out.println("  --emitUv <true/false>         default: false (for Unreal-like format)");
    }
}
