const express = require('express');
const path = require('path');
const cookieParser = require('cookie-parser');
const session = require('express-session');


const app = express();
app.set('port', 3000);
app.set('views', path.join(__dirname, 'templates'));
app.set('view engine', 'jade');
app.set('trust proxy', 1);


app.use(cookieParser('donggi-test.goorm.io'));
app.use(session({
    secret: 'donggi-test.goorm.io',
    resave: false,
    saveUninitialized: false,
    cookie: { secure: true }
}));


app.all('/api', authorize);

app.all('/api', (req, res) => {
    res.json('No API serviced');
});

app.all('*', (req, res) => {
    let url = req.url.substr(1);
    console.log(url);
    res.render(url == ''? 'index' : url, { time: new Date() });
});


app.listen(app.get('port'), () => console.log('server started'));


function authorize(req, res, next) {
    if (req.session) {
        console.log(req.session);
        return next();
    } else {
        res.set
        res.redirect(401, '/auth');
    }
}