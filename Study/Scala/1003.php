<?php
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/functions.php';
?>

<!DOCTYPE html>
<html lang="kor">

<?php
put_html_head('wiz');
?>

<body>

<?php
put_nav();
?>

    <main role="main">
        <div class="container">

<pre class="hljs" style="display: block; overflow-x: auto; padding: 0.5em; background: rgb(63, 63, 63); color: rgb(220, 220, 220);">
<span class="hljs-keyword" style="color: rgb(227, 206, 171);">sealed</span> <span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">trait</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">List</span>[+<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]</span>
<span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">object</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">Nil</span> <span class="hljs-keyword" style="color: rgb(227, 206, 171);">extends</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">Nothing</span>]</span>
<span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">class</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">Cons</span>[+<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](<span class="hljs-params">head: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, tail: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]</span>) <span class="hljs-keyword" style="color: rgb(227, 206, 171);">extends</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]</span>

<span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">object</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">List</span> </span>{
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">sum</span></span>(ints: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">Int</span>]): <span class="hljs-type" style="color: rgb(239, 239, 143);">Int</span> = ints <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-number" style="color: rgb(140, 208, 211);">0</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(x, xs) =&gt; x + sum(xs) <span class="hljs-comment" style="color: rgb(127, 159, 127);">// construct의 관례상 줄임말</span>
  }
  
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">tail</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = l <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; tail
  }
  
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">setHead</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>], v: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = l <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(v, tail)
  }
  
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// 처음 n개 삭제</span>
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">drop</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>], n: <span class="hljs-type" style="color: rgb(239, 239, 143);">Int</span>): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">if</span>(n == <span class="hljs-number" style="color: rgb(140, 208, 211);">0</span>) { l }
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">else</span> l <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
      <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
      <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; drop(tail, n<span class="hljs-number" style="color: rgb(140, 208, 211);">-1</span>)
    }
  }
  
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// 조건에 부합하는 연속적인 요소들을 head부터 삭제.</span>
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// def dropWhile[A](l: List[A], f: A =&gt; Boolean): List[A] = l match {</span>
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">dropWhile</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>])(f: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Boolean</span>): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = l <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; {
      <span class="hljs-keyword" style="color: rgb(227, 206, 171);">if</span>(f(head)) { dropWhile(tail)(f) }
      <span class="hljs-keyword" style="color: rgb(227, 206, 171);">else</span> l
    }
  }
  
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">append</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l1: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>], l2: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = l1 <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; l2
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, append(tail, l2))
  }
  
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// 마지막 요소만 제거</span>
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">removeLast</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](l: <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>]): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = l <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, tail) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(head, removeLast(tail))
  }
  
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// * 가변 인자 : 0개 이상</span>
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// 가변 인자는 컬렉션 라이브러리의 Seq[A]로 묶인다.</span>
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// Seq[A].head : 첫 요소, Seq[A].tail : head를 제외한 나머지 모든 요소</span>
  <span class="hljs-comment" style="color: rgb(127, 159, 127);">// :_* : Seq를 가변 인자로 보낼 수 있게 </span>
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">apply</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>](as: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>*): <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>] = {
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">if</span>(as.isEmpty) <span class="hljs-type" style="color: rgb(239, 239, 143);">Nil</span>
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">else</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(as.head, apply(as.tail: _*))
  }
}

<span class="hljs-keyword" style="color: rgb(227, 206, 171);">import</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>._

<span class="hljs-comment" style="color: rgb(127, 159, 127);">/**
 * Main.scala
 */</span>
<span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">object</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">Main</span> </span>{
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">main</span></span>(args: <span class="hljs-type" style="color: rgb(239, 239, 143);">Array</span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">String</span>]): <span class="hljs-type" style="color: rgb(239, 239, 143);">Unit</span> = {
    print(<span class="hljs-type" style="color: rgb(239, 239, 143);">List</span>(<span class="hljs-number" style="color: rgb(140, 208, 211);">10</span>, <span class="hljs-number" style="color: rgb(140, 208, 211);">2</span>, <span class="hljs-number" style="color: rgb(140, 208, 211);">3</span>, <span class="hljs-number" style="color: rgb(140, 208, 211);">4</span>, <span class="hljs-number" style="color: rgb(140, 208, 211);">5</span>) <span class="hljs-keyword" style="color: rgb(227, 206, 171);">match</span> {
      <span class="hljs-keyword" style="color: rgb(227, 206, 171);">case</span> <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(x, <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(y, <span class="hljs-type" style="color: rgb(239, 239, 143);">Cons</span>(<span class="hljs-number" style="color: rgb(140, 208, 211);">3</span>, _))) =&gt; x + y
    })
  }
}</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>