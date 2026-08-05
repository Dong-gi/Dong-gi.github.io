function sleep(ms) {
    return new Promise(_ => setTimeout(_, ms));
}

function hello1() {
    console.log(1);
    sleep(1000);
    console.log(2);
}

async function hello2() {
    console.log(10);
    await sleep(1000);
    console.log(20);
}

hello1();
hello2();