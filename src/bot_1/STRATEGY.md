# Bot-1 Tahap-1: Strategi Greedy

## Fungsi Objektif
Memaksimalkan area paint tim secepat mungkin (proxy untuk objective >70% map).

## Ide Greedy Tahap-1
1. Soldier selalu memilih aksi paint dengan skor tertinggi lokal:
   - Skor 100: tile enemy paint
   - Skor 60: tile empty
   - Skor -20: tile ally paint
2. Setelah paint, soldier memilih langkah dengan skor tile tertinggi untuk ekspansi.
3. Mopper memilih tile enemy paint terdekat yang bisa langsung dibersihkan.
4. Tower memprioritaskan spawn Soldier agar laju ekspansi tinggi, fallback ke Mopper.

## Kenapa Ini Disebut Greedy
Di setiap turn, keputusan diambil berdasarkan keuntungan langsung paling besar (local maximum), tanpa perencanaan global jangka panjang.

## Batasan Tahap-1
- Belum ada pathfinding menuju objective global.
- Belum ada pembagian role adaptif berdasarkan fase game.
- Belum ada manajemen paint/chips tingkat lanjut.

## Rencana Tahap-2
- Tambah objective global: ruin terdekat dan frontier wilayah lawan.
- Tambah prioritas adaptif spawn (soldier/mopper/splasher) berdasarkan state lokal.
- Tambah heuristik konservasi paint saat cadangan rendah.
