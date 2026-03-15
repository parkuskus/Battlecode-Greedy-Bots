# Tugas Besar 1 IF2211 Strategi Algoritma
## Jambi X Bekasi - Battlecode 2025 Greedy Bots

Repositori ini berisi implementasi tiga bot Battlecode 2025 berbasis algoritma greedy:
- `main_bot` (bot utama, final)
- `alternative_bots_1` (varian offensive)
- `alternative_bots_2` (varian coverage-maximizing)

Selain source bot, repositori ini juga berisi laporan lengkap LaTeX (`docs/`).

## Tim
- Kevin Wirya Valerian (13524019)
- Muhammad Aufar Rizqi Kusuma (13524061)
- Athilla Zaidan Zidna Fann (13524068)

## Ringkasan Strategi

### 1. `main_bot` - `scoring-based nearest-target` (final)
Fokus utama:
- Tower-first expansion yang tetap seimbang dengan combat oportunistik.
- Soldier dengan state `EXPLORE`, `REFILL`, `COMBAT`, `BUILD_TOWER`, `BUILD_SRP`.
- Splasher untuk akselerasi coverage (menghindari splash berisiko ke tower musuh).
- Mopper sebagai support stabilizer (mop swing, cleanup enemy paint, transfer paint).
- Tower spawn berbasis fase (early Soldier-heavy, mid balanced, late Splasher-heavy).

Kekuatan utama:
- Stabil lintas fase game (early-mid-late).
- Ekspansi + sustain cenderung konsisten di banyak map.

### 2. `alternative_bots_1` - `Greedy Offensive`
Fokus utama:
- Tekanan ofensif tinggi: `RUSH`/`ATTACK` cepat ke arah target musuh.
- Splasher dan Soldier lebih agresif mengejar momentum serang.
- Build/refill tetap ada tetapi bukan objektif utama.

Kekuatan utama:
- Bisa memberi pressure tinggi pada map/map state yang cocok.

Trade-off:
- Lebih sensitif terhadap kegagalan early push dan manajemen resource.

### 3. `alternative_bots_2` - `coverage-maximizing greedy`
Fokus utama:
- Coverage-first untuk unggul area (termasuk skenario tiebreak akhir).
- Soldier `REFILL`, `COMBAT`, `BUILD`, `SRP`, `COVER`.
- Sector partitioning dan eksplorasi yang minim overlap.
- Spawn phase-based berorientasi coverage.

Kekuatan utama:
- Kontrol area yang stabil pada map terbuka/semi-terbuka.

## Struktur Proyek

```text
.
├── src/
│   ├── main_bot/
│   ├── alternative_bots_1/
│   └── alternative_bots_2/
├── docs/
│   ├── main.tex
│   └── sections/
├── matches/
├── artifacts/
│   ├── engine/engine.jar
│   └── client/
├── build.gradle
├── gradle.properties
└── README.md
```

Catatan:
- `artifacts/engine/engine.jar` dan artifact client sudah disediakan di repo ini.
- Output replay pertandingan headless disimpan di folder `matches/`.

## Prasyarat
- JDK 21 (wajib)
- OS: Windows/Linux/macOS
- (Opsional) TeX Live + `latexmk` jika ingin build laporan PDF

## Quick Start

### 1. Build project
```bash
./gradlew build
```
### 2. Jalankan App
Untuk menjalankan App, jalankan "Stima Battle Client.exe" di folder client

### 3. Lihat bot yang tersedia
```bash
./gradlew -q listPlayers
```

Output yang diharapkan:
- `main_bot`
- `alternative_bots_1`
- `alternative_bots_2`

## Konfigurasi `gradle.properties`
File ini berisi default untuk run task:
- `teamA`
- `teamB`
- `maps`
- `debug`
- `outputVerbose`
- `showIndicators`
- `validateMaps`
- `alternateOrder`

Kamu bisa:
- mengubah default di file ini, atau
- override langsung lewat `-Pkey=value` saat menjalankan command.

## Build Laporan PDF
Laporan ada di `docs/` dengan entry point `docs/main.tex`.
