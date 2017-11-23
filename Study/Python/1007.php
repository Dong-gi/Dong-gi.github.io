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
code0008.py
함수
'''</span>

<span style="color:#fbde2d">print</span>(<span style="color:#61ce3c">'함수'</span>)

a <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">1</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func1</span>():
    <span style="color:#61ce3c">'func1() do nothing'</span>
    <span style="color:#fbde2d">pass</span> <span style="color:#aeaeae"># return 없이 함수 종료</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func2</span>():
    <span style="color:#fbde2d">print</span>(a) <span style="color:#aeaeae"># scope 공유</span>
    b <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span>

func2()
<span style="color:#aeaeae"># print(b) # 이건 안 됨</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func3</span>(n1, n2, n3):
    <span style="color:#fbde2d">print</span>((n1, n2, n3))

func3(<span style="color:#d8fa3c">1</span>, n3<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">2</span>, n2<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">4</span>)
<span style="color:#aeaeae"># func3(n2=2) # 전부 전달해야 됨</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func4</span>(n1<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">1</span>, n2<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">2</span>, n3<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">3</span>):
    <span style="color:#fbde2d">print</span>((n1, n2, n3))

func4()

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func5</span>(n, l <span style="color:#fbde2d">=</span> []): <span style="color:#aeaeae"># 기본 매개변수는 함수가 정의될 때 한번만 계산된다.</span>
    l.append(n)
    <span style="color:#fbde2d">print</span>(l)

func5(<span style="color:#d8fa3c">1</span>)
func5(<span style="color:#d8fa3c">2</span>)
func5(<span style="color:#d8fa3c">3</span>)

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func6</span>(*args):
    <span style="color:#fbde2d">print</span>(args)

func6()
func6(<span style="color:#d8fa3c">1</span>, <span style="color:#d8fa3c">2</span>, <span style="color:#d8fa3c">3</span>)
<span style="color:#aeaeae"># func6(n1 = 1, n2 = 2, n3 = 3) # 이름 있는 인자 전달 불가</span>

<span style="color:#fbde2d">def</span> <span style="color:#ff6400">func7</span>(**kwargs):
    <span style="color:#fbde2d">print</span>(kwargs)

func7()
<span style="color:#aeaeae"># func7(1, 2, 3) # 이름 없는 인자 전달 불가</span>
func7(n1 <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">1</span>, n2 <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span>, n3 <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">3</span>)

</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>