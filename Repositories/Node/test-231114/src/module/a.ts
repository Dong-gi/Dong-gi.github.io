export class A {
    /**
     * @returns some string
     */
    public do(): string {
        return `Hello World@${new Date().toUTCString()}`;
    }
}
