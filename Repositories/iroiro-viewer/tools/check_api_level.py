"""순수 JVM 모듈이 부르는 자바 API 가 안드로이드의 minSdk 에 있는지 본다 — iroiro-browser 의 같은 이름 도구를 옮겨 왔다.

그 모듈들은 JDK 로 컴파일되므로 컴파일러도 JVM 시험도 안드로이드에 없는 함수를 모른다 — 기기에서 `NoSuchMethodError` 로 앱이 죽는다
(7단계의 `InputStream.readNBytes`, 걷은 `core:sftp` 의 `ByteArrayOutputStream.toString(Charset)` — 둘 다 API 33). lint 의 NewApi 는
안드로이드 모듈만 본다. 그래서 컴파일된 클래스의 상수 풀에서 `java/…`·`javax/…` 의 메서드·필드 참조를 모아 SDK 의 `api-versions.xml`
(lint 가 쓰는 표)과 견준다. 상속은 따라 올라가 찾는다.

    ./gradlew classes                          # 순수 JVM 모듈의 클래스를 만든다
    python tools/check_api_level.py            # 문제가 있으면 끝 코드 1
    python tools/check_api_level.py format/epub  # 모듈 하나만

표준 라이브러리만 쓴다.
"""

from __future__ import annotations

import pathlib
import re
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
MIN_SDK = 31
# 순수 JVM 모듈 전부(settings.gradle.kts 에서 `kotlin.jvm`·`java-library` 를 쓰는 것). 새로 만들면 여기 더한다.
MODULES = [
    "core/model", "core/safety", "core/charset",
    "format/api", "format/archive", "format/text", "format/html", "format/epub",
]
# api-versions.xml 이 공개하지 않는 부모(`AbstractStringBuilder`)에 정의된 멤버 — 표에서 찾지 못하지만 API 1 부터 있다.
KNOWN_HIDDEN_PARENT = {
    ("java/lang/StringBuilder", "setLength(I)V"),
    ("java/lang/StringBuilder", "substring(II)Ljava/lang/String;"),
    ("java/lang/StringBuilder", "lastIndexOf(Ljava/lang/String;I)I"),
    # 자바 9 의 공변 반환(`ByteBuffer flip()` 이 `ByteBuffer` 를 돌려준다) — api-versions.xml 에는 `Buffer` 판만 있지만 API 31 기기에
    # 있다: `core:charset` 의 `WindowedDecoder` 가 7단계부터 글을 열 때마다 이것을 부르고(덱스에 그대로 남는다) 에뮬레이터에서 돈다.
    ("java/nio/ByteBuffer", "flip()Ljava/nio/ByteBuffer;"),
    ("java/nio/ByteBuffer", "clear()Ljava/nio/ByteBuffer;"),
    ("java/nio/CharBuffer", "flip()Ljava/nio/CharBuffer;"),
    ("java/nio/CharBuffer", "clear()Ljava/nio/CharBuffer;"),
}
# invokedynamic 의 부트스트랩(문자열 잇기·람다)은 D8 이 풀어 쓴다 — 안드로이드에서 그대로 부르지 않는다.
IGNORED_PREFIXES = ("java/lang/invoke/",)


def sdk_dir() -> pathlib.Path:
    props = (ROOT / "local.properties").read_text(encoding="utf-8")
    m = re.search(r"^sdk\.dir=(.*)$", props, re.M)
    if not m:
        sys.exit("local.properties 에 sdk.dir 이 없다")
    return pathlib.Path(m.group(1).strip().replace("\\:", ":").replace("\\\\", "\\"))


def api_versions() -> pathlib.Path:
    platforms = sorted((sdk_dir() / "platforms").glob("android-*/data/api-versions.xml"))
    if not platforms:
        sys.exit("api-versions.xml 이 없다(SDK 의 platforms)")
    return platforms[-1]


