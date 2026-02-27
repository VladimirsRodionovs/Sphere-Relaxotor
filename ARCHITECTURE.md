# ARCHITECTURE — SphereRelaxator

## Purpose
`SphereRelaxator` is an offline relaxer for Goldberg/icosphere meshes. It equalizes edge lengths, reduces local deformation (especially near pentagons), and writes updated geometry back to JSON.

## Tech stack
- Java 17
- Maven
- Jackson

## High-level flow
1. CLI reads relaxation parameters.
2. `io/*` parses input JSON into mesh DTO/domain structures.
3. `mesh/*` builds internal vertex/tile graph.
4. `solver/SphereRelaxator` runs iterative relaxation.
5. Each iteration re-projects vertices to target sphere radius (`|v| = R`).
6. Result is exported to JSON; optional UV can be emitted for Unreal-like input.

## Layers
### 1) CLI / orchestration
- Class: `com.sphererelaxator.SphereRelaxatorCli`
- Responsibility: parse args (`iterations`, `step`, `radius`, weights, `threads`, `emitUv`) and run workflow.

### 2) Solver
- Package: `com.sphererelaxator.solver`
- Key classes:
  - `SphereRelaxator` — core iterative algorithm;
  - `RelaxationConfig` — configuration container;
  - `RelaxationMetrics` — quality/convergence metrics.

### 3) Mesh model
- Package: `com.sphererelaxator.mesh`
- Key classes:
  - `Mesh`, `MeshBuilder`, `Tile`, `TileType`, `Vec3`.
- Responsibility: topology + geometry representation.

### 4) IO DTO
- Package: `com.sphererelaxator.io`
- Classes: `MeshDocument`, `VertexDto`, `TileDto`
- Responsibility: JSON de/serialization contracts.

### 5) Format adapters / utilities
- Package: `com.sphererelaxator.unreal`
- Classes: `UnrealFormatProcessor`, `UnrealTileCsvExporter`
- Responsibility: Unreal-like format support and helper exports.

### 6) Optional generators
- Package: `com.sphererelaxator.generator`
- Classes: `IcosphereGenerator`, `FullSphereCsvGenerator`
- Responsibility: generate baseline test geometry.

## Algorithm model
Each iteration combines:
- spring-like edge-length correction;
- Laplacian smoothing;
- pentagon-neighborhood expansion.

After applying displacement, every vertex is normalized back to target radius to preserve global spherical shape and prevent radial drift.

## Input / output
- Input: JSON mesh (`vertices`, `tiles`, `radius`) or Unreal-like arrays.
- Output: updated JSON mesh, optional spherical UV.

## Integrations
- Used as geometry pre-processing step for `PlanetSurfaceGenerator` / rendering pipeline.
- Can emit CSV diagnostics for analysis.

## Extension points
- Add convergence metrics in `RelaxationMetrics`.
- Add step/weight policies in `RelaxationConfig`.
- Add new file-format adapters near `unreal/*`.

## Risks
- Excessive step size can cause local artifacts/oscillation.
- High iteration count increases runtime significantly.
- External JSON schema changes can break importer without adapter update.

## Quick navigation
- Entry point: `src/main/java/com/sphererelaxator/SphereRelaxatorCli.java`
- Solver: `src/main/java/com/sphererelaxator/solver/SphereRelaxator.java`
- Mesh model: `src/main/java/com/sphererelaxator/mesh/`
- Unreal adapter: `src/main/java/com/sphererelaxator/unreal/`
