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
<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">Main</span> {

    <span style="color:#fbde2d">public</span> <span style="color:#fbde2d">static</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">main</span>(<span style="color:#fbde2d">String</span>[] args) {
        <span style="color:#fbde2d">int</span> var1 <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>;
        <span style="color:#fbde2d">switch</span>(var1)
        {
        <span style="color:#fbde2d">case</span> <span style="color:#d8fa3c">0</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Zero"</span>);
            <span style="color:#fbde2d">break</span>;
        <span style="color:#fbde2d">default</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Non Zero"</span>);
        }

        <span style="color:#fbde2d">char</span> var2 <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">'0'</span>;
        <span style="color:#fbde2d">switch</span>(var2)
        {
        <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'0'</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Zero Character"</span>);
            <span style="color:#fbde2d">break</span>;
        <span style="color:#fbde2d">case</span> <span style="color:#d8fa3c">0</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Zero Number"</span>);
            <span style="color:#fbde2d">break</span>;
        <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">'<span style="color:#d8fa3c">\u</span>FFFF'</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"What's This?"</span>);
            <span style="color:#fbde2d">break</span>;
        }

        <span style="color:#fbde2d">String</span> var3 <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"asdf"</span>;
        <span style="color:#fbde2d">switch</span>(var3)
        {
        <span style="color:#fbde2d">case</span> <span style="color:#61ce3c">"0"</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"Zero String"</span>);
            <span style="color:#fbde2d">break</span>;
        <span style="color:#fbde2d">default</span><span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"What's This? : "</span> <span style="color:#fbde2d">+</span> var3);
        }

        outer<span style="color:#fbde2d">:</span>
            <span style="color:#fbde2d">for</span>( ; ; ) {
                inner<span style="color:#fbde2d">:</span>
                    <span style="color:#fbde2d">for</span>( ; ; ) {
                        <span style="color:#fbde2d">break</span> outer;
                    }
            }
        <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>println(<span style="color:#61ce3c">"이중 반복문 종료"</span>);
        
        <span style="color:#fbde2d">int</span>[] arr <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">new</span> <span style="color:#fbde2d">int</span>[] {<span style="color:#d8fa3c">1</span>, <span style="color:#d8fa3c">2</span>, <span style="color:#d8fa3c">3</span>, <span style="color:#d8fa3c">4</span>};
        <span style="color:#fbde2d">for</span>(<span style="color:#fbde2d">int</span> i <span style="color:#fbde2d">:</span> arr) {
            <span style="color:#fbde2d">System</span><span style="color:#fbde2d">.</span>out<span style="color:#fbde2d">.</span>print(<span style="color:#61ce3c">" "</span> <span style="color:#fbde2d">+</span> i);
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