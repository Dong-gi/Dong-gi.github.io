package io.github.donggi.iroirobrowser.lab

/** 실험이 WebView 에 넣는 스크립트. */
object LabScripts {

    /**
     * WebRTC 를 지운다(1판) — 쪽의 스크립트보다 먼저(문서 시작 스크립트). 크롬 확장이라면 `webRTCIPHandlingPolicy = disable_non_proxied_udp` 로
     * 막는데 WebView 에는 그 설정이 없다. WebRTC 의 STUN 은 UDP 라 프록시를 타지 않고 폰의 실제 IP 를 드러낸다.
     *
     * 실측(WebView 153): 주 문서·srcdoc·샌드박스·data: 틀에는 들어가지만, **스크립트가 만든 빈 틀(about:blank)에는 들어가지 않는다**
     * — `iframe.contentWindow.RTCPeerConnection` 으로 비켜 간다.
     */
    const val WEBRTC_GUARD = """
(function () {
  var names = ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'RTCSessionDescription',
    'RTCIceCandidate', 'RTCRtpSender', 'RTCRtpReceiver', 'RTCRtpTransceiver', 'RTCDtlsTransport', 'RTCIceTransport',
    'RTCSctpTransport', 'RTCCertificate', 'RTCPeerConnectionIceEvent', 'RTCDataChannelEvent', 'RTCTrackEvent'];
  for (var i = 0; i < names.length; i++) {
    try { delete window[names[i]]; } catch (e) {}
    try { Object.defineProperty(window, names[i], { value: undefined, writable: false, configurable: false }); } catch (e) {}
  }
})();
"""

    /** 2판은 앱의 것을 그대로 쓴다([io.github.donggi.iroirobrowser.browser.PageScripts.WEBRTC_GUARD]). */
    val WEBRTC_GUARD_V2: String get() = io.github.donggi.iroirobrowser.browser.PageScripts.WEBRTC_GUARD

    /**
     * 스크립트가 넣는 `<link rel=dns-prefetch>` 를 무디게 한다 — `rel` setter 와 `setAttribute('rel', …)` 에서 그 낱말을 뺀다.
     * HTML 원문의 것·`Link` 머리의 것·`innerHTML` 로 넣은 것은 스크립트가 손댈 틈이 없다(실험이 그 한계를 잰다).
     */
    const val DNS_GUARD = """
(function () {
  var RX = /(^|\s)dns-prefetch(?=\s|$)/ig;
  function clean(v) { return String(v).replace(RX, ' ').trim(); }
  var d = Object.getOwnPropertyDescriptor(HTMLLinkElement.prototype, 'rel');
  if (d && d.set) {
    Object.defineProperty(HTMLLinkElement.prototype, 'rel', { get: d.get, set: function (v) { d.set.call(this, clean(v)); },
      enumerable: d.enumerable, configurable: d.configurable });
  }
  var setAttribute = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function (name, value) {
    if (this instanceof HTMLLinkElement && String(name).toLowerCase() === 'rel') value = clean(value);
    return setAttribute.call(this, name, value);
  };
})();
"""
}
