# ARCHITECTURE — SphereRelaxator

## Назначение
`SphereRelaxator` — оффлайн инструмент релаксации сферической сетки (Goldberg/icosphere). Он выравнивает длины ребер, снижает локальные деформации (особенно вокруг пентагонов) и сохраняет геометрию обратно в JSON.

## Технологический стек
- Java 17
- Maven
- Jackson

## Высокоуровневый поток
1. CLI принимает параметры релаксации.
2. `io/*` читает входной JSON в доменную структуру mesh.
3. `mesh/*` строит внутреннее представление графа вершин/тайлов.
4. `solver/SphereRelaxator` выполняет итерации релаксации.
5. После каждой итерации вершины проектируются на радиус сферы (`|v| = R`).
6. Результат записывается в JSON; при необходимости добавляются UV для Unreal-формата.

## Слои
### 1) CLI / orchestration
- Класс: `com.sphererelaxator.SphereRelaxatorCli`
- Роль: парсинг параметров (`iterations`, `step`, `radius`, веса компонентов, `threads`, `emitUv`) и запуск процесса.

### 2) Solver
- Пакет: `com.sphererelaxator.solver`
- Ключевые классы:
  - `SphereRelaxator` — основной итеративный алгоритм;
  - `RelaxationConfig` — конфигурация;
  - `RelaxationMetrics` — метрики качества/сходимости.

### 3) Mesh model
- Пакет: `com.sphererelaxator.mesh`
- Ключевые классы:
  - `Mesh`, `MeshBuilder`, `Tile`, `TileType`, `Vec3`.
- Роль: топология и геометрия сетки.

### 4) IO DTO
- Пакет: `com.sphererelaxator.io`
- Классы: `MeshDocument`, `VertexDto`, `TileDto`
- Роль: сериализация/десериализация JSON-контракта.

### 5) Format adapters / utilities
- Пакет: `com.sphererelaxator.unreal`
- Классы: `UnrealFormatProcessor`, `UnrealTileCsvExporter`
- Роль: поддержка Unreal-like формата и экспортных вспомогательных артефактов.

### 6) Optional generators
- Пакет: `com.sphererelaxator.generator`
- Классы: `IcosphereGenerator`, `FullSphereCsvGenerator`
- Роль: генерация базовой тестовой геометрии.

## Алгоритмическая модель
На каждой итерации применяется комбинация воздействий:
- spring-like коррекция длин ребер;
- Laplacian smoothing;
- дополнительное расширение в окрестностях пентагонов.

После суммарного смещения каждая вершина нормализуется к целевому радиусу. Это сохраняет глобальную сферическую форму и устраняет радиальный дрейф.

## Вход/выход
- Вход: JSON mesh (`vertices`, `tiles`, `radius`) или Unreal-like массивы.
- Выход: обновленный JSON mesh; опционально UV-координаты для spherical mapping.

## Интеграции
- Используется как подготовительный этап геометрии для `PlanetSurfaceGenerator`/клиентского рендера.
- Может экспортировать промежуточные артефакты в CSV для диагностики.

## Точки расширения
- Добавление новых метрик сходимости в `RelaxationMetrics`.
- Новые политики шага/весов в `RelaxationConfig`.
- Новые адаптеры форматов входа/выхода рядом с `unreal/*`.

## Риски
- Переизбыточный шаг релаксации может вызывать локальные артефакты/осцилляции.
- Высокое число итераций заметно увеличивает время выполнения.
- Несовместимость внешнего JSON-контракта ломает импортер без адаптера.

## Быстрая навигация
- Entry point: `src/main/java/com/sphererelaxator/SphereRelaxatorCli.java`
- Solver: `src/main/java/com/sphererelaxator/solver/SphereRelaxator.java`
- Mesh model: `src/main/java/com/sphererelaxator/mesh/`
- Unreal adapter: `src/main/java/com/sphererelaxator/unreal/`
