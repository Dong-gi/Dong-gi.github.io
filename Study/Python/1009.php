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

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#61ce3c">'''
code0010.py
네임스페이스
'''</span>

a <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">1</span>
b <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func</span>():
    c <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">3</span>
    d <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">4</span>
    <span style="color:#fbde2d">global</span> a
    a <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">4</span>
    <span style="color:#fbde2d">print</span>(a)
    <span style="color:#fbde2d">print</span>(<span style="color:#8da6ce">locals</span>())
    <span style="color:#fbde2d">print</span>(<span style="color:#8da6ce">globals</span>())

func()

</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>