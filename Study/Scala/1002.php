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
<span class="hljs-class"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">object</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">Prac</span> </span>{
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">curry</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>](f: (<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>): <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span> =&gt; (<span class="hljs-type" style="color: rgb(239, 239, 143);">B</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>) = {
    (a: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>) =&gt; ((b: <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>) =&gt; f(a, b))
  }
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">uncarry</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>](f: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>): (<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>) =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span> = {
    (a: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, b: <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>) =&gt; f(a)(b)
  }
  <span class="hljs-function"><span class="hljs-keyword" style="color: rgb(227, 206, 171);">def</span> <span class="hljs-title" style="color: rgb(239, 239, 143);">compose</span></span>[<span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>, <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>](f: <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span>, g: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">B</span>): <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span> =&gt; <span class="hljs-type" style="color: rgb(239, 239, 143);">C</span> = {
    (a: <span class="hljs-type" style="color: rgb(239, 239, 143);">A</span>) =&gt; f(g(a)) <span class="hljs-comment" style="color: rgb(127, 159, 127);">// f compose g == g andThen f</span>
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