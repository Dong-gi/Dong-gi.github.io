const process = require('process');
const express = require('express');
const path = require('path');
const session = require('express-session'); // Since 1.5 cookie-parser 필요없음
const bodyParser = require('body-parser');


const connectionUrl = `mongodb://localhost:27017`;
const mongo = new require('mongodb').MongoClient(connectionUrl, {useUnifiedTopology: true});
mongo.connect(function(err) {
    assert.equal(err, null);
});


const app = express();
app.set('port', 3000);
app.set('views', path.join(__dirname, 'templates'));
app.set('view engine', 'jade');
app.set('trust proxy', 1);


app.on('error', function (err) {});
process.on('uncaughtException', function (err) {});


/*
const RedisStore = require('connect-redis')(session);
app.use(session({
    store: new RedisStore(...),
    ...
}))
*/
app.use(session({
    secret: 'donggi-test.goorm.io',
    resave: false,
    saveUninitialized: false,
    cookie: { secure: true } // default : { path: ‘/’, httpOnly: true, secure: false, maxAge: null }
}));
app.use(function (req, res, next) {
    req.mongo = mongo;
});
app.use(bodyParser.json());
app.use(bodyParser.urlencoded());


app.all('/api', authorize);

app.all('/api', (req, res) => {
    res.json('No API serviced');
});

app.all('*', (req, res) => {
    let url = req.url.substr(1);
    res.render(url == ''? 'index' : url, { time: new Date() });
});


app.listen(app.get('port'), () => console.log('server started'));


function authorize(req, res, next) {
    if (req.session && req.session.expire && req.session.expire < new Date()) {
        req.session = null;
    }
    if (req.session && req.session.accountId) {
        console.log(req.session);
        return next();
    } else {
        res.set
        res.redirect(401, '/auth');
    }
}