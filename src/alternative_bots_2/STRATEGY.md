# Strategi aufar_bot_2 — Greedy Coverage-Maximizing Bot

## Filosofi Utama

Bot ini menggunakan pendekatan **greedy** yang berfokus pada **memaksimalkan paint coverage** untuk memenangkan tiebreak di round 2000. Setiap unit membuat keputusan optimal secara lokal setiap turn tanpa perencanaan global yang kompleks.

Prinsip inti:
- **Coverage first** — Semua keputusan diprioritaskan berdasarkan seberapa banyak tile baru yang bisa di-paint.
- **Aggressive exploration** — Unit terus bergerak ke territory baru, bukan bertahan di area yang sudah di-paint.
- **Minimal downtime** — Threshold refill rendah agar unit lebih lama di lapangan melakukan painting.

---

## Arsitektur Modular

```
aufar_bot_2/
├── RobotPlayer.java   — Entry point, dispatch ke unit controller
├── Nav.java            — Navigasi (BugNav, FuzzyMove, SafeMove, tile scoring)
├── Comms.java          — Messaging bit-packed 32-bit, tower registry
├── SoldierBot.java     — Explorer & builder utama
├── SplasherBot.java    — Area paint specialist
├── MopperBot.java      — Enemy paint cleaner & unit drainer
└── TowerBot.java       — Unit spawner & tower manager
```

Setiap file independen dan berkomunikasi lewat shared state di `RobotPlayer` dan `Comms`.

---

## Strategi Per Unit

### Soldier (SoldierBot.java)

**Prioritas greedy setiap turn:**
1. **REFILL** — Retreat ke paint tower terdekat jika paint < 20% (threshold agresif agar lebih lama painting)
2. **COMBAT** — Serang enemy tower jika HP > 40 dan bukan defense tower
3. **BUILD** — Bangun tower di ruin paling lengkap (greedy: pilih ruin dengan paling banyak ally paint)
4. **SRP** — Bangun Special Resource Pattern jika aman dan tower ≥ 4
5. **COVER** — Paint tile kosong & eksplorasi territory baru

**Fitur unggulan:**
- **Sector Partitioning** — Peta dibagi 3×2 = 6 sektor. Setiap soldier di-assign ke sektor berdasarkan `myID % 6`. Soldier memprioritaskan painting di sektornya sendiri → mengurangi overlap antar soldier.
- **Coverage Scoring** — Tile kosong diberi skor +10, enemy paint +6, bonus sektor +8, minus jarak. Soldier selalu bergerak ke tile dengan skor coverage tertinggi.
- **Endgame Mode** — Setelah round 1500, soldier langsung masuk mode COVER murni (mengabaikan combat/build) untuk memaksimalkan coverage menjelang tiebreak.
- **Explore Cycle** — Bergantian menuju: sector center → diagonal musuh → random edge, agar eksplorasi merata ke seluruh peta.

**Tower type decision (greedy):**
- Money tower first (rasio 2.5:1 vs paint tower di awal, 1.5:1 setelah 6 money tower)
- Paint tower jika sudah cukup money tower

### Splasher (SplasherBot.java)

**Prioritas greedy:**
1. **REFILL** — Threshold 20% (sangat agresif — splasher punya paint capacity besar)
2. **SPLASH** — Skor semua tile attackable dengan grid 11×11, serang jika skor ≥ 2 (threshold rendah = lebih agresif)
3. **FRONTIER** — Gerak menuju pusat enemy paint untuk cari target baru
4. **EXPLORE** — Gerak menuju cluster tile kosong jika tidak ada enemy paint

**Fitur unggulan:**
- **Dual-Purpose Targeting** — Selain flip enemy paint, juga memberi bonus skor untuk tile kosong (EMPTY). Splasher bisa paint area kosong sekaligus flip area musuh.
- **Low Splash Threshold** — Threshold 2 (vs 3-4 di bot standar) → lebih sering melakukan splash → coverage lebih cepat.
- **Empty Cluster Seeking** — Saat tidak ada enemy paint, splasher mencari cluster tile kosong dan bergerak ke sana.

### Mopper (MopperBot.java)

**Prioritas greedy:**
1. **REFILL** — Threshold 25%/75%
2. **COMBAT** — Serang enemy unit (prioritas: soldier dengan paint terendah) hanya jika dalam jarak ≤ 9
3. **MOPPING** — Bersihkan enemy paint (prioritas: dekat ruin → bisa bangun tower)
4. **EXPLORE** — Ikuti soldier terdekat untuk support, atau pergi ke lokasi yang diminta tower

