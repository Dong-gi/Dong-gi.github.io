<?php
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/create_code_page.php';

putHeader();
putTitle("배열");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">var</span> arr <span style="color:#fbde2d">=</span> [];
arr <span style="color:#fbde2d">=</span> [<span style="color:#d8fa3c">1</span>, <span style="color:#d8fa3c">2</span>, <span style="color:#61ce3c">'asdf'</span>];
arr.<span style="color:#8da6ce">push</span>(<span style="color:#61ce3c">'fdsa'</span>);
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(arr);
<span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">var</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> arr.<span style="color:#8da6ce">length</span>; <span style="color:#fbde2d">++</span>i)
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(arr[i]);
</pre>
<button onclick="func1()">결과 확인</button>

<script>
function func1() {
    var arr = [];
    arr = [1, 2, 'asdf'];
    arr.push('fdsa');
    console.log(arr);
    for(var i = 0; i < arr.length; ++i)
        console.log(arr[i]);
}
</script>

<?php
putTitle("객체");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">var</span> obj <span style="color:#fbde2d">=</span> {a : <span style="color:#d8fa3c">1</span>, b : <span style="color:#61ce3c">'asdf'</span>, c : <span style="color:#d8fa3c">true</span>, <span style="color:#ff6400">d</span> : <span style="color:#fbde2d">function</span>(){}};
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(obj);
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(obj.a);
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(obj[<span style="color:#61ce3c">'a'</span>]);
<span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">var</span> v <span style="color:#fbde2d">in</span> obj)
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(v <span style="color:#fbde2d">+</span> <span style="color:#61ce3c">' : '</span> <span style="color:#fbde2d">+</span> obj[v]);
</pre>
<button onclick="func2()">결과 확인</button>

<script>
function func2() {
    var obj = {a : 1, b : 'asdf', c : true, d : function(){}};
    console.log(obj);
    console.log(obj.a);
    console.log(obj['a']);
    for(var v in obj)
        console.log(v + ' : ' + obj[v]);

    // in 키워드를 for와 별개로 이용하여 key의 존재유무 확인
    console.log('a' in obj);
    console.log('aa' in obj);

    // with 키워드를 이용하여 쉬운 참조 이용
    with (obj) {
    	console.log(a);
    	console.log(c);
	    d = 'new data d';
	    console.log(d);
	    delete(c);
	    console.log('c' in obj);
    }
    obj.e = 'new data e'
    console.log(obj.d + '\n' + obj.e);
    delete(obj.e);
    console.log('e' in obj);
}
</script>

<?php
putTitle("생성자, 프로토타입, 상속");
?>

<button onclick="
var p = new Person('wiz', 1234);
console.log(p.toString());
console.log(Person.prototype);
var s = new Student('rowiz', 4321, 2018424);
console.log(s.newFunction());
console.log(s.getID() + ', ' + s.getName());

var s2 = new Student2('wiz', 8888, 2017351);
console.log(s2.toString());
console.log(s2.getID() + ', ' + s2.getName());

// 상속 3
var s3 = Object.create(s2);
s3.name = 'rowiz';
console.log(s3.toString());
// ECMAScript5 : Object.defineProperty(), Object.defineProperties()를 이용해 객체 속성 수정 가능
Object.defineProperty(s3, 'name', {value: 'wiz', enumerable: false});
console.log(s3.toString());
// Object.preventExtensions()를 이용해 객체 속성 추가 제한
// Object.seal()을 이용해 객체 속성 삭제 제한
// Object.freeze()를 이용해 객체 속성 수정 제한
console.log(Object.keys(s2)); // for in 가능한 자기 소유의 속성 키 배열
console.log(Object.keys(s3));
console.log(Object.getOwnPropertyNames(s3)); // 자기 소유의 속성 키 배열
console.log(Object.getOwnPropertyDescriptor(s3, 'name'));
// 객체에 없는 속성을 참조하려 할 경우 객체의 모든 프로토타입 체인을 검사하게 된다.
// 따라서 이를 방지하기 위해 hasOwnProperty()를 이용할 필요가 있다.
console.log(s3.hasOwnProperty('name'));
console.log(p instanceof Person);
console.log(p instanceof Student);
console.log(s instanceof Person);
console.log(s instanceof Student);
console.log(s instanceof Student2);
console.log(s3 instanceof Person);
console.log(s3 instanceof Student);
console.log(s3 instanceof Student2);

