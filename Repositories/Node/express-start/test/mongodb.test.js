const assert = require('assert');
const connectionUrl = `mongodb://localhost:27017`;
const client = new require('mongodb').MongoClient(connectionUrl, {useUnifiedTopology: true});

before('Open mongo connection', function () {
    client.connect(function(err) {
        assert.equal(err, null);
    });
});

beforeEach('Init test collection', async function () {
    const test = client.db('test').collection('test');
    if (await test.countDocuments() > 0)
        await test.drop();
    assert.equal(await test.countDocuments(), 0);
});

describe('Collection', function () {
    it('#insert', async function () {
        const test = client.db('test').collection('test');
        let beforeCount = await test.countDocuments();
        let data = [{}, {}, {}];
        let result = (await test.insertMany(data)).result;
        assert.ok(result.ok);
        
        let afterCount = await test.countDocuments();
        assert.equal(afterCount, beforeCount + data.length);
    });
    
    it('#find', async function () {
        const test = client.db('test').collection('test');
        let data = [];
        for (let i = 0; i < 5; ++i) {
            data.push({num: i});
        }
        data = (await test.insertMany(data)).ops;
        
        let docs = await test.find().toArray();
        assert.equal(docs.length, data.length);
        for (let doc of docs) {
            assert.equal(data.filter(x => x._id.equals(doc._id))[0].num, doc.num);
        }
    });
    
    it('#update', async function () {
        const test = client.db('test').collection('test');
        const beforeName = 'Hello';
        const afterName = 'World';
        
        let data = {name: beforeName};
        data = (await test.insertOne(data)).ops[0];
        
        let result = (await test.updateOne({name: beforeName}, {$set: {name: afterName}})).result;
        assert.ok(result.ok);
        
        result = (await test.findOne({_id: data._id}));
        assert.equal(result.name, afterName);
    });
    
    it('#createIndex, listIndexes, dropAllIndexes', async function () {
        const test = client.db('test').collection('test');
        await test.createIndex('level');
        await test.createIndex({monsterId: 1, attack: -1}); // 1(오름차순), -1(내림차순)
        
        let before = (await test.listIndexes().toArray()).length;
        await test.dropIndexes();
        let after = (await test.listIndexes().toArray()).length;
        
        assert.ok(before > after);
    });
});

after('Close mongo connection', function () {
    client.close();
});