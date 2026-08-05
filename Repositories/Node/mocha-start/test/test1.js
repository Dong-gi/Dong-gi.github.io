const assert = require('assert');
describe('Array', function() {
    describe('#indexOf()', function() {
        it('Should -1', function() {
            assert.equal([1, 2, 3].indexOf(4), -1);
        });
    });
});

/*
root@goorm:/workspace/donggi-github/Dong-gi.github.io/Repositories/Node/mocha-start(master)# npm test

> mocha-start@1.0.0 test /workspace/donggi-github/Dong-gi.github.io/Repositories/Node/mocha-start
> mocha

  Array
    #indexOf()
      ✓ Should -1

  1 passing (7ms)
*/