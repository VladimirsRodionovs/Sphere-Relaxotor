package com.sphererelaxator.mesh;

public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double value) {
        return new Vec3(x * value, y * value, z * value);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vec3 normalize() {
        double len = length();
        if (len < 1e-12) {
            return this;
        }
        return scale(1.0 / len);
    }

    public double distance(Vec3 other) {
        return subtract(other).length();
    }
}
