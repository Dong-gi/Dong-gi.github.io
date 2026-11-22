package io.github.donggi.iroiroviewer.io

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 갤러리가 읽은 **찍힌 시각**의 캐시. 열쇠는 (경로·크기·수정 시각) — 셋 가운데 하나라도 바뀌면 다시 읽는다.
 *
 * ## 왜 Room 이 아니라 파일 하나인가
 *
 * 표를 하나 더하면 DB 판을 올리고 마이그레이션을 써야 한다. 이 값은 **언제든 다시 만들 수 있는 캐시**라 그 무게를 질 까닭이 없다 —
 * 잃어도 다음 훑기가 다시 읽을 뿐이다. 그래서 메모리의 표 + `filesDir` 의 작은 파일 하나(1만 장에 1 MB 안팎)로 둔다. 파일이 깨졌거나
 * 판이 다르면 빈 것으로 시작한다.
 *
 * `cacheDir` 가 아닌 까닭은 썸네일과 같다 — 시스템이 비우면 1만 장의 EXIF 를 다시 읽는다. 공유 저장소에 두지 않는 까닭도 같다(어떤
 * 사진이 있는가를 다른 앱이 읽는다).
 *
 * ## 시간대가 바뀌면 통째로 버린다
 *
 * 시차 칸이 없는 EXIF 는 읽을 때의 기기 시간대로 순간을 셈한다. 그 값을 다른 시간대에서 그대로 쓰면 날이 하루 밀린 사진이 섞인다.
 * 그래서 표는 셈한 시간대를 들고([useZone]), 파일 머리에도 적는다. 다르면 비운다 — 다시 읽는 값이 잘못 묶인 사진보다 싸다.
 *
 * 값 [NONE] 은 '열어서 읽었으나 날짜가 없다(또는 못 믿는다)' 다 — 날짜 없는 파일을 훑을 때마다 다시 여는 일을 막는다. 열지 못한
 * 것(일시적일 수 있다)은 넣지 않는다.
 */
class CaptureDateCache(private val file: File?) {

    private data class Rec(val size: Long, val mtime: Long, val taken: Long)

    /** 한 벌 — 셈한 시간대와 줄들. 파일 왕복의 단위다. */
    data class Snapshot(val zone: String, val entries: Map<String, Triple<Long, Long, Long>>)

    private val map = HashMap<String, Rec>()
    private var zone: String? = null
    private var loaded = false
    private var dirty = false

    /** 훑기를 시작할 때 부른다. 표가 다른 시간대로 셈한 것이면 비운다. */
    @Synchronized
    fun useZone(zoneId: String) {
        ensureLoaded()
        if (zone == zoneId) return
        if (map.isNotEmpty()) map.clear()
        zone = zoneId
        dirty = true
    }

    /** 찍힌 시각, [NONE](날짜 없음), 또는 `null`(아직 모른다 — 읽어야 한다). */
    @Synchronized
    fun lookup(path: String, size: Long, mtime: Long): Long? {
        ensureLoaded()
        val r = map[path] ?: return null
        return if (r.size == size && r.mtime == mtime) r.taken else null
    }

    /**
     * [zoneId] 는 그 값을 셈한 시간대다. 표의 시간대와 다르면 버린다 — 읽는 사이 다른 훑기가 [useZone] 으로 표를 다른 시간대로 바꿨을 수 있다.
     */
    @Synchronized
    fun put(path: String, size: Long, mtime: Long, taken: Long, zoneId: String) {
        ensureLoaded()
        if (zoneId != zone) return
        if (map.put(path, Rec(size, mtime, taken)) != Rec(size, mtime, taken)) dirty = true
    }

    /** [alive] 에 없는 경로를 지운다. **폴더를 끝까지 훑은 뒤에만** 부른다 — 중간에 부르면 아직 못 본 것을 지운다. */
    @Synchronized
    fun retainOnly(alive: Set<String>) {
        ensureLoaded()
        if (map.keys.retainAll(alive)) dirty = true
    }

    /** 이 경로들을 지운다(지우거나 옮긴 파일 — 다음 훑기를 기다리지 않고 경로를 걷는다). */
    @Synchronized
    fun forget(paths: Collection<String>) {
        ensureLoaded()
        if (map.keys.removeAll(paths.toSet())) dirty = true
    }

