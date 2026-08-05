let counter = ((max) => {
    return new Promise(
        (resolve, reject) => {
            let i = 1;
            let intervalId;

            let work = () => {
                if (i > max) {
                    clearInterval(intervalId);
                    resolve();
                    return true;
                }
                if (i === 4) {
                    clearInterval(intervalId);
                    reject(new Error("4는 안 됩니다"));
                    return true;
                }
                console.log(i++);
                return false;
            };
            intervalId = setInterval(work, 1000);
        }
    )
});

let c1 = counter(5);
c1.then(
    () => { console.log("카운터 종료"); },
    (err) => { console.log(err); }
).then(
    () => console.log("이처럼 다른 작업을 이어서 할 수 있다.")
);

let c2 = counter(3);
c2.then(
    () => { console.log("카운터 종료"); },
    (err) => { console.log(err); }
);