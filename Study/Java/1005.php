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

<pre style="background:#0c1021;color:#f8f8f8">
<span style="color:#fbde2d">interface</span> <span style="color:#ff6400">Actable</span>
{
    default <span style="color:#fbde2d">void</span> <span style="color:#ff6400">act</span>() {
        <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Outer Interface"</span>);
    }
}

<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Main</span> {
    <span style="color:#aeaeae">// 정적 멤버 클래스</span>
    <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Inner1</span> {
        <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">print</span>() {
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Static Nested Class"</span>);
        }
    }

    <span style="color:#aeaeae">// 비정적 멤버 클래스</span>
    <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Inner2</span> {
        <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">print</span>() {
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Non-Static Nested Class"</span>);
        }
    }

    <span style="color:#aeaeae">// 클래스 내의 인터페이스</span>
    <span style="color:#aeaeae">/*자동으로 static*/</span> <span style="color:#fbde2d">interface</span> <span style="color:#ff6400">Actable</span> {
        default <span style="color:#fbde2d">void</span> <span style="color:#ff6400">act</span>() {
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Inner Interface"</span>);
        }
    }

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Inner1</span> in1 <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Main</span>.<span style="color:#fbde2d">Inner1</span>();
        <span style="color:#fbde2d">Main</span> main <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Main</span>();
        <span style="color:#fbde2d">Inner2</span> in2 <span style="color:#fbde2d">=</span> main<span style="color:#fbde2d">.</span><span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Inner2</span>();

        <span style="color:#aeaeae">// anonymous class</span>
        <span style="color:#aeaeae">// 자신을 둘러싸고 있는 영역의 final 변수를 이용할 수 있다.</span>
        in1 <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Inner1</span>() {
            <span style="color:#fbde2d">@Override</span>
            <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">print</span>() {
                <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"New Print"</span>);
            }
        };
        in1<span style="color:#fbde2d">.</span>print();

        <span style="color:#aeaeae">// Main.Actable이 참조된다.</span>
        <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Actable</span>() {
            <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">String</span> msg <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"Hello World"</span>;
            <span style="color:#fbde2d">@Override</span>
            <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">act</span>() {
                <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(msg);
            }
        }<span style="color:#fbde2d">.</span>act();

        <span style="color:#aeaeae">// local class</span>
        <span style="color:#aeaeae">// 자신을 둘러싸고 있는 영역의 final 변수를 이용할 수 있다.</span>
        <span style="color:#fbde2d">final</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">LocalClass</span> <span style="color:#fbde2d">implements</span> <span style="color:#ff6400;font-style:italic">Actable</span> {}
        <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">LocalClass</span>()<span style="color:#fbde2d">.</span>act();
    }
}
</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>