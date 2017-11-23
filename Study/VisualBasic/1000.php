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

<pre class="hljs" style="display: block; overflow-x: auto; padding: 0.5em; background: rgb(63, 63, 63); color: rgb(220, 220, 220);">Public Class Form1

    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
        <span class="hljs-comment" style="color: rgb(127, 159, 127);">' TextBox에서 MultiLine 속성을 True로 했다.</span>
        <span class="hljs-comment" style="color: rgb(127, 159, 127);">' 왜 \이 이스케이프되지 않는진 모르겠지만...</span>
        TextBox1.Text = <span class="hljs-string" style="color: rgb(204, 147, 147);">"10 \ 3 : "</span> &amp; (<span class="hljs-number" style="color: rgb(140, 208, 211);">10</span> \ <span class="hljs-number" style="color: rgb(140, 208, 211);">3</span>) &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"
10 Mod 3 : "</span> &amp; (<span class="hljs-number" style="color: rgb(140, 208, 211);">10</span> <span class="hljs-keyword" style="color: rgb(227, 206, 171);">Mod</span> <span class="hljs-number" style="color: rgb(140, 208, 211);">3</span>) &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"
0.1 ^ 3 : "</span> &amp; (<span class="hljs-number" style="color: rgb(140, 208, 211);">0.1</span> ^ <span class="hljs-number" style="color: rgb(140, 208, 211);">3</span>)
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">End</span> Sub

    Private Sub Button2_Click(sender As Object, e As EventArgs) Handles Button2.Click
        <span class="hljs-keyword" style="color: rgb(227, 206, 171);">Dim</span> s1 As String = <span class="hljs-string" style="color: rgb(204, 147, 147);">"Hello"</span>
        <span class="hljs-keyword" style="color: rgb(227, 206, 171);">Dim</span> s2 As String = <span class="hljs-string" style="color: rgb(204, 147, 147);">"Hel"</span>
        TextBox1.Text = <span class="hljs-string" style="color: rgb(204, 147, 147);">""</span> &amp; (<span class="hljs-string" style="color: rgb(204, 147, 147);">"Hello World"</span> Like <span class="hljs-string" style="color: rgb(204, 147, 147);">"Hello"</span>) &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"
"</span> &amp; (<span class="hljs-string" style="color: rgb(204, 147, 147);">"Hello World"</span> Like <span class="hljs-string" style="color: rgb(204, 147, 147);">"H*"</span>) &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"
"</span> &amp; (s1 = (s2 &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"lo"</span>)) &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"
"</span> &amp; (s1 Is (s2 &amp; <span class="hljs-string" style="color: rgb(204, 147, 147);">"lo"</span>))
    <span class="hljs-keyword" style="color: rgb(227, 206, 171);">End</span> Sub

<span class="hljs-keyword" style="color: rgb(227, 206, 171);">End</span> Class
</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>