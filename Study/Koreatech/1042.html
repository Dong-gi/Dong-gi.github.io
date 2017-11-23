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
        <span style="color:#fbde2d">Scanner</span> in <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> size <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();

        <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> peaks <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;></span>();
        <span style="color:#fbde2d">int</span> before <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
        <span style="color:#fbde2d">boolean</span> findSmall <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">true</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">1</span>; i <span style="color:#fbde2d">&lt;</span> size; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">int</span> current <span style="color:#fbde2d">=</span> in<span style="color:#fbde2d">.</span>nextInt();
            <span style="color:#aeaeae">//System.out.printf("%d, %d, %s\n", before, current, findSmall);</span>
            <span style="color:#fbde2d">if</span>(findSmall) {
                <span style="color:#fbde2d">if</span>(current <span style="color:#fbde2d">></span> before) {
                    peaks<span style="color:#fbde2d">.</span>add(before);
                    findSmall <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">false</span>;
                    <span style="color:#fbde2d">if</span>(i <span style="color:#fbde2d">==</span> size<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>) {
                        peaks<span style="color:#fbde2d">.</span>add(current);
                    }
                }
            } <span style="color:#fbde2d">else</span> {
                <span style="color:#fbde2d">if</span>(current <span style="color:#fbde2d">&lt;</span> before) {
                    peaks<span style="color:#fbde2d">.</span>add(before);
                    findSmall <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">true</span>;
                } <span style="color:#fbde2d">else</span> <span style="color:#fbde2d">if</span>(i <span style="color:#fbde2d">==</span> size<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>) {
                    peaks<span style="color:#fbde2d">.</span>add(current);
                }
            }
            before <span style="color:#fbde2d">=</span> current;
        }

        <span style="color:#aeaeae">/*for(int i : peaks) {
            System.out.print(i + " ");
        }
        System.out.println();*/</span>

        <span style="color:#aeaeae">// reduce peaks</span>
        <span style="color:#aeaeae">// 1 4 2 9 --1번 거래 가능--> (1 9)</span>
        <span style="color:#aeaeae">// 1 4 2 6 3 9 --2번 거래 가능--> (1 6), (3 9)</span>
        <span style="color:#aeaeae">// 손실 비교 : 4-2 &lt; 6-3</span>
        <span style="color:#fbde2d">int</span> dealSize <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span>;
        <span style="color:#fbde2d">Integer</span>[] peakArr <span style="color:#fbde2d">=</span> peaks<span style="color:#fbde2d">.</span>toArray(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[<span style="color:#d8fa3c">0</span>]);
        <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>[]></span> midPeaks <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;></span>();
        <span style="color:#fbde2d">HashSet&lt;<span style="color:#fbde2d">Integer</span>></span> removedMidPeakIdxSet <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">HashSet&lt;></span>();
        <span style="color:#fbde2d">if</span>(peakArr<span style="color:#fbde2d">.</span>length<span style="color:#fbde2d">/</span><span style="color:#d8fa3c">2</span> <span style="color:#fbde2d">></span> dealSize) {
            <span style="color:#fbde2d">int</span> midPeakCount <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">3</span> <span style="color:#fbde2d">&lt;</span> peakArr<span style="color:#fbde2d">.</span>length; i<span style="color:#fbde2d">+=</span><span style="color:#d8fa3c">2</span>) {
                <span style="color:#fbde2d">if</span>(peakArr[i] <span style="color:#fbde2d">&lt;</span> peakArr[i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">2</span>] <span style="color:#fbde2d">&amp;&amp;</span> peakArr[i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>] <span style="color:#fbde2d">&lt;</span> peakArr[i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">3</span>]) {
                    midPeakCount <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">1</span>;
                    midPeaks<span style="color:#fbde2d">.</span>add(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[] {i, peakArr[i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">1</span>]<span style="color:#fbde2d">-</span>peakArr[i<span style="color:#fbde2d">+</span><span style="color:#d8fa3c">2</span>]});
                }
            }
            <span style="color:#fbde2d">Collections</span><span style="color:#fbde2d">.</span>sort(midPeaks, (<span style="color:#fbde2d">Integer</span>[] arg0, <span style="color:#fbde2d">Integer</span>[] arg1) <span style="color:#fbde2d">-</span><span style="color:#fbde2d">></span> {
                    <span style="color:#fbde2d">return</span> arg0[<span style="color:#d8fa3c">1</span>]<span style="color:#fbde2d">.</span>compareTo(arg1[<span style="color:#d8fa3c">1</span>]);
                });
            <span style="color:#aeaeae">/*for(Integer[] i : midPeaks) {
                System.out.print(i[1] + " ");
            }
            System.out.println();*/</span>
            <span style="color:#fbde2d">int</span> removeSize <span style="color:#fbde2d">=</span> peakArr<span style="color:#fbde2d">.</span>length<span style="color:#fbde2d">/</span><span style="color:#d8fa3c">2</span> <span style="color:#fbde2d">-</span> dealSize;
            <span style="color:#fbde2d">int</span> removeMidPeakSize <span style="color:#fbde2d">=</span> midPeakCount <span style="color:#fbde2d">></span> removeSize<span style="color:#fbde2d">?</span> removeSize <span style="color:#fbde2d">:</span> midPeakCount;
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> removeMidPeakSize; <span style="color:#fbde2d">++</span>i) {
                removedMidPeakIdxSet<span style="color:#fbde2d">.</span>add(midPeaks<span style="color:#fbde2d">.</span>get(i)[<span style="color:#d8fa3c">0</span>]);
            }
        }

        <span style="color:#aeaeae">// 상/하한가당 이득을 정렬</span>
        <span style="color:#fbde2d">ArrayList&lt;<span style="color:#fbde2d">Integer</span>></span> benefits <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">ArrayList&lt;></span>();
        <span style="color:#fbde2d">int</span> limit <span style="color:#fbde2d">=</span> peakArr<span style="color:#fbde2d">.</span>length<span style="color:#fbde2d">/</span><span style="color:#d8fa3c">2</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> limit; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">int</span> buyIdx <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span><span style="color:#fbde2d">*</span>i;
            <span style="color:#fbde2d">int</span> sellIdx <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">2</span><span style="color:#fbde2d">*</span>i <span style="color:#fbde2d">+</span> <span style="color:#d8fa3c">1</span>;
            <span style="color:#fbde2d">try</span> {
                <span style="color:#fbde2d">if</span>(removedMidPeakIdxSet<span style="color:#fbde2d">.</span>contains(buyIdx<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span>)) {
                    <span style="color:#fbde2d">continue</span>;
                }
                <span style="color:#fbde2d">while</span>(removedMidPeakIdxSet<span style="color:#fbde2d">.</span>contains(sellIdx<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">1</span>)) {
                    sellIdx <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">2</span>;
                    i <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">1</span>;
                }
                benefits<span style="color:#fbde2d">.</span>add(peakArr[sellIdx] <span style="color:#fbde2d">-</span> peakArr[buyIdx]);
            } <span style="color:#fbde2d">catch</span>(<span style="color:#fbde2d">Exception</span> e) {}
        }
        <span style="color:#fbde2d">Collections</span><span style="color:#fbde2d">.</span>sort(benefits, (<span style="color:#fbde2d">Integer</span> o1, <span style="color:#fbde2d">Integer</span> o2) <span style="color:#fbde2d">-</span><span style="color:#fbde2d">></span> {<span style="color:#fbde2d">return</span> o2<span style="color:#fbde2d">.</span>compareTo(o1);});

        <span style="color:#fbde2d">int</span> sum <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> dealSize; <span style="color:#fbde2d">++</span>i) {
            sum <span style="color:#fbde2d">+=</span> benefits<span style="color:#fbde2d">.</span>get(i);
        }
        <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(sum);
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