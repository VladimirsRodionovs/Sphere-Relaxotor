package com.sphererelaxator.mesh;

public enum TileType {
    PENTAGON,
    HEXAGON;

    public static TileType from(String value) {
        if (value == null) {
            return HEXAGON;
        }
        String v = value.trim().toUpperCase();
        if (v.startsWith("PENT")) {
            return PENTAGON;
        }
        return HEXAGON;
    }
}
