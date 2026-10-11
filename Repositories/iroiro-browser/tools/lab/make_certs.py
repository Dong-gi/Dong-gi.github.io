"""실험실의 인증서와 시험 계정을 만든다 — 한 번만 돌리면 된다(다시 돌리면 새로 만든다).

만드는 것(전부 git 이 무시한다):

- ``local/lab/ca.pem``·``ca.key``      실험용 인증 기관. 30 일짜리
- ``local/lab/server.pem``·``server.key``  ``*.lab.test``·``lab.test``·``10.0.2.2``·``127.0.0.1`` 의 서버 인증서
- ``local/lab/credentials.json``        시험 프록시의 계정(무작위). 사용자의 실제 프록시 계정이 아니다
- ``app/src/debug/res/raw/lab_ca.pem``  디버그 빌드가 믿을 인증 기관
- ``app/src/debug/res/xml/nsc_lab.xml``  그 인증 기관을 **``lab.test`` 아래 이름에만** 믿는 네트워크 보안 설정

디버그 빌드는 ``nsc_lab.xml`` 이 있으면 그것을 건다(``app/build.gradle.kts``). 릴리스는 시스템 인증서만 믿는다.
에뮬레이터의 시스템 설정(인증서 설치)은 건드리지 않는다.

필요한 것: ``openssl`` (Git for Windows 에 들어 있다).
"""

from __future__ import annotations

import json
import pathlib
import secrets
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
LAB = ROOT / "local" / "lab"
RAW = ROOT / "app" / "src" / "debug" / "res" / "raw"
XML = ROOT / "app" / "src" / "debug" / "res" / "xml"

DAYS = 30

# openssl req 가 시스템 설정 파일을 찾지 않게 설정을 직접 준다(Git 의 openssl 은 그 경로가 어긋날 때가 있다).
OPENSSL_CNF = """[req]
distinguished_name = dn
prompt = no

[dn]
CN = unused

[ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash

[server]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature
extendedKeyUsage = serverAuth
subjectAltName = DNS:*.lab.test,DNS:lab.test,DNS:labhost,IP:10.0.2.2,IP:127.0.0.1
authorityKeyIdentifier = keyid
subjectKeyIdentifier = hash
"""

NSC_LAB = """<?xml version="1.0" encoding="utf-8"?>
<!--
  tools/lab/make_certs.py 가 만든다(git 이 무시한다). 실험용 인증 기관은 lab.test 아래 이름에만 믿는다 —
  다른 이름은 시스템 인증서만 믿는다. 실험의 목적지(8080)와 평문 프록시(18080)가 평문이라 평문을 허락한다.
-->
<network-security-config xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources">
    <base-config cleartextTrafficPermitted="true" tools:ignore="InsecureBaseConfiguration">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <domain-config>
        <domain includeSubdomains="true">lab.test</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/lab_ca" />
        </trust-anchors>
    </domain-config>
</network-security-config>
"""


def openssl(*args: str) -> None:
    exe = shutil.which("openssl")
    if exe is None:
        sys.exit("openssl 을 찾지 못했다 — Git for Windows 의 셸에서 돌리거나 PATH 에 넣는다.")
    done = subprocess.run([exe, *args], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if done.returncode != 0:
        sys.exit(f"openssl {args[0]} 실패:\n{done.stderr}")


def main() -> None:
    LAB.mkdir(parents=True, exist_ok=True)
    ca_key, ca_pem = LAB / "ca.key", LAB / "ca.pem"
    srv_key, srv_pem = LAB / "server.key", LAB / "server.pem"
    with tempfile.TemporaryDirectory() as tmp:
        cnf = pathlib.Path(tmp) / "lab.cnf"
        cnf.write_text(OPENSSL_CNF, encoding="ascii")
        csr = pathlib.Path(tmp) / "server.csr"
        openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(ca_key))
        openssl(
            "req", "-x509", "-new", "-key", str(ca_key), "-sha256", "-days", str(DAYS), "-subj", "/CN=iroiro lab CA",
            "-config", str(cnf), "-extensions", "ca", "-out", str(ca_pem),
        )
        openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(srv_key))
        openssl("req", "-new", "-key", str(srv_key), "-subj", "/CN=lab.test", "-config", str(cnf), "-out", str(csr))
        openssl(
            "x509", "-req", "-in", str(csr), "-CA", str(ca_pem), "-CAkey", str(ca_key), "-CAcreateserial",
            "-sha256", "-days", str(DAYS), "-extfile", str(cnf), "-extensions", "server", "-out", str(srv_pem),
        )

    credentials = {"user": "lab-" + secrets.token_hex(4), "password": secrets.token_urlsafe(18)}
    (LAB / "credentials.json").write_text(json.dumps(credentials, indent=2) + "\n", encoding="utf-8")

    RAW.mkdir(parents=True, exist_ok=True)
    XML.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ca_pem, RAW / "lab_ca.pem")
    (XML / "nsc_lab.xml").write_text(NSC_LAB, encoding="utf-8", newline="\n")
    print(f"인증서: {LAB}")
    print(f"디버그 빌드용: {RAW / 'lab_ca.pem'}, {XML / 'nsc_lab.xml'}")
    print("시험 계정: local/lab/credentials.json (사용자의 실제 프록시 계정이 아니다)")
    print("다음: ./gradlew :app:assembleDebug  (nsc_lab 이 걸린 디버그 APK)")


if __name__ == "__main__":
    main()
