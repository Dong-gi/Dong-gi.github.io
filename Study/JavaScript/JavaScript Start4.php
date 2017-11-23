<?php
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/create_code_page.php';

putHeader();
putTitle("자료형");
?>

<button onclick="func1()">결과 확인</button>
<p>number, string, boolean, function, object, undefined<br/>초기화되지 않은 변수도 undefined</p>

<script>
	function func1() {
		var tmp;
		var arr = [1, '1', true, function () { }, {}, tmp];
		console.log(typeof (arr));
		for (var i = 0; i < arr.length; ++i)
			console.log(typeof (arr[i]));
		console.log(typeof (Number('1')));
		console.log(typeof (String(1)));
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">var</span> tmp;
<span style="color:#fbde2d">var</span> arr <span style="color:#fbde2d">=</span> [<span style="color:#d8fa3c">1</span>, <span style="color:#61ce3c">'1'</span>, <span style="color:#d8fa3c">true</span>, <span style="color:#fbde2d">function</span> () { }, {}, tmp];
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#fbde2d">typeof</span> (arr));
<span style="color:#fbde2d">for</span> (<span style="color:#fbde2d">var</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> arr.<span style="color:#8da6ce">length</span>; <span style="color:#fbde2d">++</span>i)
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#fbde2d">typeof</span> (arr[i]));
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#fbde2d">typeof</span> (<span style="color:#8da6ce">Number</span>(<span style="color:#61ce3c">'1'</span>)));
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#fbde2d">typeof</span> (<span style="color:#8da6ce">String</span>(<span style="color:#d8fa3c">1</span>)));
</pre>

<?php
putTitle("false가 되는 5가지 형변환");
?>

<button onclick="func2()">결과 확인</button>
<script>
	function func2() {
		console.log(Boolean(0)); // == !!0
		console.log(Boolean(NaN)); // == !!NaN
		console.log(Boolean(''));
		console.log(Boolean(null));
		console.log(Boolean(undefined));
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Boolean</span>(<span style="color:#d8fa3c">0</span>)); <span style="color:#aeaeae">// == !!0</span>
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Boolean</span>(<span style="color:#d8fa3c">NaN</span>)); <span style="color:#aeaeae">// == !!NaN</span>
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Boolean</span>(<span style="color:#61ce3c">''</span>));
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Boolean</span>(<span style="color:#d8fa3c">null</span>));
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Boolean</span>(<span style="color:#d8fa3c">undefined</span>));
</pre>

<?php
putTitle("switch 문");
?>
<button onclick="func3()">결과 확인</button>
<p>문자열, boolean 등도 가능</p>
<script>
	function func3() {
		switch ('asdf') {
			case 'a': console.log('a'); break;
			case 'as': console.log('as'); break;
			case 'asd': console.log('asd'); break;
			case 'asdf': console.log('asdf'); break;
		}
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">switch</span> (<span style="color:#61ce3c">'asdf'</span>) {
    <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'a'</span>: <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'a'</span>); <span style="color:#fbde2d">break</span>;
    <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'as'</span>: <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'as'</span>); <span style="color:#fbde2d">break</span>;
    <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'asd'</span>: <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'asd'</span>); <span style="color:#fbde2d">break</span>;
    <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'asdf'</span>: <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'asdf'</span>); <span style="color:#fbde2d">break</span>;
}
</pre>

<?php
putTitle("삼항 연산과 짧은 조건문");
?>

<button onclick="func4()">결과 확인</button>
<p>연산자 수행 특징을 이용. void 반환이라도 실행된다.</p>
<script>
	function func4() {
		true? console.log('실행됨') : console.log('실행되지 않음');
		true || console.log('실행되지 않음');
		false || console.log('실행됨');
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#d8fa3c">true</span>? <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'실행됨'</span>) : <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'실행되지 않음'</span>);
<span style="color:#d8fa3c">true</span> <span style="color:#fbde2d">||</span> <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'실행되지 않음'</span>);
<span style="color:#d8fa3c">false</span> <span style="color:#fbde2d">||</span> <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'실행됨'</span>);
</pre>

<?php
putTitle("함수의 재정의");
?>

