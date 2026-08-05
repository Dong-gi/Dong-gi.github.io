describe('후킹 테스트', function() {
    before('후킹 테스트#before', function() {
        console.log('before');
    });

    after('후킹 테스트#after', function() {
        throw new Error('ㅎㅎ');
    });

    beforeEach('후킹 테스트#beforeEach', function() {
        console.log('beforeEach');
    });

    afterEach('후킹 테스트#afterEach', function() {
        console.log('afterEach');
    });

    it('#test1', function () {});
    it('#test2', function () {});
});

/*
  후킹 테스트
before
beforeEach
    ✓ #test1
afterEach
beforeEach
    ✓ #test2
afterEach
    2) "after all" hook: 후킹 테스트#after for "#test2"
*/