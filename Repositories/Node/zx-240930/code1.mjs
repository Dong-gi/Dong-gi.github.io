import { $ } from 'zx'

const date = await $`date`
console.log(date)
console.log(await $`echo Current date is ${date}`)
