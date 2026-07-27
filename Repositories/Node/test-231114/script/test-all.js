import fs from 'node:fs/promises'
import { $, cd } from 'zx'

$`npm run test`.then(x => console.log(x.stdout)).catch(x => console.log(x.stdout))

const packageFile = (await fs.glob('hi.donggi-test-*.tgz').next()).value
if (packageFile == null) {
    throw new Error('No package found')
}

cd('./test-cjs')
await $`npm install file:../${packageFile}`
$`npm run test`.then(x => console.log(x.stdout)).catch(x => console.log(x.stdout))

cd('../test-mjs')
await $`npm install file:../${packageFile}`
$`npm run test`.then(x => console.log(x.stdout)).catch(x => console.log(x.stdout))
