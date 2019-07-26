{
    let f;
    {
        let obj = {name: "Donggi Kim"};
        f = function () { return obj; }
    }
    let obj = f();
    console.log(obj);
}

{
    let f = (() => {
        let callCount = 0;
        return () => { return callCount++; }
    })();
    console.log(f());
    console.log(f());
}