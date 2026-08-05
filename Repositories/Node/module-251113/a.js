import { b } from "./b.js";

console.log("a.js body start");
b();

export function a() {
    console.log("a() called");
}
