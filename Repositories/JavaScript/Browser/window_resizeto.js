function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}
async function example() {
    let newWindow = window.open('', '', 'width=480, height=272, resizable=1', true);
    await sleep(1000);
    newWindow.moveTo(100, 200);
    await sleep(1000);
    newWindow.resizeTo(800, 600);
    await sleep(1000);
    console.log(screen.width + ', ' + screen.height);
    newWindow.resizeTo(screen.width, screen.height);
}
example();