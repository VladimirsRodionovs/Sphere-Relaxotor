# SphereRelaxator Context

## Project Path
- `/home/vladimirs/SphereRelaxator`

## Current Goal
- Generate sphere data for Unreal (`Create Mesh Section`) with tile structure:
  - 12 pentagons + hexagons
  - tile neighbors
  - triangle-to-tile mapping
  - per-tile center
  - separate CSV outputs for positions, normals, UV, tangents, triangles

## Implemented Modes
- `relax`: relaxes geometry from input JSON.
- `icosphere`: generates only vertices of icosphere.
- `tilecsv`: reads Unreal-like JSON and exports tile CSV pack.
- `fullcsv`: generates full tile CSV pack from scratch (no input JSON).

## Important Behavior
- In `icosphere` and `fullcsv`, radius is fixed to `1.0`.
- `--radius` is ignored in these 2 modes.
- Output in `tilecsv/fullcsv` is a **prefix** (not a directory).
  - Example: `--output /home/vladimirs/SphereRelaxator/out/sphere_full_s4`
  - Produces files like `sphere_full_s4_tiles.csv`, etc.

## Main Run Command (Full From Scratch)
```bash
cd /home/vladimirs/SphereRelaxator
mvn -q -DskipTests package
./run_relaxator.sh --mode fullcsv --subdivisions 4 --output /home/vladimirs/SphereRelaxator/out/sphere_full_s4
```

## Generated CSV Files (fullcsv/tilecsv)
- `*_tiles.csv`
- `*_tile_centers.csv`
- `*_tile_vertices.csv`
- `*_tile_vertex_positions.csv`
- `*_tile_vertex_normals.csv`
- `*_tile_vertex_uv.csv`
- `*_tile_vertex_tangents.csv`
- `*_tile_triangles.csv`
- `*_triangle_to_tile.csv`
- `*_tile_neighbors.csv`

## Key Source Files
- `src/main/java/com/sphererelaxator/SphereRelaxatorCli.java`
- `src/main/java/com/sphererelaxator/generator/IcosphereGenerator.java`
- `src/main/java/com/sphererelaxator/generator/FullSphereCsvGenerator.java`
- `src/main/java/com/sphererelaxator/unreal/UnrealTileCsvExporter.java`
- `src/main/java/com/sphererelaxator/unreal/UnrealFormatProcessor.java`
- `simple_ui.sh`
- `run_relaxator.sh`

## Notes For Next Session
- If needed, next step is UE-friendly import helper scripts (convert CSV -> arrays for C++/Blueprint data assets).
- `.uasset` binary is not directly editable/readable as plain text; use Unreal export tools/editor pipelines if inspection is needed.
