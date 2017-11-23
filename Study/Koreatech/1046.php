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

    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">char</span>[][] map;
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">boolean</span> reachable;

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> in <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> testCase <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
        <span style="color:#fbde2d">while</span>(testCase<span style="color:#fbde2d">--</span> <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
            reachable <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">false</span>;
            map <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">char</span>[in<span style="color:#fbde2d">.</span>nextInt()][in<span style="color:#fbde2d">.</span>nextInt()];
            <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>[]></span> next <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;></span>();
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> map<span style="color:#fbde2d">.</span>length; <span style="color:#fbde2d">++</span>i) {
                <span style="color:#fbde2d">String</span> line <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>next();
                <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> j <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; j <span style="color:#fbde2d">&lt;</span> map[<span style="color:#d8fa3c">0</span>]<span style="color:#fbde2d">.</span>length; <span style="color:#fbde2d">++</span>j) {
                    map[i][j] <span style="color:#fbde2d">=</span> line<span style="color:#fbde2d">.</span>charAt(j);
                    <span style="color:#fbde2d">if</span>(map[i][j] <span style="color:#fbde2d">==</span> <span style="color:#61ce3c">'E'</span>) {
                        next<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {i, j});
                    }
                }
            }

            <span style="color:#fbde2d">int</span> round <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
            <span style="color:#fbde2d">while</span>(<span style="color:#fbde2d">!</span>reachable) {
                <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>[]></span> cur <span style="color:#fbde2d">=</span> next;
                next <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;></span>();
                <span style="color:#fbde2d">if</span>(cur<span style="color:#fbde2d">.</span>isEmpty()) {
                    <span style="color:#fbde2d">break</span>;
                }
                <span style="color:#fbde2d">while</span>(<span style="color:#fbde2d">!</span>cur<span style="color:#fbde2d">.</span>isEmpty()) {
                    <span style="color:#fbde2d">Integer</span>[] pos <span style="color:#fbde2d">=</span> cur<span style="color:#fbde2d">.</span>remove(<span style="color:#d8fa3c">0</span>);
                    <span style="color:#fbde2d">int</span> r <span style="color:#fbde2d">=</span> pos[<span style="color:#d8fa3c">0</span>], c <span style="color:#fbde2d">=</span> pos[<span style="color:#d8fa3c">1</span>];
                    <span style="color:#fbde2d">if</span>(mark(r, c<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>)) {
                        next<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {r, c<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>});
                    }
                    <span style="color:#fbde2d">if</span>(mark(r, c<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)) {
                        next<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {r, c<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>});
                    }
                    <span style="color:#fbde2d">if</span>(mark(r<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>, c)) {
                        next<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {r<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>, c});
                    }
                    <span style="color:#fbde2d">if</span>(mark(r<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>, c)) {
                        next<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {r<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>, c});
                    }
                }
                round <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">1</span>;
            }
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(reachable<span style="color:#fbde2d">?</span> round <span style="color:#fbde2d">:</span> <span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>);
        }
    }

    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">boolean</span> <span style="color:#ff6400">mark</span>(<span style="color:#fbde2d">int</span> r, <span style="color:#fbde2d">int</span> c) {
        <span style="color:#fbde2d">try</span> {
            <span style="color:#fbde2d">switch</span>(map[r][c]) {
            <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'S'</span><span style="color:#fbde2d">:</span> reachable <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">true</span>; <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">true</span>;
            <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'#'</span><span style="color:#fbde2d">:</span> <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">false</span>;
            <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'-'</span><span style="color:#fbde2d">:</span> map[r][c] <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">'.'</span>; <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">true</span>;
            <span style="color:#fbde2d">default</span><span style="color:#fbde2d">:</span> <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">false</span>;
            }
        } <span style="color:#fbde2d">catch</span>(<span style="color:#fbde2d">Exception</span> e) { <span style="color:#fbde2d">return</span> <span style="color:#d8fa3c">false</span>; }
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