// class, extends 키워드를 이용한 방법도 있다.">결과 확인</button>
<script>
	// new 키워드를 사용하지 않으면 해당 지점의 this가 가리키는 곳에 속성이 추가된다.
	function Person(name, height) {
		this.name = name;
		this.height = height;
	}
	// 모든 함수는 prototype 객체를 갖는다.
	// 이를 통해 같은 클래스의 객체들이 메서드를 공유할 수 있다.
	Person.prototype = {
		getName: function () { return this.name; },
		getHeight: function () { return this.height; }
	};
	Person.prototype.toString = function () {
		return '이름 : ' + this.name + ', 키 : ' + this.height;
	};
	// 상속 1
	function Student(name, height, id) {
		this.super = Person;
		this.super(name, height); // Person의 속성을 가져온다.
		this.id = id;
	}
	// ECMAScript5 : Object.create()를 이용하여 다중상속을 할 수 있다.
	Student.prototype = Object.create(Person.prototype, {
		newField: {
			value: '☆'
			/* configurable : 설정 변경 가능? false일 경우 추후 수정 불가.
			 * enumerable : for in 가능?
			 * value : 값
			 * writable : 변경 가능?
			 * get : getter, set : setter
			 */
		},
		newFunction: {
			value: function () {
				return this.newField + '이름 : ' + this.name + ', 키 : ' + this.height +
					', ID : ' + this.id;
			}
		}
	});
	Student.prototype.getID = function () { return this.id };
	Student.prototype.constructor = Student;
	// 상속 2
	function Student2(name, height, id) {
		Student.call(this, name, height, id);
	}
	Student2.prototype = Object.create(Student.prototype, {
		toString: {
			value: function () {
				return '이름 : ' + this.name + ', 키 : ' + this.height +
					', ID : ' + this.id;
			}
		}
	});
	Student2.prototype.constructor = Student2;
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">&lt;</span>button onclick<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"
var p = new Person('wiz', 1234);
console.log(p.toString());
console.log(Person.prototype);
var s = new Student('rowiz', 4321, 2018424);
console.log(s.newFunction());
console.log(s.getID() + ', ' + s.getName());

var s2 = new Student2('wiz', 8888, 2017351);
console.log(s2.toString());
console.log(s2.getID() + ', ' + s2.getName());

// 상속 3
var s3 = Object.create(s2);
s3.name = 'rowiz';
console.log(s3.toString());
// ECMAScript5 : Object.defineProperty(), Object.defineProperties()를 이용해 객체 속성 수정 가능
Object.defineProperty(s3, 'name', {value: 'wiz', enumerable: false});
console.log(s3.toString());
// Object.preventExtensions()를 이용해 객체 속성 추가 제한
// Object.seal()을 이용해 객체 속성 삭제 제한
// Object.freeze()를 이용해 객체 속성 수정 제한
console.log(Object.keys(s2)); // for in 가능한 자기 소유의 속성 키 배열
console.log(Object.keys(s3));
console.log(Object.getOwnPropertyNames(s3)); // 자기 소유의 속성 키 배열
console.log(Object.getOwnPropertyDescriptor(s3, 'name'));
// 객체에 없는 속성을 참조하려 할 경우 객체의 모든 프로토타입 체인을 검사하게 된다.
// 따라서 이를 방지하기 위해 hasOwnProperty()를 이용할 필요가 있다.
console.log(s3.hasOwnProperty('name'));
console.log(p instanceof Person);
console.log(p instanceof Student);
console.log(s instanceof Person);
console.log(s instanceof Student);
console.log(s instanceof Student2);
console.log(s3 instanceof Person);
console.log(s3 instanceof Student);
console.log(s3 instanceof Student2);

