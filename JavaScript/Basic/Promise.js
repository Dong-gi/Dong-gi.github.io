let counter = ((max) => {
    return new Promise(
        (resolve, reject) => {
            let i = 1;

            let work = () => {
                if (i > max) {
                    resolve();
                    return true;
                }
                if (i === 4) {
                    reject(new Error("4는 안 됩니다"));
                    return true;
                }
                console.log(i++);
                return false;
            };
            setInterval(work, 1000);
        }
    )
});

let c1 = counter(5);
c1.then(
    () => { console.log("카운터 종료"); },
    (err) => { console.log(err); }
);

let c2 = counter(3);
c2.then(
    () => { console.log("카운터 종료"); },
    (err) => { console.log(err); }
);