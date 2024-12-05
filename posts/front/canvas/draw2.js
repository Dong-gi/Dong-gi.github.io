(() => {
	let canvas = document.getElementById('tutorial-canvas');
	if (!(canvas instanceof HTMLCanvasElement))
		return;
	let parent = canvas.parentElement;
	canvas.width = parent.clientWidth - parseInt(window.getComputedStyle(parent).paddingLeft);
	canvas.height = window.innerHeight / 3;
	let c = canvas.getContext('2d');
	c.clearRect(0, 0, canvas.width, canvas.height);
	let image = new Image();
	image.src = "/imgs/no-date/kanna.gif";
	image.onload = () => c.drawImage(image, 0, 0);
})();