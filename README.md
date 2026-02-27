# SphereRelaxator

Offline-relaxator for Goldberg-like sphere meshes (12 pentagons + hexagons).

## What it does

- reads full mesh geometry from JSON;
- iteratively evens edge lengths;
- expands pentagon neighborhoods to reduce compression artifacts;
- projects every vertex back to sphere radius each iteration (`|v| = R`);
- writes updated geometry to JSON.

## Build

```bash
cd /home/vladimirs/SphereRelaxator
mvn -q -DskipTests package
```

## Run

```bash
java -cp "target/classes:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar" \
  com.sphererelaxator.SphereRelaxatorCli \
  --input sample/input.json \
  --output sample/output.json \
  --iterations 500 \
  --radius 1.0 \
  --step 0.28 \
  --laplacianWeight 0.42 \
  --springWeight 0.45 \
  --pentagonExpandWeight 0.35 \
  --threads 8 \
  --logEvery 25 \
  --progressEvery 10
```

For Unreal-like input (array with `Vertiches`, `Triangles`, `Normals`, `Tangents`) you can additionally emit spherical UV:

```bash
java -cp "target/classes:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar" \
  com.sphererelaxator.SphereRelaxatorCli \
  --input LatLongTileID3test_v2.json \
  --output LatLongTileID3test_v2.relaxed.json \
  --radius 500 \
  --iterations 350 \
  --pentagonExpandWeight 0.45 \
  --progressEvery 10 \
  --emitUv true
```

## Input format

```json
{
  "radius": 1.0,
  "vertices": [
    { "id": 0, "x": 0.0, "y": 0.0, "z": 1.0, "fixed": false }
  ],
  "tiles": [
    { "id": 100, "type": "PENTAGON", "vertexIds": [0, 1, 2, 3, 4] },
    { "id": 101, "type": "HEXAGON", "vertexIds": [4, 3, 8, 9, 10, 11] }
  ]
}
```

`type` can be `PENTAGON` or `HEXAGON`.
