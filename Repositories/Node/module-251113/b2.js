import { a2 } from "./a2.js";

console.log("b2.js body start");
a2();

export let count = 0
export function b2() {
    console.log(`b2() called#${count++}`);
}
