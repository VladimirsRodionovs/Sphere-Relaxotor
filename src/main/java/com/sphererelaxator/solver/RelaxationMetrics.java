package com.sphererelaxator.solver;

public record RelaxationMetrics(
        double edgeMin,
        double edgeMax,
        double edgeMean,
        double edgeStdDev,
        double pentagonAreaMean,
        double hexAreaMean
) {
}
