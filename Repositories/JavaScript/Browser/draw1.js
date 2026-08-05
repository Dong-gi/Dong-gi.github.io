(() => {
	let canvas = document.getElementById('tutorial-canvas-1');
	if (!(canvas instanceof HTMLCanvasElement))
		return;
	let parent = canvas.parentElement;
	canvas.width = parent.clientWidth - parseInt(window.getComputedStyle(parent).paddingLeft);
	canvas.height = window.innerHeight / 3;
	let c = canvas.getContext('2d');
	c.clearRect(0, 0, canvas.width, canvas.height);
	c.fillText('Hello 캔버스', 0, 75);
})();