    /** 바뀐 것이 있으면 파일에 쓴다. 임시 파일 → fsync → rename 이라 쓰다 죽어도 앞의 것이 남는다. */
    @Synchronized
    fun save() {
        val f = file ?: return
        val z = zone ?: return
        if (!dirty) return
        val tmp = tmpOf(f)
        try {
            FileOutputStream(tmp).use { out ->
                write(Snapshot(z, map.mapValues { (_, r) -> Triple(r.size, r.mtime, r.taken) }), out)
                out.fd.sync()
            }
            if (tmp.renameTo(f)) dirty = false else tmp.delete()
        } catch (e: IOException) {
            tmp.delete()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val f = file ?: return
        // 앞 프로세스가 쓰다 죽어 남은 임시 파일. 경로가 든 파일이라 남겨 두지 않는다.
        tmpOf(f).delete()
        // 파일이 없으면 디스크를 읽지 않는다 — 갤러리를 한 번도 열지 않은 기기에서 이 객체를 불러도 비용이 없다.
        if (!f.isFile) return
        val snap = try {
            f.inputStream().use { read(it) }
        } catch (e: IOException) {
            return // 열지 못했다 — 다음 저장이 덮는다
        }
        if (snap == null) {
            // 판·머리가 다르거나 잘렸다. 쓰지 못할 파일에 경로를 평문으로 남겨 두지 않는다.
            f.delete()
            return
        }
        zone = snap.zone
        snap.entries.forEach { (path, v) -> map[path] = Rec(v.first, v.second, v.third) }
    }

    companion object {
        /** `filesDir` 아래 파일 이름. */
        const val FILE_NAME = "gallery-dates.bin"

        @Volatile private var shared: CaptureDateCache? = null

        /** 이 프로세스에서 이미 만든 것(갤러리를 한 번이라도 열었다). 없으면 `null` — 파일 작업 뒤의 경로 걷기가 큰 캐시를 새로 올리지 않게. */
        fun sharedIfOpened(): CaptureDateCache? = shared

        /** 프로세스에 하나. 갤러리를 다시 열 때 파일을 다시 읽지 않고, 두 훑기가 같은 표를 본다. */
        fun shared(filesDir: File): CaptureDateCache =
            shared ?: synchronized(this) { shared ?: CaptureDateCache(File(filesDir, FILE_NAME)).also { shared = it } }

        /** 날짜가 없다는 표시. 실제 시각과 겹치지 않는다. */
        const val NONE = Long.MIN_VALUE

        private const val MAGIC = 0x49524F44 // "IROD"
        private const val VERSION = 2

        private fun tmpOf(f: File) = File(f.parentFile, f.name + ".tmp")

        /** 한 벌을 쓴다. 순수 스트림 함수라 JVM 시험이 왕복을 본다. */
        fun write(snapshot: Snapshot, out: OutputStream) {
            val d = DataOutputStream(BufferedOutputStream(out))
            d.writeInt(MAGIC)
            d.writeInt(VERSION)
            d.writeUTF(snapshot.zone)
            d.writeInt(snapshot.entries.size)
            for ((path, v) in snapshot.entries) {
                d.writeUTF(path)
                d.writeLong(v.first)
                d.writeLong(v.second)
                d.writeLong(v.third)
            }
            d.flush()
        }

        /** 읽는다. 머리가 다르거나·판이 다르거나·잘렸으면 `null`(빈 캐시로 시작한다). */
        fun read(input: InputStream): Snapshot? {
            val d = DataInputStream(BufferedInputStream(input))
            return try {
                if (d.readInt() != MAGIC || d.readInt() != VERSION) return null
                val zone = d.readUTF()
                val n = d.readInt()
                if (n < 0) return null
                val out = HashMap<String, Triple<Long, Long, Long>>(minOf(n, 1 shl 16))
                repeat(n) {
                    out[d.readUTF()] = Triple(d.readLong(), d.readLong(), d.readLong())
                }
                Snapshot(zone, out)
            } catch (e: IOException) {
                // 잘린 파일(`EOFException`)·깨진 글(`UTFDataFormatException`)도 여기로 온다.
                null
            }
        }
    }
}
