Promise.allSettled([
    (async () => 'OK 1')(),
    (async () => { throw new Error('NG 1') })(),
    (async () => 'OK 2')(),
    (async () => { throw new Error('NG 2') })(),
]).then(resultArr => {
    for (const result of resultArr) {
        console.log(result.status, result.value, result.reason?.message)
    }
})

// fulfilled OK 1 undefined
// rejected undefined NG 1
// fulfilled OK 2 undefined
// rejected undefined NG 2
