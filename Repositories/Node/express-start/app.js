const express = require('express');
const path = require('path');

const app = express();
app.set('port', 3000);
app.set('views', path.join(__dirname, 'templates'));
app.set('view engine', 'jade');

app.all(/.*/, (req, res) => {
    let url = req.url.substr(1);
    res.render(url == ''? 'index' : url, { time: new Date() });
});
app.listen(app.get('port'));