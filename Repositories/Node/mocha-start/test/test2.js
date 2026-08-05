class My {
    async do1(callback) {
        await new Promise(_ => setTimeout(_, 1000));
        callback();
    }
    async do2(callback) {
        await this.do1(callback);
        callback();
    }
}

describe('My', function() {
    describe('#do1()', function() {
        it('Should OK', function(done) {
            new My().do1(done);
        });
    });
    describe('#do2()', function() {
        it('Should Error', function(done) {
            new My().do2(done);
        });
    });
});

/*
  My
    #do1()
      ✓ Should OK (1001ms)
    #do2()
      ✓ Should Error (1001ms)
      1) Should Error

  2 passing (2s)
  1 failing

  1) My
       #do2()
         Should Error:
     Error: done() called multiple times
      at My.do2 (test/test2.js:8:9)
*/