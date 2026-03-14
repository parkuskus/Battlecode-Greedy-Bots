# GEMILANG2 Greedy Strategy Validation

## 1) Greedy Contract (Global)

Semua keputusan utama di `gemilang2` mengikuti kontrak:

1. Bentuk candidate set `C`
2. Filter feasibility `F`
3. Hitung skor `S`
4. Pilih `argmax(S)` dengan deterministic tie-break: jarak lebih dekat, lalu tie-id lebih kecil

Score standar:

`Score = 2.6*paint_gain + 2.2*enemy_paint_removed + 3.1*objective + 1.8*tempo + 1.1*support - 2.8*risk - 1.0*paint_cost`

Implementasi inti ada di `GreedyCore`.

## 2) Formal C/F/S Mapping per Unit

### Soldier (objective-progress heavy)
- `C`: `RETREAT`, `REFILL`, `SKIRMISH`, `COMBAT_TOWER`, `BUILD_TOWER`, `BUILD_SRP`, `EXPLORE`
- `F`: cek paint ratio, HP, jarak refill, keberadaan target ruin/SRP, builder cap ruin, timeout build
- `S`: objective progress (tower/SRP), paint economy, risk pressure lokal, support ally
- Tie-break: jarak target dan tie-id location

### Splasher (area-net-value heavy)
- `C`: state `RETREAT`, `REFILL`, `PRESSURE`, `EXPLORE`; kandidat splash target pada radius aksi
- `F`: canAttack target, paint cukup, batas risk terhadap enemy tower
- `S`: enemy paint cleaned + frontier gain + empty conversion - risk - overpaint cost
- Tie-break: jarak target splash, tie-id location

### Mopper (cleanup/support heavy)
- `C`: `REFILL`, `CLEANUP`, `SUPPORT`, `SKIRMISH`, `PATROL`; kandidat `swing` dan `attack` tile
- `F`: canMopSwing/canAttack, radius valid, danger gate
- `S`: enemy paint removal, cleanup urgency, support signal relevance, risk dan paint cost
- Tie-break: jarak dan tie-id

### Tower (composition/economy-pressure heavy)
- `C`: spawn `SOLDIER`, `SPLASHER`, `MOPPER` + kandidat spawn location
- `F`: canBuildRobot pada slot, budget paint/chips, message budget constraint
- `S`: frontier demand, pressure enemy, composition deficit, economy tempo, crowding/risk lokasi
- Tie-break: jarak pressure point dan tie-id location

## 3) Runtime Greedy Proof

Runtime flags:
- `bc.testing.greedyTrace=true|false`
- `bc.testing.greedyAssert=true|false`

Trace format:
- `GDEC` (tiap 10 ronde): decision greedy + score chosen + gap
- `GMET` (tiap 50 ronde): metric agregat

Contoh cuplikan trace (3 match berbeda):

1. `tmp/gemilang2-trace-medium.log`

`[A: #11540@10] GDEC|pkg=gemilang2|u=SOLDIER|d=STATE|cs=32.574|gap=17.104|rej=5|a=BUILD_TOWER`

2. `tmp/gemilang2-trace-small.log`

`[A: #1@10] GDEC|pkg=gemilang2|u=TOWER|d=SPAWN_TYPE|cs=998.000|gap=998.000|rej=0|a=MOPPER`

3. `tmp/gemilang2-trace-himothee.log`

`[A: #13054@10] GDEC|pkg=gemilang2|u=MOPPER|d=STATE|cs=10.580|gap=0.000|rej=4|a=PATROL`

## 4) Compliance Notes

- Message cap tetap: robot <= 1, tower <= 20 per ronde
- Message schema tetap 32-bit internal
- Bytecode guard dipakai pada loop sensing/scoring kritis
- Tanpa API Java terlarang pada runtime Battlecode
