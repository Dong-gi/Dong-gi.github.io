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
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">int</span> area <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">int</span> max <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">char</span>[][] map <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">null</span>;

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> in <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> width <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
        <span style="color:#fbde2d">int</span> height <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
        in<span style="color:#fbde2d">.</span>nextLine();

        map <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">char</span>[height][width];
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> r <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; r <span style="color:#fbde2d">&lt;</span> height; <span style="color:#fbde2d">++</span>r) {
            <span style="color:#fbde2d">String</span> line <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextLine();
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> c <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; c <span style="color:#fbde2d">&lt;</span> width; <span style="color:#fbde2d">++</span>c)
                map[r][c] <span style="color:#fbde2d">=</span> line<span style="color:#fbde2d">.</span>charAt(c);
        }

        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> r <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; r <span style="color:#fbde2d">&lt;</span> height; <span style="color:#fbde2d">++</span>r) {
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> c <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; c <span style="color:#fbde2d">&lt;</span> width; <span style="color:#fbde2d">++</span>c) {
                check(r, c);
                max <span style="color:#fbde2d">=</span> (area <span style="color:#fbde2d">></span> max)<span style="color:#fbde2d">?</span> area <span style="color:#fbde2d">:</span> max;
                area <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
            }
        }

        <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(max);
    }

    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">check</span>(<span style="color:#fbde2d">int</span> row, <span style="color:#fbde2d">int</span> col) {
        <span style="color:#fbde2d">if</span>(row <span style="color:#fbde2d">&lt;</span> <span style="color:#d8fa3c">0</span> <span style="color:#fbde2d">||</span> col <span style="color:#fbde2d">&lt;</span> <span style="color:#d8fa3c">0</span> <span style="color:#fbde2d">||</span> row <span style="color:#fbde2d">>=</span> map<span style="color:#fbde2d">.</span>length <span style="color:#fbde2d">||</span> col <span style="color:#fbde2d">>=</span> map[<span style="color:#d8fa3c">0</span>]<span style="color:#fbde2d">.</span>length <span style="color:#fbde2d">||</span> map[row][col] <span style="color:#fbde2d">==</span> <span style="color:#61ce3c">'1'</span>)
            <span style="color:#fbde2d">return</span>;

        <span style="color:#fbde2d">boolean</span> isGrass <span style="color:#fbde2d">=</span> (map[row][col] <span style="color:#fbde2d">==</span> <span style="color:#61ce3c">'*'</span>); 
        map[row][col] <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">'1'</span>;

        <span style="color:#fbde2d">if</span>(isGrass) {
            area <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">1</span>;
            <span style="color:#aeaeae">//System.out.printf("row : %d, col : %d, area : %d\n", row, col, area);</span>
            check(row<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>, col);
            check(row<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>, col);
            check(row, col<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>);
            check(row, col<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>);
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