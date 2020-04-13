const express = require('express');
const path = require('path');

const app = express();
app.set('port', 3000);
app.set('views', path.join(__dirname, 'templates'));
app.set('view engine', 'jade');

app.use(express.static(path.join(__dirname, 'templates')));

app.all('/', (req, res) => res.render('index', { time: new Date() }));
app.listen(app.get('port'));