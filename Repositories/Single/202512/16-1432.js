console.log([...'<> <a> <div>'.matchAll(/<.*>/g)].map(x => x[0]))   // [ '<> <a> <div>' ]
console.log([...'<> <a> <div>'.matchAll(/<.+>/g)].map(x => x[0]))   // [ '<> <a> <div>' ]
console.log([...'<> <a> <div>'.matchAll(/<.*?>/g)].map(x => x[0]))  // [ '<>', '<a>', '<div>' ]
console.log([...'<> <a> <div>'.matchAll(/<.+?>/g)].map(x => x[0]))  // [ '<> <a>', '<div>' ]
