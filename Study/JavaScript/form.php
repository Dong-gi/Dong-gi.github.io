<?php
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/create_code_page.php';

putHeader();
putTitle("form 1");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">&lt;</span>form action<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"#"</span> id<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"form1"</span><span style="color:#fbde2d">></span>
    <span style="color:#fbde2d">&lt;</span>input type<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"text"</span> name<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"str"</span> value<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"텍스트 입력"</span>/<span style="color:#fbde2d">></span>
    <span style="color:#fbde2d">&lt;</span>input type<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"submit"</span> value<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"전송"</span>/<span style="color:#fbde2d">></span>
<span style="color:#fbde2d">&lt;</span>/form<span style="color:#fbde2d">></span>

<span style="color:#fbde2d">&lt;</span>script<span style="color:#fbde2d">></span>
<span style="color:#8da6ce">document</span>.<span style="color:#8da6ce">getElementById</span>(<span style="color:#61ce3c">'form1'</span>).<span style="color:#ff6400">onsubmit</span> <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">function</span>() {
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'버튼 클릭됨'</span>);
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'내용 : '</span> <span style="color:#fbde2d">+</span> <span style="color:#8da6ce">document</span>.<span style="color:#8da6ce">getElementById</span>(<span style="color:#61ce3c">'form1'</span>).str.<span style="color:#8da6ce">value</span>);
    <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">false</span>; <span style="color:#aeaeae">// HTML이 갱신되는 form의 기본 동작을 취소</span>
};
<span style="color:#fbde2d">&lt;</span>/script<span style="color:#fbde2d">></span>
</pre>

<form action="#" id="form1">
    <input type="text" name="str" value="텍스트 입력"/>
    <input type="submit" value="전송"/>
</form>

<script>
document.getElementById('form1').onsubmit = function() {
    console.log('버튼 클릭됨');
    console.log('내용 : ' + document.getElementById('form1').str.value);
    return false; // HTML이 갱신되는 form의 기본 동작을 취소
};
</script>

<?php
putTitle("form 2");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">&lt;</span>form action<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"func1"</span> id<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"form2"</span><span style="color:#fbde2d">></span>
    <span style="color:#fbde2d">&lt;</span>input type<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"text"</span> name<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"str"</span> value<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"텍스트 입력"</span>/<span style="color:#fbde2d">></span>
    <span style="color:#fbde2d">&lt;</span>input type<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"button"</span> onclick<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"func1()"</span> value<span style="color:#fbde2d">=</span><span style="color:#61ce3c">"전송"</span>/<span style="color:#fbde2d">></span>
<span style="color:#fbde2d">&lt;</span>/form<span style="color:#fbde2d">></span>

<span style="color:#fbde2d">&lt;</span>script<span style="color:#fbde2d">></span>
<span style="color:#fbde2d">function</span> <span style="color:#ff6400">func1</span>() {
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'버튼 클릭됨'</span>);
    <span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(<span style="color:#61ce3c">'내용 : '</span> <span style="color:#fbde2d">+</span> <span style="color:#8da6ce">document</span>.<span style="color:#8da6ce">getElementById</span>(<span style="color:#61ce3c">'form1'</span>).str.<span style="color:#8da6ce">value</span>);
    <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">false</span>; <span style="color:#aeaeae">// HTML이 갱신되는 form의 기본 동작을 취소</span>
};
<span style="color:#fbde2d">&lt;</span>/script<span style="color:#fbde2d">></span>
</pre>

<form action="func1" id="form2">
    <input type="text" name="str" value="텍스트 입력"/>
    <input type="button" onclick="func1()" value="전송"/>
</form>

<script>
function func1() {
    console.log('버튼 클릭됨');
    console.log('내용 : ' + document.getElementById('form1').str.value);
    return false; // HTML이 갱신되는 form의 기본 동작을 취소
};
</script>

<?php
putFooter();
?>

