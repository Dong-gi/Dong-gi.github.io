let div = document.getElementById('basic2-div1');
let img = document.createElement('img');
div.appendChild(img);
img.src = 'https://image.flaticon.com/icons/svg/53/53260.svg';
img.width = 120;
img.style.position = 'absolute';
img.style.paddingLeft = '50px';
img.style.paddingTop = '50px';

let img2 = img.cloneNode();
div.appendChild(img2);

setInterval(((img, img2, theta, radius, fps) => {
    return () => {
        // 'px'이 포함되어 있으므로 parse 필요
        img2.style.paddingLeft = parseInt(img.style.paddingLeft) + radius * Math.cos(theta) + 'px';
        img2.style.paddingTop = parseInt(img.style.paddingTop) + radius * Math.sin(theta) + 'px';
        theta = (theta + Math.PI / fps);
    };
})(img, img2, 0, 60, 30), 1000 / 30);