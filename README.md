# SphereRelaxator

Offline relaxer for Goldberg/icosphere-like meshes.

SphereRelaxator iteratively smooths spherical mesh geometry, reduces local deformation (including pentagon neighborhoods), preserves radius constraints, and exports updated mesh JSON.

## Highlights
- Iterative spring + Laplacian relaxation.
- Pentagon-neighborhood expansion to reduce artifacts.
- Radius projection per iteration (`|v| = R`).
- Supports Unreal-like input and optional UV emission.

## Tech Stack
- Java 17
- Maven
- Jackson

## Quick Start
1. Build:
   - `mvn -q -DskipTests package`
2. Run CLI:
   - `java -cp "target/classes:..." com.sphererelaxator.SphereRelaxatorCli --input ... --output ...`

## Documentation
- Internal design: `ARCHITECTURE.md`
- Context notes: `CONTEXT.md`

## Contact
vladimirs.rodionovs@gmail.com
