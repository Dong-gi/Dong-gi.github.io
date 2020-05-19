const redis = require('redis')
const client = redis.createClient({port: 27017})

// redis.print (err, reply)
client.set('hello', 'world', redis.print) // Reply: OK
client.get('hello', redis.print)          // Reply: world

client.quit()