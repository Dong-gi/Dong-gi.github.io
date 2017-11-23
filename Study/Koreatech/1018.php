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

    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">char</span>[] arr1, arr2;
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">int</span> distance;
    
    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">Scanner</span> scanner <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Scanner</span>(<span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>in);
        <span style="color:#fbde2d">int</span> testCase <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>nextInt();
        <span style="color:#fbde2d">while</span>(testCase<span style="color:#fbde2d">--</span> <span style="color:#fbde2d">></span> <span style="color:#d8fa3c">0</span>) {
            distance <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">Integer</span><span style="color:#d8fa3c"><span style="color:#fbde2d">.</span>MAX_VALUE</span>;
            arr1 <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>next()<span style="color:#fbde2d">.</span>toCharArray();
            arr2 <span style="color:#fbde2d">=</span> scanner<span style="color:#fbde2d">.</span>next()<span style="color:#fbde2d">.</span>toCharArray();
            
            <span style="color:#fbde2d">int</span> size <span style="color:#fbde2d">=</span> arr2<span style="color:#fbde2d">.</span>length <span style="color:#fbde2d">-</span> arr1<span style="color:#fbde2d">.</span>length; 
            <span style="color:#fbde2d">HashSet&lt;<span style="color:#fbde2d">Integer</span>></span> indexSet <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">HashSet&lt;></span>();
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> arr1<span style="color:#fbde2d">.</span>length; <span style="color:#fbde2d">++</span>i) {
                <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> j <span style="color:#fbde2d">=</span> i; j <span style="color:#fbde2d">&lt;=</span> i <span style="color:#fbde2d">+</span> size; <span style="color:#fbde2d">++</span>j) {
                    <span style="color:#fbde2d">if</span>(arr1[i] <span style="color:#fbde2d">==</span> arr2[j]) {
                        indexSet<span style="color:#fbde2d">.</span>add(j<span style="color:#fbde2d">-</span>i);
                    }
                }
            }
            <span style="color:#fbde2d">if</span>(indexSet<span style="color:#fbde2d">.</span>isEmpty()) {
                <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(arr1<span style="color:#fbde2d">.</span>length);
                <span style="color:#fbde2d">continue</span>;
            }
            <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> from <span style="color:#fbde2d">:</span> indexSet<span style="color:#fbde2d">.</span>toArray(<span style="color:#fbde2d">new</span> <span style="color:#fbde2d">Integer</span>[<span style="color:#d8fa3c">0</span>])) {
                distance(from);
            }
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(distance);
        }
    }
    
    <span style="color:#fbde2d">private</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">distance</span>(<span style="color:#fbde2d">int</span> from) {
        <span style="color:#fbde2d">int</span> result <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; i <span style="color:#fbde2d">&lt;</span> arr1<span style="color:#fbde2d">.</span>length; <span style="color:#fbde2d">++</span>i) {
            <span style="color:#fbde2d">if</span>(arr1[i] <span style="color:#fbde2d">!=</span> arr2[i<span style="color:#fbde2d">+</span>from]) {
                result <span style="color:#fbde2d">+=</span> <span style="color:#d8fa3c">1</span>;
                <span style="color:#fbde2d">if</span>(result <span style="color:#fbde2d">>=</span> distance) {
                    <span style="color:#fbde2d">return</span>;
                }
            }
        }
        <span style="color:#fbde2d">if</span>(distance <span style="color:#fbde2d">></span> result) {
            distance <span style="color:#fbde2d">=</span> result;
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