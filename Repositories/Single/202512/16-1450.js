console.log(['banana ice cream', 'banana chip', 'chocolate ice cream', 'chocolate chip'].map(x => /\w+(?= ice cream)/.test(x)))     // [ true, false, true, false ]
console.log(['banana ice cream', 'banana chip', 'chocolate ice cream', 'chocolate chip'].map(x => /banana(?! ice cream)/.test(x)))  // [ false, true, false, false ]
console.log(['banana ice cream', 'banana chip', 'chocolate ice cream', 'chocolate chip'].map(x => /(?<=banana )\w+/.test(x)))       // [ true, true, false, false ]
console.log(['banana ice cream', 'banana chip', 'chocolate ice cream', 'chocolate chip'].map(x => /(?<!banana )ice cream/.test(x))) // [ false, false, true, false ]
