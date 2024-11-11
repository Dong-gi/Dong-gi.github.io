self.addEventListener('install', (ev) => {
    ev.waitUntil(
        caches.open('offline-data').then(function (cache) {
            return cache.addAll([
                '/source/highlight.pack.js',
                '/source/default.min.js',
                '/files/posts-compressed.json',
            ])
        })
    )
})

self.addEventListener('fetch', ev => {
    if (ev.request.method !== 'GET') {
        return
    }

    ev.respondWith(
        (async () => {
            const cache = await caches.open('offline-data')
            const url = new URL(ev.request.url)

            return await fetch(url).then(res => {
                if (res.ok) {
                    console.log('Cache update', url.href)
                    cache.put(url, res.clone())
                    return res
                }
                return null
            }).catch(e => {
                console.error('Fetch failed', url.href, e)
                return null
            }).then(async res => {
                if (res != null) {
                    return res
                }
                const cachedRes = await cache.match(url)
                if (cachedRes != null) {
                    console.log('Cache hit', url.href)
                } else {
                    console.log('Cache miss', url.href)
                }
                return cachedRes
            })
        })()
    )
})
