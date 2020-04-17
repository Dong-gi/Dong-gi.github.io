const cluster = require('cluster');
const http = require('http');
const numCPUs = require('os').cpus().length;

if (cluster.isMaster) {
    console.log(`Master process(pid ${process.pid}) start...`);
    
    for (let i = 0; i < numCPUs; ++i) {
        cluster.fork();
    }
    
    cluster.on('exit', (worker, code, signal) => console.log(`Worker process(pid ${worker.process.pid}) exit...`));
} else {
    http.createServer(function (req, res) {
        res.writeHead(200);
        res.end(`Hello World. I'm worker no ${process.pid}`);
    }).listen(3000, () => console.log(`Worker process(pid ${process.pid}) start...`));
}