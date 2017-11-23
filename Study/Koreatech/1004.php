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

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">import</span> <span style="color:#fbde2d">java.util.Scanner</span>;

<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Main</span> {

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> scanner <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> testCase <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
        scanner<span style="color:#fbde2d">.</span>nextLine();
        <span style="color:#fbde2d">while</span>(testCase<span style="color:#fbde2d">--</span> <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
            <span style="color:#fbde2d">String</span> numberString <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextLine();
            <span style="color:#fbde2d">int</span> reverse <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">Integer</span><span style="color:#fbde2d">.</span>parseInt(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">StringBuilder</span>(numberString)<span style="color:#fbde2d">.</span>reverse()<span style="color:#fbde2d">.</span>toString());
            <span style="color:#fbde2d">String</span> sumString <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">""</span> <span style="color:#fbde2d">+</span> (<span style="color:#fbde2d">Integer</span><span style="color:#fbde2d">.</span>parseInt(numberString) <span style="color:#fbde2d">+</span> reverse);
            <span style="color:#fbde2d">if</span>(sumString<span style="color:#fbde2d">.</span>equals(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">StringBuilder</span>(sumString)<span style="color:#fbde2d">.</span>reverse()<span style="color:#fbde2d">.</span>toString())) {
                <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"yes"</span>);
            } <span style="color:#fbde2d">else</span> {
                <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"no"</span>);
            }
        }
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