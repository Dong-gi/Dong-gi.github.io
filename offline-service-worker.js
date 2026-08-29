/*
 * 오프라인 대비 캐시.
 *
 * 전략은 network-first 다. 온라인이면 늘 새 것을 주고, 받아 온 것을 캐시에 넣어 둔다.
 * 네트워크가 안 되면 캐시에 있는 것을 준다. 낡은 것을 먼저 주는 일은 없다.
 */
const CACHE = 'offline-data-v2';

const PRECACHE = [
    '/',
    '/source/highlight.pack.js',
    '/source/default.min.js',
    '/source/default.css',
    '/source/posts-compressed.json',
];

self.addEventListener('install', (ev) => {
    ev.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PRECACHE)));
});

/*
 * 캐시 이름을 바꾸면 옛 캐시가 남는다. 예전에는 activate 핸들러가 아예 없어서
 * 한 번 들어간 것이 영원히 남았다.
 */
self.addEventListener('activate', (ev) => {
    ev.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
            .then(() => self.clients.claim()),
    );
});

self.addEventListener('fetch', (ev) => {
    if (ev.request.method !== 'GET') return;

    // 예전에는 호스트명 두 개를 박아 두어 로컬 서버에서는 아무 일도 하지 않았다.
    // 같은 출처인지만 보면 어디서 띄우든 똑같이 동작한다.
    const url = new URL(ev.request.url);
    if (url.origin !== self.location.origin) return;

    ev.respondWith((async () => {
        const cache = await caches.open(CACHE);

        let response = null;
        try {
            response = await fetch(ev.request);
        } catch (e) {
            // 네트워크가 안 된다. 아래에서 캐시를 본다.
        }

        if (response != null) {
            /*
             * 200 만 캐시한다. 예전에는 res.ok 가 아니면 null 을 돌려주었는데,
             * respondWith 에 undefined 가 들어가면 브라우저가 그것을 **네트워크 오류**로
             * 다룬다. 그래서 404 가 ERR_FAILED 로 둔갑해 404 페이지가 아예 안 보였다.
             * 응답은 그대로 돌려주고, 캐시에 넣을지만 따로 정한다.
             */
            if (response.status === 200 && response.type === 'basic') {
                // 실패해도(할당량 초과 등) 페이지 응답에는 영향이 없어야 한다.
                cache.put(ev.request, response.clone()).catch(e => console.warn('캐시 저장 실패', url.href, e));
            }
            return response;
        }

        const cached = await cache.match(ev.request);
        if (cached != null) return cached;

        // 캐시에도 없다. 그래도 Response 를 돌려줘야 한다.
        return new Response('오프라인이고 캐시에도 없는 문서다.', {
            status: 503,
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        });
    })());
});
