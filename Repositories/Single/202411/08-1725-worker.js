addEventListener('message', async function (ev) {
    const logs = [`${new Date()} Message received : ${JSON.stringify(ev.data)}`]
    postMessage(logs)

    for (let i = 0; i < 3; ++i) {
        await new Promise(resolve => setTimeout(resolve, 1000))
        logs.push(`${new Date()} processing... ${i}`)
        postMessage(logs)
    }

    logs.push(`${new Date()} DONE!`)
    postMessage(logs)
})