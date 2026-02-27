package com.sphererelaxator.solver;

public record RelaxationConfig(
        int iterations,
        double radius,
        double step,
        double laplacianWeight,
        double springWeight,
        double pentagonExpandWeight,
        int threads,
        int logEvery,
        int progressEvery
) {
}
