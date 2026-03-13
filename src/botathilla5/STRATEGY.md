# botathilla5 — Strategy Document

**Tugas Besar 1 IF2211 Strategi Algoritma**
Battlecode 2025 — Greedy Paint Coverage Bot

---

## Filosofi Utama

Bot ini menggunakan **algoritma greedy** dengan fokus utama pada **paint coverage** (menang di 70% paint). Setiap keputusan robot diambil berdasarkan prioritas greedy yang sudah ditetapkan tanpa perencanaan jangka panjang — selalu pilih aksi terbaik saat ini.

---

## Arsitektur

```
RobotPlayer.java   — Entry point, dispatch ke bot spesifik per tipe
├── SoldierBot.java — Tower builder + attacker + SRP builder + painter (CORE)
├── SplasherBot.java— Area painter (coverage pusher)
├── MopperBot.java  — Support: transfer paint + hapus enemy paint
├── TowerBot.java   — Spawn units + attack + upgrade
├── Nav.java        — Navigasi: FuzzyMove + BugNav + anti-oscillation
└── Messaging.java  — Komunikasi antar-robot via bit-packed 32-bit messages
```

---

## Strategi Greedy per Robot

### Soldier (Unit Utama)
State machine dengan 5 state, dipilih greedy setiap turn:

| Prioritas | State | Kondisi |
|-----------|-------|---------|
| 1 | **REFILL** | Paint < 10% DAN ada tower dalam jarak √100 |
| 2 | **COMBAT** | Ada tower musuh & HP > 40 & paint > 30% |
| 3 | **BUILD_TOWER** | Ada ruin kosong & chips ≥ 200 |
| 4 | **BUILD_SRP** | Tower ≥ 3, ada paint tower, aman, chips ≥ 100 |
| 5 | **EXPLORE** | Default — frontier-based paint expansion |

**Greedy decisions:**
- **Tower type**: Defense jika musuh dekat ruin (dist² ≤ 36), else Money/Paint berdasarkan mana yang kurang
- **Ruin selection**: Skor = `correct*3 - missing*2 - dist²/4` — prioritas ruin yang hampir selesai
- **SRP placement**: Grid tileable (x%4==2, y%4==2) — no overlap between SRPs
- **Explore direction**: Prime scatter (`myID * 37 % 8`) mengurangi clustering antar soldier

### Splasher (Area Painter)
2 state: EXPLORE dan REFILL.

**Greedy splash targeting:**
- Evaluasi setiap posisi dalam action radius
- Skor inner ring (3×3): empty +2, enemy +4
- Skor outer ring (4 cardinal tiles): empty +1
- **Skip splash yang mengenai tower musuh** (splasher bukan combat unit)
- Minimum splash value = 3 agar worth cost 50 paint
- Bergerak ke cluster paintable tiles (centroid of empty+enemy tiles)

### Mopper (Support)
Prioritas aksi setiap turn:
1. **Refill** jika paint < 15%
2. **Mop Swing** ke robot musuh (prioritas arah dengan paling banyak target)
3. **Mop** robot musuh adjacent (dist² ≤ 2)
4. **Mop** enemy paint di tanah (hanya adjacent — TIDAK chase)
5. **Transfer paint** ke ally yang butuh (ratio < 40%)
6. **Move**: chase musuh on ally paint → mop adjacent enemy paint → follow soldier → patrol

**Key insight**: Mopper TIDAK agresif chase ke teritori musuh (double paint penalty = mati cepat).

### Tower (Spawner)
**Spawn ratio by phase:**
| Phase | Round | Ratio |
|-------|-------|-------|
| Early | < 150 | 4 Soldier : 1 Mopper |
| Mid | 150-400 | 2 Soldier : 2 Splasher : 1 Mopper |
| Late | > 400 | 2 Splasher : 1 Soldier : 1 Mopper |

**Greedy decisions:**
- Emergency spawn mopper jika ada soldier musuh dekat tower
- Upgrade konservatif (level 2 pada round ≥ 100, level 3 pada round ≥ 400)
- Broadcast info tower pada turn pertama (sekali saja)
- Attack prioritas: soldier musuh dulu, lalu lowest HP

---

## Navigasi

### Fuzzy Move
- Evaluasi 7 arah (target ± 3 rotasi) berdasarkan tile score
- **Tile score**: ally paint +4, empty +2, enemy paint -8, crowding -2/ally
- Anti-oscillation: penalty -30 jika tile ada di history 6 posisi terakhir

### BugNav
- Aktivasi saat fuzzy move gagal (obstacle)
- Wall-following dengan direction stack (max 80)
- Reset saat target berubah signifikan (dist² > 8 dari target lama)
- **Safe variant**: hindari tile dalam range tower musuh

---

## Komunikasi

### Message Format (32-bit packed)
```
[31..29] code (3 bit) | [28..23] x (6 bit) | [22..17] y (6 bit) | [16..15] payload (2 bit) | [14..0] unused
```

### Message Types
| Code | Arti | Payload |
|------|------|---------|
| 0 | Tower dibangun | 0=Paint, 1=Money, 2=Defense |
| 1 | Butuh mopper di lokasi | — |
| 2 | Tower musuh ditemukan | — |

### Relay System
- Robot relay info tower ke tower terdekat (max 3 pesan/interaksi)
- Tower relay info ke robot baru saat spawn (max 3 pesan)
- Tower broadcast sekali di turn pertama via `broadcastMessage`

---

## Painting Strategy (Mark-Based)

Sistem painting menggunakan **marks** bawaan API:
1. `markTowerPattern(type, ruin)` / `markResourcePattern(center)` — tandai pattern sekali
2. Iterasi `senseNearbyMapInfos(center, 8)`:
   - Jika `getMark() != EMPTY` dan `getMark() != getPaint()` → paint tile
   - Warna ditentukan oleh mark: `ALLY_SECONDARY` → `useSecondary=true`
3. **Hanya mark sekali** — cek apakah tile sudah marked sebelum re-marking (hemat bytecode + paint)

Keunggulan vs hardcoded pattern arrays:
- Tidak ada risiko mismatch antara pattern dan actual marks
- Soldiers lain bisa melanjutkan painting yang sudah di-mark
- Kompatibel dengan `completePattern` check

---

## Key Improvements dari v3

| # | Fix | Dampak |
|---|-----|--------|
| 1 | Mark-based painting | Eliminasi mismatch pattern, hemat bytecode |
| 2 | Only mark once | Hemat ~25 paint/turn per soldier |
| 3 | Fix stale `me` | Action setelah move tidak salah posisi |
| 4 | Refill dari ANY tower | Tidak mati kehabisan paint di dekat money/defense tower |
| 5 | Combat threshold 30% | Soldier tidak masuk combat hampir kosong |
| 6 | Broadcast first turn | Reliable tower discovery tanpa race condition round < 5 |
| 7 | Hapus tower flickering | Tidak self-sabotage ekonomi |
| 8 | Prime scatter explore | Soldier menyebar lebih merata ke seluruh map |
| 9 | Cap relay 3 msg | Hemat message quota robot |
| 10 | Correct outer ring splash | Skor splash akurat — tidak overcount |
| 11 | Hapus Math.sqrt | Deterministic + hemat bytecode |
| 12 | SRP chip guard | Tidak bankrut bangun SRP saat chips rendah |

---

## Win Condition

- **Target**: 70% paint coverage → instant win
- **Tiebreaker**: Minimize penalty tiles (enemy paint + uncovered) di akhir round
- **Strategi**: Greedy expand territory outward dari spawn, bangun tower ASAP untuk snowball ekonomi, splasher push coverage di mid-late game
