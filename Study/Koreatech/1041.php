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

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">import</span> <span style="color:#fbde2d">java.util.ArrayList</span>;
<span style="color:#fbde2d">import</span> <span style="color:#fbde2d">java.util.Comparator</span>;
<span style="color:#fbde2d">import</span> <span style="color:#fbde2d">java.util.Scanner</span>;

<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Main</span> {
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> xPositions <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span>();
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> yPositions <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span>();
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> xValues <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span>();
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> yValues <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span>();

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> in <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> size <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();

        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i<span style="color:#fbde2d">=</span><span style="color:#d8fa3c">0</span>; i<span style="color:#fbde2d">&lt;</span>size; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">int</span> x <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
            xPositions<span style="color:#fbde2d">.</span>add(x);
            <span style="color:#fbde2d">if</span>(<span style="color:#fbde2d">!</span>xValues<span style="color:#fbde2d">.</span>contains(x))
                xValues<span style="color:#fbde2d">.</span>add(x);

            <span style="color:#fbde2d">int</span> y <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
            yPositions<span style="color:#fbde2d">.</span>add(y);
            <span style="color:#fbde2d">if</span>(<span style="color:#fbde2d">!</span>yValues<span style="color:#fbde2d">.</span>contains(y))
                yValues<span style="color:#fbde2d">.</span>add(y);
        }

        <span style="color:#fbde2d">Comparator&lt;<span style="color:#fbde2d">Integer</span>></span> intCmp <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Comparator&lt;<span style="color:#fbde2d">Integer</span>></span>(){
            <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">int</span> <span style="color:#ff6400">compare</span>(<span style="color:#fbde2d">Integer</span> o1, <span style="color:#fbde2d">Integer</span> o2) {
                <span style="color:#fbde2d">return</span> o1 <span style="color:#fbde2d">-</span> o2;
            }
        };
        xPositions<span style="color:#fbde2d">.</span>sort(intCmp);
        xValues<span style="color:#fbde2d">.</span>sort(intCmp);
        yPositions<span style="color:#fbde2d">.</span>sort(intCmp);
        yValues<span style="color:#fbde2d">.</span>sort(intCmp);

        <span style="color:#fbde2d">int</span> distance <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#aeaeae">// check x</span>
        {
            <span style="color:#fbde2d">int</span> checkPoint <span style="color:#fbde2d">=</span> xValues<span style="color:#fbde2d">.</span>get((xValues<span style="color:#fbde2d">.</span>size()<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)<span style="color:#fbde2d">/</span><span style="color:#d8fa3c">2</span>);
            <span style="color:#fbde2d">int</span> checkIdx <span style="color:#fbde2d">=</span> xValues<span style="color:#fbde2d">.</span>indexOf(checkPoint);
            <span style="color:#fbde2d">boolean</span> hasFound <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">false</span>;
            <span style="color:#fbde2d">int</span> numOfLeftPoints, numOfRightPoints;
            <span style="color:#fbde2d">while</span>(<span style="color:#fbde2d">!</span>hasFound) {
                numOfLeftPoints <span style="color:#fbde2d">=</span> xPositions<span style="color:#fbde2d">.</span>indexOf(checkPoint);
                <span style="color:#fbde2d">if</span>(checkIdx <span style="color:#fbde2d">==</span> xValues<span style="color:#fbde2d">.</span>size()<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)
                    numOfRightPoints <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
                <span style="color:#fbde2d">else</span>
                    numOfRightPoints <span style="color:#fbde2d">=</span> size <span style="color:#fbde2d">-</span> xPositions<span style="color:#fbde2d">.</span>indexOf(xValues<span style="color:#fbde2d">.</span>get(checkIdx<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>));

                <span style="color:#fbde2d">if</span>(numOfLeftPoints <span style="color:#fbde2d">></span> size <span style="color:#fbde2d">-</span> numOfLeftPoints) {
                    checkPoint <span style="color:#fbde2d">=</span> xValues<span style="color:#fbde2d">.</span>get(<span style="color:#fbde2d">--</span>checkIdx);
                    <span style="color:#fbde2d">continue</span>;
                }
                <span style="color:#fbde2d">if</span>(size <span style="color:#fbde2d">-</span> numOfRightPoints <span style="color:#fbde2d">&lt;</span> numOfRightPoints) {
                    checkPoint <span style="color:#fbde2d">=</span> xValues<span style="color:#fbde2d">.</span>get(<span style="color:#fbde2d">++</span>checkIdx);
                    <span style="color:#fbde2d">continue</span>;
                }
                hasFound <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">true</span>;
            }
            <span style="color:#aeaeae">//System.out.println(checkPoint);</span>
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> item <span style="color:#fbde2d">:</span> xPositions)
                distance <span style="color:#fbde2d">+=</span> <span style="color:#fbde2d">Math</span><span style="color:#fbde2d">.</span>abs(item <span style="color:#fbde2d">-</span> checkPoint);
        }

        <span style="color:#aeaeae">// check y</span>
        {
            <span style="color:#fbde2d">int</span> checkPoint <span style="color:#fbde2d">=</span> yValues<span style="color:#fbde2d">.</span>get((yValues<span style="color:#fbde2d">.</span>size()<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)<span style="color:#fbde2d">/</span><span style="color:#d8fa3c">2</span>);
            <span style="color:#fbde2d">int</span> checkIdx <span style="color:#fbde2d">=</span> yValues<span style="color:#fbde2d">.</span>indexOf(checkPoint);
            <span style="color:#fbde2d">boolean</span> hasFound <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">false</span>;
            <span style="color:#fbde2d">int</span> numOfLeftPoints, numOfRightPoints;
            <span style="color:#fbde2d">while</span>(<span style="color:#fbde2d">!</span>hasFound) {
                numOfLeftPoints <span style="color:#fbde2d">=</span> yPositions<span style="color:#fbde2d">.</span>indexOf(checkPoint);
                <span style="color:#fbde2d">if</span>(checkIdx <span style="color:#fbde2d">==</span> yValues<span style="color:#fbde2d">.</span>size()<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)
                    numOfRightPoints <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
                <span style="color:#fbde2d">else</span>
                    numOfRightPoints <span style="color:#fbde2d">=</span> size <span style="color:#fbde2d">-</span> yPositions<span style="color:#fbde2d">.</span>indexOf(yValues<span style="color:#fbde2d">.</span>get(checkIdx<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>));

                <span style="color:#fbde2d">if</span>(numOfLeftPoints <span style="color:#fbde2d">></span> size <span style="color:#fbde2d">-</span> numOfLeftPoints) {
                    checkPoint <span style="color:#fbde2d">=</span> yValues<span style="color:#fbde2d">.</span>get(<span style="color:#fbde2d">--</span>checkIdx);
                    <span style="color:#fbde2d">continue</span>;
                }
                <span style="color:#fbde2d">if</span>(size <span style="color:#fbde2d">-</span> numOfRightPoints <span style="color:#fbde2d">&lt;</span> numOfRightPoints) {
                    checkPoint <span style="color:#fbde2d">=</span> yValues<span style="color:#fbde2d">.</span>get(<span style="color:#fbde2d">++</span>checkIdx);
                    <span style="color:#fbde2d">continue</span>;
                }
                hasFound <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">true</span>;
            }
            <span style="color:#aeaeae">//System.out.println(checkPoint);</span>
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> item <span style="color:#fbde2d">:</span> yPositions)
                distance <span style="color:#fbde2d">+=</span> <span style="color:#fbde2d">Math</span><span style="color:#fbde2d">.</span>abs(item <span style="color:#fbde2d">-</span> checkPoint);
        }
        <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(distance);
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