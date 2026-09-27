package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.format.cfb.CfbFormatException

/**
 * `\u0006DataSpaces` 저장소(MS-OFFCRYPTO 2.1) — 패키지에 **어떤 변환**이 걸렸는지 적는 곳.
 *
 * 암호 문서는 변환이 `StrongEncryptionTransform`(ID `{FF9A3F03-56EF-4613-BDD5-5A41C1D07246}`) 하나다.
 * IRM(권한 관리)으로 잠긴 문서는 `DRMEncryptedTransform`(`{C73DFACD-061F-43B0-8B64-0C620D2A8B50}`)이 걸리고,
 * 열쇠는 암호가 아니라 **권한 서버가 내주는 사용 허가**다 — 암호를 물어도 소용이 없다.
 *
 * ## 읽는 범위
 *
 * `TransformInfo/<변환>/\u0006Primary` 의 머리에서 **변환 ID** 만 읽는다(`TransformInfoHeader` 의
 * `TransformID`, UNICODE-LP-P4). 저장소 하나에서 앞 512바이트만 본다 — DRM 의 `\u0006Primary` 에는 뒤에
 * 수 KB 짜리 사용 허가(XrML)가 붙는다.
 *
 * **방어적으로 읽는다.** 이 저장소가 없거나, 있어도 모양이 이상해 ID 를 못 읽으면 '모른다' 로 치고
 * `EncryptionInfo` 에 맡긴다. 읽힌 ID 가 암호 변환이 **아니면** 그때만 '암호로 풀 수 없다' 다.
 */
internal object OfficeCryptoDataSpaces {

    const val PASSWORD_TRANSFORM = "{FF9A3F03-56EF-4613-BDD5-5A41C1D07246}"
    const val DRM_TRANSFORM = "{C73DFACD-061F-43B0-8B64-0C620D2A8B50}"

    private const val HEAD = 512
    private const val MAX_ID_BYTES = 256

    /** 암호가 아닌 변환(IRM 등)이 걸려 있는가. */
    fun hasForeignTransform(cfb: CfbFile, dataSpaces: CfbEntry): Boolean {
        val transforms = cfb.child(dataSpaces, "TransformInfo") ?: return false
        if (!transforms.isStorage) return false
        // 자식 수는 CFB 리더가 항목 수 상한으로 이미 잘랐다.
        for (t in cfb.children(transforms)) {
            if (!t.isStorage) continue
            val primary = cfb.child(t, "\u0006Primary") ?: continue
            if (!primary.isStream) continue
            val id = transformId(cfb, primary) ?: continue
            if (!id.equals(PASSWORD_TRANSFORM, ignoreCase = true)) return true
        }
        return false
    }

    /** `TransformLength`(4) · `TransformType`(4) · `TransformID`(길이 4 + UTF-16LE). 못 읽으면 null. */
    private fun transformId(cfb: CfbFile, primary: CfbEntry): String? {
        val head = ByteArray(HEAD)
        val n = try {
            cfb.openStream(primary).use { input ->
                var read = 0
                while (read < HEAD) {
                    val r = input.read(head, read, HEAD - read)
                    if (r < 0) break
                    read += r
                }
                read
            }
        } catch (e: CfbFormatException) {
            return null
        }
        if (n < 12) return null
        val len = (head[8].toInt() and 0xFF) or ((head[9].toInt() and 0xFF) shl 8) or
            ((head[10].toInt() and 0xFF) shl 16) or ((head[11].toInt() and 0xFF) shl 24)
        if (len <= 0 || len % 2 != 0 || len > MAX_ID_BYTES || 12 + len > n) return null
        val chars = CharArray(len / 2) { i ->
            ((head[12 + i * 2].toInt() and 0xFF) or ((head[13 + i * 2].toInt() and 0xFF) shl 8)).toChar()
        }
        return String(chars).trimEnd('\u0000')
    }
}
