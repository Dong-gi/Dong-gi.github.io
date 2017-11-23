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
        <span style="color:#fbde2d">Integer</span>[] start <span style="color:#fbde2d">=</span> {<span style="color:#d8fa3c">0</span>, <span style="color:#d8fa3c">0</span>, <span style="color:#d8fa3c">0</span>, <span style="color:#d8fa3c">0</span>};
        <span style="color:#fbde2d">while</span>(testCase<span style="color:#fbde2d">--</span> <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
            <span style="color:#fbde2d">int</span> length <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
            <span style="color:#fbde2d">Integer</span>[][] passwords <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[length][<span style="color:#d8fa3c">4</span>];
            <span style="color:#fbde2d">int</span> nextDistance <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">Integer</span><span style="color:#d8fa3c"><span style="color:#fbde2d">.</span>MAX_VALUE</span>;
            <span style="color:#fbde2d">int</span> nextIdx <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> length; <span style="color:#fbde2d">++</span>i) {
                <span style="color:#fbde2d">String</span> password <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>next();
                passwords[i][<span style="color:#d8fa3c">0</span>] <span style="color:#fbde2d">=</span> password<span style="color:#fbde2d">.</span>charAt(<span style="color:#d8fa3c">0</span>) <span style="color:#fbde2d">-</span> <span style="color:#61ce3c">'0'</span>;
                passwords[i][<span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">=</span> password<span style="color:#fbde2d">.</span>charAt(<span style="color:#d8fa3c">1</span>) <span style="color:#fbde2d">-</span> <span style="color:#61ce3c">'0'</span>;
                passwords[i][<span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">=</span> password<span style="color:#fbde2d">.</span>charAt(<span style="color:#d8fa3c">2</span>) <span style="color:#fbde2d">-</span> <span style="color:#61ce3c">'0'</span>;
                passwords[i][<span style="color:#d8fa3c">3</span>] <span style="color:#fbde2d">=</span> password<span style="color:#fbde2d">.</span>charAt(<span style="color:#d8fa3c">3</span>) <span style="color:#fbde2d">-</span> <span style="color:#61ce3c">'0'</span>;
                <span style="color:#fbde2d">int</span> distance <span style="color:#fbde2d">=</span> distance(start, passwords[i]);
                <span style="color:#fbde2d">if</span>(nextDistance <span style="color:#fbde2d">></span> distance) {
                    nextDistance <span style="color:#fbde2d">=</span> distance;
                    nextIdx <span style="color:#fbde2d">=</span> i;
                }
            }
            <span style="color:#fbde2d">int</span>[] shortestDistances <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[length];
            <span style="color:#fbde2d">int</span> totalDistance <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
            <span style="color:#fbde2d">Arrays</span><span style="color:#fbde2d">.</span>fill(shortestDistances, <span style="color:#fbde2d">Integer</span><span style="color:#d8fa3c"><span style="color:#fbde2d">.</span>MAX_VALUE</span>);
            shortestDistances[nextIdx] <span style="color:#fbde2d">=</span> nextDistance; 
            <span style="color:#fbde2d">HashSet&lt;<span style="color:#fbde2d">Integer</span>></span> visited <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">HashSet&lt;></span>();
            <span style="color:#fbde2d">while</span>(<span style="color:#d8fa3c">true</span>) {
                <span style="color:#fbde2d">int</span> curIdx <span style="color:#fbde2d">=</span> nextIdx;
                totalDistance <span style="color:#fbde2d">+=</span> shortestDistances[curIdx];
                shortestDistances[curIdx] <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
                visited<span style="color:#fbde2d">.</span>add(curIdx);
                <span style="color:#fbde2d">if</span>(visited<span style="color:#fbde2d">.</span>size() <span style="color:#fbde2d">==</span> length) {
                    <span style="color:#fbde2d">break</span>;
                }
                nextDistance <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">Integer</span><span style="color:#d8fa3c"><span style="color:#fbde2d">.</span>MAX_VALUE</span>;
                <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> length; <span style="color:#fbde2d">++</span>i) {
                    <span style="color:#fbde2d">if</span>(shortestDistances[i] <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
                        <span style="color:#fbde2d">int</span> distance <span style="color:#fbde2d">=</span> distance(passwords[i], passwords[curIdx]);
                        <span style="color:#fbde2d">if</span>(shortestDistances[i] <span style="color:#fbde2d">></span> distance) {
                            shortestDistances[i] <span style="color:#fbde2d">=</span> distance;
                        }
                        <span style="color:#fbde2d">if</span>(nextDistance <span style="color:#fbde2d">></span> shortestDistances[i]) {
                            nextDistance <span style="color:#fbde2d">=</span> shortestDistances[i];
                            nextIdx <span style="color:#fbde2d">=</span> i;
                        }
                    }
                }
            }
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(totalDistance);
        }
    }

    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">int</span> <span style="color:#ff6400">distance</span>(<span style="color:#fbde2d">Integer</span>[] nums1, <span style="color:#fbde2d">Integer</span>[] nums2) {
        <span style="color:#fbde2d">int</span> result <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span><span style="color:#d8fa3c">4</span>; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">int</span> dist <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">Math</span><span style="color:#fbde2d">.</span>abs(nums1[i] <span style="color:#fbde2d">-</span> nums2[i]);
            result <span style="color:#fbde2d">+=</span> dist <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">5</span><span style="color:#fbde2d">?</span> <span style="color:#d8fa3c">10</span><span style="color:#fbde2d">-</span>dist <span style="color:#fbde2d">:</span> dist;
        }
        <span style="color:#fbde2d">return</span> result;
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