// class, extends 키워드를 이용한 방법도 있다."</span><span style="color:#fbde2d">></span>결과 확인<span style="color:#fbde2d">&lt;</span>/button<span style="color:#fbde2d">></span>
<span style="color:#fbde2d">&lt;</span>script<span style="color:#fbde2d">></span>
    <span style="color:#aeaeae">// new 키워드를 사용하지 않으면 해당 지점의 this가 가리키는 곳에 속성이 추가된다.</span>
    <span style="color:#fbde2d">function</span> <span style="color:#ff6400">Person</span>(name, height) {
        this.<span style="color:#8da6ce">name</span> <span style="color:#fbde2d">=</span> name;
        this.<span style="color:#8da6ce">height</span> <span style="color:#fbde2d">=</span> height;
    }
    <span style="color:#aeaeae">// 모든 함수는 prototype 객체를 갖는다.</span>
    <span style="color:#aeaeae">// 이를 통해 같은 클래스의 객체들이 메서드를 공유할 수 있다.</span>
    <span style="color:#8da6ce">Person</span>.<span style="color:#8da6ce">prototype</span> <span style="color:#fbde2d">=</span> {
        <span style="color:#ff6400">getName</span>: <span style="color:#fbde2d">function</span> () { <span style="color:#fbde2d">return</span> this.<span style="color:#8da6ce">name</span>; },
        <span style="color:#ff6400">getHeight</span>: <span style="color:#fbde2d">function</span> () { <span style="color:#fbde2d">return</span> this.<span style="color:#8da6ce">height</span>; }
    };
    <span style="color:#8da6ce">Person</span>.<span style="color:#8da6ce">prototype</span>.<span style="color:#ff6400">toString</span> <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">function</span> () {
        <span style="color:#fbde2d">return</span> <span style="color:#61ce3c">'이름 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">name</span> <span style="color:#fbde2d">+</span> <span style="color:#61ce3c">', 키 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">height</span>;
    };
    <span style="color:#aeaeae">// 상속 1</span>
    <span style="color:#fbde2d">function</span> <span style="color:#ff6400">Student</span>(name, height, id) {
        this.super <span style="color:#fbde2d">=</span> Person;
        this.super(name, height); <span style="color:#aeaeae">// Person의 속성을 가져온다.</span>
        this.<span style="color:#8da6ce">id</span> <span style="color:#fbde2d">=</span> id;
    }
    <span style="color:#aeaeae">// ECMAScript5 : Object.create()를 이용하여 다중상속을 할 수 있다.</span>
    <span style="color:#8da6ce">Student</span>.<span style="color:#8da6ce">prototype</span> <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">Object</span>.create(Person.<span style="color:#8da6ce">prototype</span>, {
        newField: {
            value: <span style="color:#61ce3c">'☆'</span>
            <span style="color:#aeaeae">/* configurable : 설정 변경 가능? false일 경우 추후 수정 불가.
             * enumerable : for in 가능?
             * value : 값
             * writable : 변경 가능?
             * get : getter, set : setter
             */</span>
        },
        newFunction: {
            <span style="color:#ff6400">value</span>: <span style="color:#fbde2d">function</span> () {
                <span style="color:#fbde2d">return</span> this.newField <span style="color:#fbde2d">+</span> <span style="color:#61ce3c">'이름 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">name</span> <span style="color:#fbde2d">+</span> <span style="color:#61ce3c">', 키 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">height</span> <span style="color:#fbde2d">+</span>
                    <span style="color:#61ce3c">', ID : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">id</span>;
            }
        }
    });
    <span style="color:#8da6ce">Student</span>.<span style="color:#8da6ce">prototype</span>.<span style="color:#ff6400">getID</span> <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">function</span> () { <span style="color:#fbde2d">return</span> this.<span style="color:#8da6ce">id</span> };
    <span style="color:#8da6ce">Student</span>.<span style="color:#8da6ce">prototype</span>.<span style="color:#ff6400">constructor</span> <span style="color:#fbde2d">=</span> Student;
    <span style="color:#aeaeae">// 상속 2</span>
    <span style="color:#fbde2d">function</span> <span style="color:#ff6400">Student2</span>(name, height, id) {
        Student.<span style="color:#8da6ce">call</span>(this, name, height, id);
    }
    <span style="color:#8da6ce">Student2</span>.<span style="color:#8da6ce">prototype</span> <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">Object</span>.create(Student.<span style="color:#8da6ce">prototype</span>, {
        toString: {
            <span style="color:#ff6400">value</span>: <span style="color:#fbde2d">function</span> () {
                <span style="color:#fbde2d">return</span> <span style="color:#61ce3c">'이름 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">name</span> <span style="color:#fbde2d">+</span> <span style="color:#61ce3c">', 키 : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">height</span> <span style="color:#fbde2d">+</span>
                    <span style="color:#61ce3c">', ID : '</span> <span style="color:#fbde2d">+</span> this.<span style="color:#8da6ce">id</span>;
            }
        }
    });
    <span style="color:#8da6ce">Student2</span>.<span style="color:#8da6ce">prototype</span>.<span style="color:#ff6400">constructor</span> <span style="color:#fbde2d">=</span> Student2;
<span style="color:#fbde2d">&lt;</span>/script<span style="color:#fbde2d">></span>
</pre>

<?php
putFooter();
?>

