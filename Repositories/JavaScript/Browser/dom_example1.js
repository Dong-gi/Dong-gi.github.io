let img = document.createElement('img');
img.src = 'https://image.flaticon.com/icons/svg/53/53260.svg';
img.width = 120;

let p = document.createElement('p');
p.id = 'basic2-p1';
p.innerHTML = 'Icons made by <a href="http://www.freepik.com" title="Freepik">Freepik</a> from <a href="http://www.flaticon.com" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a>';
p.style.color = '#0055aa';
p.style.backgroundColor = '#ffaa00';

let div = document.getElementById('basic2-div1');
div.appendChild(img);
div.appendChild(p);

setTimeout(function () {
    document.getElementById('basic2-p1').parentNode.removeChild(document.getElementById('basic2-p1'));
}, 3000);