class Api:
    """api-versions.xml — 클래스마다 처음 들어온 판, 멤버마다 처음 들어온 판, 부모들."""

    def __init__(self, path: pathlib.Path):
        # 판은 '37.0'·'36.1' 꼴도 있다 — 소수로 읽는다.
        self.classes: dict[str, tuple[float, dict[str, float], list[str]]] = {}
        for _, el in ET.iterparse(path, events=("end",)):
            if el.tag != "class":
                continue
            name = el.get("name")
            since = float(el.get("since", "1"))
            members: dict[str, float] = {}
            parents: list[str] = []
            for child in el:
                if child.tag in ("method", "field"):
                    members[child.get("name")] = float(child.get("since", str(since)))
                elif child.tag in ("extends", "implements"):
                    parents.append(child.get("name"))
            self.classes[name] = (since, members, parents)
            el.clear()

    def since(self, cls: str, member: str, seen: set[str] | None = None) -> float | None:
        """멤버가 그 클래스(또는 조상)에 들어온 판. 어디에도 없으면 None."""
        seen = seen or set()
        if cls in seen or cls not in self.classes:
            return None
        seen.add(cls)
        class_since, members, parents = self.classes[cls]
        if member in members:
            return max(class_since, members[member])
        found = [s for p in parents if (s := self.since(p, member, seen)) is not None]
        return max(class_since, min(found)) if found else None


def references(data: bytes) -> list[tuple[str, str, str]]:
    """클래스 파일의 상수 풀에서 (종류, 클래스, 멤버) — 메서드는 이름+서술자, 필드는 이름."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    count = struct.unpack(">H", data[8:10])[0]
    cp: list[tuple | None] = [None] * count
    i, idx = 10, 1
    while idx < count:
        tag = data[i]
        if tag == 1:
            length = struct.unpack(">H", data[i + 1:i + 3])[0]
            cp[idx] = ("utf8", data[i + 3:i + 3 + length].decode("utf-8", "replace"))
            i += 3 + length
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1
        elif tag == 7:
            cp[idx] = ("class", struct.unpack(">H", data[i + 1:i + 3])[0])
            i += 3
        elif tag in (8, 16, 19, 20):
            i += 3
        elif tag in (9, 10, 11):
            cp[idx] = ({9: "field", 10: "method", 11: "method"}[tag],) + struct.unpack(">HH", data[i + 1:i + 5])
            i += 5
        elif tag == 12:
            cp[idx] = ("nat",) + struct.unpack(">HH", data[i + 1:i + 5])
            i += 5
        elif tag == 15:
            i += 4
        elif tag in (17, 18):
            i += 5
        else:
            raise ValueError(f"constant pool tag {tag}")
        idx += 1
    out = []
    for entry in cp:
        if not entry or entry[0] not in ("field", "method"):
            continue
        kind, class_index, nat_index = entry
        cls = cp[cp[class_index][1]][1]
        nat = cp[nat_index]
        name, desc = cp[nat[1]][1], cp[nat[2]][1]
        out.append((kind, cls, name if kind == "field" else name + desc))
    return out


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    api = Api(api_versions())
    problems = []
    checked = 0
    modules = sys.argv[1:] or MODULES
    for module in modules:
        classes = ROOT / module / "build" / "classes" / "kotlin" / "main"
        if not classes.is_dir():
            sys.exit(f"{classes} 이 없다 — 먼저 ./gradlew :{module.replace('/', ':')}:classes")
        for f in classes.rglob("*.class"):
            for kind, cls, member in references(f.read_bytes()):
                if not (cls.startswith("java/") or cls.startswith("javax/")) or cls.startswith(IGNORED_PREFIXES):
                    continue
                if (cls, member) in KNOWN_HIDDEN_PARENT:
                    continue
                checked += 1
                since = api.since(cls, member)
                where = f"{module}:{f.relative_to(classes).as_posix()}"
                if since is None:
                    problems.append(f"안드로이드에 없다: {cls}.{member}  ← {where}")
                elif since > MIN_SDK:
                    problems.append(f"API {since} 부터: {cls}.{member}  ← {where}")
    for p in sorted(set(problems)):
        print(p)
    print(f"자바 API 참조 {checked}개를 minSdk {MIN_SDK} 와 견줬다 — 문제 {len(set(problems))}건")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
