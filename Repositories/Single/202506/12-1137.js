/**
 * @template {string} T
 * @typedef {T extends `${string}:${infer Param}/${infer Rest}`
 *  ? (Param | ExtractParams<Rest>)
 *  : (T extends `${string}:${infer Param}` ? Param : never)} ExtractParams
 */

/**
 * @template {string} T
 * @param {T} path
 * @param {(req: { path: Record<ExtractParams<T>, any> }) => void} callback
 */
function get(path, callback) {
    // ...
}

get('/api/:userId/:postId', (req) => {
    req.path
})