**Fitur unggulan:**
- **Mop-Swing Multi-Hit** — Deteksi arah cardinal dengan 2+ musuh, eksekusi mop swing untuk damage massal.
- **Paint Transfer Network** — Mopper berbagi paint ke ally yang low-paint (< 15 paint), menjaga soldier tetap productive.
- **Ruin-Prioritized Mopping** — Bersihkan enemy paint dekat ruin terlebih dahulu agar soldier bisa bangun tower.
- **Selective Combat** — Hanya engage combat jika target dalam jarak dekat (≤ 9), menghindari overcommit.

### Tower (TowerBot.java)

**Prioritas greedy:**
1. **Upgrade** — Jika chips cukup (paint: 2500, lainnya: 3600) dan ada ally di sekitar
2. **Attack** — Prioritas enemy soldier dengan HP terendah
3. **Broadcast** — Kirim lokasi tower di early game (round < 5)
4. **Relay** — Sebarkan tower registry ke unit terdekat
5. **Spawn** — Phase-based ratio (lihat di bawah)
6. **Self-Destruct** — Money tower disintegrate jika chips > 10000, aman, round > 200

**Phase-Based Spawning (coverage-optimized):**

| Phase | Round | Rasio | Alasan |
|-------|-------|-------|--------|
| Early | < 200, towers ≤ 5 | 3:1 soldier:splasher | Klaim territory cepat |
| Mid | 200-800 | 2:1:1 soldier:splasher:mopper | Ekspansi seimbang |
| Late | > 800 | 1:2:1 soldier:splasher:mopper | Area denial berat |

**Spawn Location:**
- Hindari edge peta (3 tile dari batas)
- Prefer lokasi dengan sedikit ally (kurangi crowding)
- Fallback: spawn ke arah diagonal musuh

---

## Navigasi (Nav.java)

### Tile Scoring (Coverage-Aware)
Semua movement menggunakan skor tile yang memprioritaskan coverage:
| Tile Paint | Skor | Alasan |
|-----------|------|--------|
| EMPTY | +6 | Target utama — tile baru untuk di-paint |
| Enemy | +4 | Flip enemy paint = coverage swing |
| Ally | +1 | Aman tapi tidak menambah coverage |
| Wall | -9999 | Tidak bisa dilewati |

### Tiga Tier Movement
1. **Fuzzy Move** — Pilih dari 7 arah (main + rotasi ±1,±2,±3), pilih tile dengan skor tertinggi
2. **Safe Fuzzy Move** — Seperti fuzzy tapi tolak tile dalam jangkauan enemy tower
3. **BugNav** — Wall-following dengan tile scoring untuk pathfinding melewati obstacle

---

## Komunikasi (Comms.java)

### Format Pesan (32-bit packed)
```
[31..29] 3-bit code
[28..23] 6-bit x coordinate (max 63)
[22..17] 6-bit y coordinate (max 63)
[16..15] 2-bit payload (tower type)
[14..0]  unused
```

### Kode Pesan
| Code | Nama | Kegunaan |
|------|------|----------|
| 0 | TOWER_BUILT | Broadcast lokasi tower baru |
| 1 | NEED_MOPPER | Tower minta mopper ke lokasi enemy paint |
| 2 | ENEMY_TOWER | (reserved) |

### Routing Rules (Battlecode constraint)
- **Robot → Tower**: `sendMessage` (robot hanya bisa kirim ke tower)
- **Tower → Robot**: `sendMessage` atau `broadcastMessage`
- Relay dibatasi 3-4 tower per turn untuk hemat bytecode

---

## Keunggulan vs kevin_bot_1

| Aspek | kevin_bot_1 | aufar_bot_2 |
|-------|-------------|-------------|
| Tile scoring | +2 ally, -2 enemy | +6 empty, +4 enemy, +1 ally |
| Refill threshold | 25% | 20% (soldier), 20% (splasher) |
| Sector partitioning | Tidak ada | 3×2 sektor per soldier |
| Endgame mode | Tidak ada | Round 1500+ pure coverage |
| Splasher targeting | Enemy only (threshold 3) | Enemy + empty (threshold 2) |
| Spawning late | 1:1:1 balanced | 1:2:1 splasher-heavy |
| Explore strategy | Random edge bounce | Cycle: sector → diagonal → edge |
