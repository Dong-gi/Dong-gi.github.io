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

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">import</span> <span style="color:#fbde2d">java.util.*</span>;

<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Main</span> {
    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> scanner <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> testCase <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
        <span style="color:#fbde2d">while</span>(testCase<span style="color:#fbde2d">--</span> <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
            <span style="color:#fbde2d">int</span> length <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
            <span style="color:#fbde2d">int</span>[] price <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[length];
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> length; <span style="color:#fbde2d">++</span>i) {
                price[i] <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
            }
            
            <span style="color:#fbde2d">int</span>[] mins <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[length];
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> length; <span style="color:#fbde2d">++</span>i) {
                <span style="color:#fbde2d">int</span>[] beforeMins <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[<span style="color:#d8fa3c">3</span>];
                beforeMins[<span style="color:#d8fa3c">0</span>] <span style="color:#fbde2d">=</span> (i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">3</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">3</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
                beforeMins[<span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">=</span> (i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
                beforeMins[<span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">=</span> (i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[i<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
                <span style="color:#fbde2d">Arrays</span><span style="color:#fbde2d">.</span>sort(beforeMins);
                mins[i] <span style="color:#fbde2d">=</span> beforeMins[<span style="color:#d8fa3c">0</span>] <span style="color:#fbde2d">+</span> price[i];
            }
            
            <span style="color:#fbde2d">int</span>[] result <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[<span style="color:#d8fa3c">3</span>];
            result[<span style="color:#d8fa3c">0</span>] <span style="color:#fbde2d">=</span> (length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">3</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">3</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
            result[<span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">=</span> (length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
            result[<span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">=</span> (length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span> <span style="color:#fbde2d">>=</span> <span style="color:#d8fa3c">0</span>)<span style="color:#fbde2d">?</span> mins[length<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">:</span> <span style="color:#d8fa3c">0</span>;
            <span style="color:#fbde2d">Arrays</span><span style="color:#fbde2d">.</span>sort(result);
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(result[<span style="color:#d8fa3c">0</span>]);
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