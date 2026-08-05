type ExtractParams<T extends string> =
    T extends `${string}:${infer Param}/${infer Rest}`
    ? (Param | ExtractParams<Rest>)
    : (T extends `${string}:${infer Param}` ? Param : never)


// 경로를 인자로 받아서 파라미터를 자동 완성으로 제공하는 함수
function get<Path extends string>(path: Path, callback: (req: { path: Record<ExtractParams<Path>, any> }) => void) {
    // ...
}

get('/api/:userId/:postId', (req) => {
    req.path
})