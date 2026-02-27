#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${ROOT_DIR}/out"
mkdir -p "${OUT_DIR}"

echo "SphereRelaxator - Simple UI"
echo
echo "1) Generate Icosphere vertices CSV"
echo "2) Relax existing JSON sphere"
echo "3) Generate full tile CSV set (from scratch)"
echo
read -r -p "Choose action [1/2/3]: " ACTION

if [[ "${ACTION}" == "1" ]]; then
  read -r -p "Subdivisions (default 2): " SUBDIV
  SUBDIV="${SUBDIV:-2}"

  read -r -p "Output filename (default icosphere_vertices.csv): " OUT_NAME
  OUT_NAME="${OUT_NAME:-icosphere_vertices.csv}"

  OUT_PATH="${OUT_DIR}/${OUT_NAME}"
  echo
  echo "Building..."
  mvn -q -DskipTests package -f "${ROOT_DIR}/pom.xml"
  echo "Running generation..."
  "${ROOT_DIR}/run_relaxator.sh" \
    --mode icosphere \
    --subdivisions "${SUBDIV}" \
    --output "${OUT_PATH}"
  echo
  echo "Done: ${OUT_PATH}"
  exit 0
fi

if [[ "${ACTION}" == "2" ]]; then
  read -r -p "Input JSON path: " INPUT_JSON
  if [[ -z "${INPUT_JSON}" ]]; then
    echo "Input JSON path is required."
    exit 1
  fi

  read -r -p "Radius (default 500): " RADIUS
  RADIUS="${RADIUS:-500}"

  read -r -p "Iterations (default 350): " ITER
  ITER="${ITER:-350}"

  read -r -p "Threads (default 8): " THREADS
  THREADS="${THREADS:-8}"

  read -r -p "Output filename (default relaxed.json): " OUT_NAME
  OUT_NAME="${OUT_NAME:-relaxed.json}"
  OUT_PATH="${OUT_DIR}/${OUT_NAME}"

  echo
  echo "Building..."
  mvn -q -DskipTests package -f "${ROOT_DIR}/pom.xml"
  echo "Running relax..."
  "${ROOT_DIR}/run_relaxator.sh" \
    --mode relax \
    --input "${INPUT_JSON}" \
    --output "${OUT_PATH}" \
    --radius "${RADIUS}" \
    --iterations "${ITER}" \
    --threads "${THREADS}" \
    --progressEvery 10 \
    --logEvery 25
  echo
  echo "Done: ${OUT_PATH}"
  exit 0
fi

if [[ "${ACTION}" == "3" ]]; then
  read -r -p "Subdivisions (default 2): " SUBDIV
  SUBDIV="${SUBDIV:-2}"

  read -r -p "Output prefix (default full_sphere): " OUT_NAME
  OUT_NAME="${OUT_NAME:-full_sphere}"

  OUT_PATH="${OUT_DIR}/${OUT_NAME}"
  echo
  echo "Building..."
  mvn -q -DskipTests package -f "${ROOT_DIR}/pom.xml"
  echo "Running full CSV generation..."
  "${ROOT_DIR}/run_relaxator.sh" \
    --mode fullcsv \
    --subdivisions "${SUBDIV}" \
    --output "${OUT_PATH}"
  echo
  echo "Done. Files prefix: ${OUT_PATH}"
  exit 0
fi

echo "Unknown action: ${ACTION}"
exit 1
