// Window.open(url?: string, target?: string, features?: string, replace?: boolean): Window
let newWindow = window.open("about:Blank", 'newWindow', 'width=480, height=272, resizable=1', true);
if (newWindow) {
    let countDown = (newWindow, count) => {
        let tick = () => {
            if (count-- <= 0) {
                return;
            }
            let node = newWindow.document.createElement('h1');
            node.appendChild(newWindow.document.createTextNode("Wait a second..."));
            newWindow.document.getElementsByTagName('body')[0].appendChild(node);
            console.log(count);
            setTimeout(tick, 1000);
        };
        setTimeout(tick, 1000);
    };
    countDown(newWindow, 3);
} else {
    window.alert('팝업 차단으로 창 생성 불가');
}