<button onclick="func5()">결과 확인</button>
<script>
	function func5() {
		function tmp() { console.log(1); }
		tmp();
		function tmp() { console.log(2); }
		tmp();
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">function</span> <span style="color:#ff6400">tmp</span>() { <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#d8fa3c">1</span>); }
tmp();
<span style="color:#fbde2d">function</span> <span style="color:#ff6400">tmp</span>() { <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#d8fa3c">2</span>); }
tmp();
</pre>

<?php
putTitle("가변인자 함수");
?>

<p>호출할 때, 선언된 매개변수보다 적은 인자를 넘길 경우 나머지는 undefined로 채워지고<br/>
선언된 매개변수보다 많은 인자를 넘길 경우 나머지는 무시된다.<br/>
넘겨진 인자는 arguments변수에 저장된다.</p>

<button onclick="func6()">결과 확인</button>
<script>
	function func6() {
		console.log(Array()); // 빈 배열 생성
		console.log(Array(3)); // 크기가 3인 배열 생성
		console.log(Array(1, '1', true)); // 배열 생성
		console.log(sum());
		console.log(sum(3));
		console.log(sum(1, '2', 'a', true));
	}
	function sum() {
		if(arguments.length === 0) {
			return 0;
		} else if(arguments.length === 1) {
			return arguments[0];
		} else {
			var sum = 0;
			for(var i = 0; i <arguments.length; ++i) {
				var num = Number(arguments[i]);
				num && (sum += num);
			}
			return sum;
		}
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">function</span> <span style="color:#ff6400">func6</span>() {
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Array</span>()); <span style="color:#aeaeae">// 빈 배열 생성</span>
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Array</span>(<span style="color:#d8fa3c">3</span>)); <span style="color:#aeaeae">// 크기가 3인 배열 생성</span>
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#8da6ce">Array</span>(<span style="color:#d8fa3c">1</span>, <span style="color:#61ce3c">'1'</span>, <span style="color:#d8fa3c">true</span>)); <span style="color:#aeaeae">// 배열 생성</span>
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(sum());
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(sum(<span style="color:#d8fa3c">3</span>));
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(sum(<span style="color:#d8fa3c">1</span>, <span style="color:#61ce3c">'2'</span>, <span style="color:#61ce3c">'a'</span>, <span style="color:#d8fa3c">true</span>));
}
<span style="color:#fbde2d">function</span> <span style="color:#ff6400">sum</span>() {
    <span style="color:#fbde2d">if</span>(arguments.<span style="color:#8da6ce">length</span> <span style="color:#fbde2d">===</span> <span style="color:#d8fa3c">0</span>) {
        <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">0</span>;
    } <span style="color:#fbde2d">else</span> <span style="color:#fbde2d">if</span>(arguments.<span style="color:#8da6ce">length</span> <span style="color:#fbde2d">===</span> <span style="color:#d8fa3c">1</span>) {
        <span style="color:#fbde2d">return</span> arguments[<span style="color:#d8fa3c">0</span>];
    } <span style="color:#fbde2d">else</span> {
        <span style="color:#fbde2d">var</span> sum <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">var</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span>arguments.<span style="color:#8da6ce">length</span>; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">var</span> num <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">Number</span>(arguments[i]);
            num <span style="color:#fbde2d">&amp;</span><span style="color:#fbde2d">&amp;</span> (sum <span style="color:#fbde2d">+</span><span style="color:#fbde2d">=</span> num);
        }
        <span style="color:#fbde2d">return</span> sum;
    }
}
</pre>

<?php
putTitle("함수를 인자로 전달");
?>

<button onclick="func7()">결과 확인</button>
<script>
	function func7() {
		function tmp() { return 1; }
		var tmp1 = function(){ return 2; };
		function tmp2(functionName) { console.log(functionName()); }
		tmp2(tmp);
		tmp2(tmp1);
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">function</span> <span style="color:#ff6400">tmp</span>() { <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">1</span>; }
<span style="color:#fbde2d">var</span> <span style="color:#ff6400">tmp1</span> <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">function</span>(){ <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">2</span>; };
<span style="color:#fbde2d">function</span> <span style="color:#ff6400">tmp2</span>(functionName) { <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(functionName()); }
tmp2(tmp);
tmp2(tmp1);
</pre>

<?php
putTitle("클로저 Closure");
?>

<p>함수를 리턴함으로써 지역변수의 생존범위를 외부로 연장</p>
<button onclick="func8()">결과 확인</button>
<script>
	function func8() {
		var result = func8sub('Hello World!');
		result();
		result = func8sub('Bye World!');
		result();
		//console.log(str);
	}
	function func8sub(str) {
		str = 'str = ' + str;
		return function() {
			console.log(str);
		}
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">function</span> <span style="color:#ff6400">func8</span>() {
    <span style="color:#fbde2d">var</span> result <span style="color:#fbde2d">=</span> func8sub(<span style="color:#61ce3c">'Hello World!'</span>);
    result();
    result <span style="color:#fbde2d">=</span> func8sub(<span style="color:#61ce3c">'Bye World!'</span>);
    result();
    <span style="color:#aeaeae">//console.log(str);</span>
}
<span style="color:#fbde2d">function</span> <span style="color:#ff6400">func8sub</span>(str) {
    str <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">'str = '</span> <span style="color:#fbde2d">+</span> str;
    <span style="color:#fbde2d">return</span> <span style="color:#fbde2d">function</span>() {
        <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(str);
    }
}
</pre>

<?php
putFooter();
?>

