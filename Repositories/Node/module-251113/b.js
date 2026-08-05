import { a } from "./a.js";

console.log("b.js body start");
a();

export function b() {
    console.log("b() called");
}
