package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * docx·xlsx·pptx 의 바탕 — OPC 꾸러미([OpcPackage]) 위의 [FlowDocumentBase]. 지키는 것(잠금·위생·자원·
 * 부분 실패·캐시·세기)은 전부 그쪽에 있다. 변환기가 `pkg` 를 [OpcPackage] 로 쓸 수 있게 하는 자리다
 * (관계·파서 같은 OPC 의 기능이 필요하다).
 */
abstract class OpcFlowDocument(
    pkg: OpcPackage,
    limits: ParseLimits,
    formatId: FormatId,
) : FlowDocumentBase<OpcPackage>(pkg, limits, formatId)
