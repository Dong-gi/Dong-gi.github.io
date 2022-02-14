const cluster = require('cluster')
const fs = require('fs')
const numCPUs = require('os').cpus().length

if (cluster.isMaster) {
    console.log(new Date(), `Master process(pid ${process.pid}) start...`)
    try {
        fs.rmSync('restart.sh')
    } catch { }
    fs.writeFileSync('restart.sh', `kill ${process.pid}; nohup node app.js >> app.log 2>&1 &`)

    for (let i = 0; i < numCPUs; ++i)
        cluster.fork()

    cluster.on('exit', (worker, code, signal) => console.log(new Date(), `Worker process(pid ${worker.process.pid}) exit...`))
    return
}

const fastify = require('fastify')({
    bodyLimit: 2048,
    disableRequestLogging: false,
    logger: true
})

fastify.route({
    method: 'GET',
    url: '/health',
    handler: async (request, reply) => {
        reply.send()
    }
})

fastify.route({
    method: 'GET',
    url: '/test',
    handler: async (request, reply) => {
        reply.send(JSON.stringify(request.query))
    }
})

async function start() {
    try {
        fastify.register(require('fastify-cors'), {})
        await fastify.listen(3000, '0.0.0.0')
        console.log(new Date(), `Worker process(pid ${process.pid}) start...`)
    } catch (err) {
        console.log(new Date(), err)
        process.exit(1)
    }
}
start()
