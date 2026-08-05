import { createWriteStream } from 'node:fs'
import * as youtube from 'youtubei.js'

// youtube.Log.setLevel(youtube.Log.Level.DEBUG)

const innerTube = await youtube.Innertube.create({
    lang: 'ko',
    location: 'KR',
    timezone: 'Asia/Seoul',
    enable_session_cache: true,
    cache: new youtube.UniversalCache(true, './cache')
})

innerTube.session.on('auth-pending', (data) => {
    console.log(`Go to ${data.verification_url} in your browser and enter code ${data.user_code} to authenticate.`)
})

innerTube.session.on('update-credentials', async ({ credentials }) => {
    await innerTube.session.oauth.cacheCredentials()
})

await innerTube.session.signIn()
await innerTube.session.oauth.cacheCredentials()

const searchRes = await innerTube.search('広告なし ASMR', { type: 'video' })
const videoArr = searchRes.results
    .filter(x => x.type === youtube.YTNodes.Video.type)
    .map(x => x.as(youtube.YTNodes.Video))

videoArr.forEach((video, idx) => {
    console.log(`#${idx + 1} : ${video.title.text.substring(0, 30)}... by ${video.author.name} at ${video.published.text}`)
})

const video = videoArr.sort((a, b) => a.duration.seconds - b.duration.seconds)[0]
const videoInfo = await innerTube.getBasicInfo(video.id)
const format = youtube.FormatUtils.chooseFormat({
    quality: 'bestefficiency',
    type: 'video+audio',
    format: 'any'
}, videoInfo.streaming_data)
const ext = /video\/(\w+);/.exec(format.mime_type)[1]
// node_modules/youtubei.js/dist/src/utils/FormatUtils.js
const readStream = await innerTube.download(video.id, {
    quality: format.quality_label,
    type: 'video+audio',
    format: 'any'
})
const writeStream = createWriteStream(`${video.id}.${ext}`, { autoClose: true })
let byteSize = 0
for await (const chunk of youtube.Utils.streamToIterable(readStream)) {
    byteSize += chunk.byteLength
    console.log(`Downloading... ${byteSize} bytes done`)
    writeStream.write(chunk)
}
console.log(`Downloaded`)