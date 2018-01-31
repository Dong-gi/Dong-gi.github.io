<?php
	header('Content-Type: text/html; charset=utf-8');
	setlocale(LC_TIME, "kr_KR.utf8");
	date_default_timezone_set('Asia/Seoul');

	$server_root_path = $_SERVER['DOCUMENT_ROOT'];
	include $server_root_path.'/lib/create_code_page.php';

	putHeader();
?>

<?php
	$banana = $_POST['banana'];
	$apple = $_POST['apple'];
	$ship = $_POST['ship'];
	$order = "";
	if($banana != "" && $apple != "") {
		$banana = intval($banana);
		$apple = intval($apple);
		// $order += string (X)
		$order = $order.
			"<p>고객님의 주문이 ".date('H:i, jS F Y')."에 접수되었습니다.<br/>
			고객님의 주문 정보는 아래와 같습니다.</p>
			<p>바나나 : ".$banana."개<br/>
			사과 : ".$apple."개<br/>
			배송방법 : ".$ship."</p>";
		if($ship == "delivery")
			$order = $order."배송비는 ".number_format(3300)."원입니다.";
		$order = $order."</p>\n";
		
		echo $order;
		
		// @ : 오류 억제
		$fp = @fopen("./01 result.txt", "ab");
		if(!$fp)
			$fp = fopen("./01 result.txt", "wb");
		flock($fp, LOCK_EX);
		
		if(!$fp) {
			echo "<p>주문을 저장할 수 없습니다. 다시 시도해주세요</p>";
			exit;
		}
		fwrite($fp, $order, strlen($order));
		// 큰따옴표는 내부 문자열을 파싱을 해서 뿌려주는 반면, 작은따옴표는 내부 내용을 그대로 출력.
		fwrite($fp, "───────────────\n──────────────────<br/>");
		fwrite($fp, '───────────────\n──────────────────<br/>');
		flock($fp, LOCK_UN);
		fclose($fp);
		
		echo("<p>주문을 저장하였습니다.</p>");
	} else {
		echo '
				<form action="./01 form and process.php" method="post">
					<table>
						<tr>
							<td>품목</td>
							<td>수량</td>
						</tr>
						<tr>
							<td>바나나</td>
							<td><input type="text" name="banana" maxlength="3"></td>
						</tr>
						<tr>
							<td>사과</td>
							<td><input type="text" name="apple" maxlength="3"></td>
						</tr>
						<tr>
							<td>배송방법</td>
							<td>
								<select name="ship">
								<option value="delivery" selected="selected">택배</option>
								<option value="visit">방문 수령</option>
								</select>
							</td>
						</tr>
						<tr>
							<td colspan="2" align="center"><input type="submit" value="주문하기"></td>
						</tr>
					</table>
				</form>
				<a href="./01 form and process.php?view_orders=true">주문 내역 보기</a>
				';
		$view_orders = $_GET['view_orders'];
		if($view_orders) {
			$fp = fopen("./01 result.txt", "rb");
			if(!$fp) {
				echo "<p>저장된 주문이 없습니다.</p>";
				exit;
			}
			while(!feof($fp)) {
				$text = fgets($fp);
				echo $text;
			}
			fclose($fp);
			
			$lines = file("./01 result.txt");
			$number_of_lines = count($lines);
			if($number_of_lines == 0) {
				echo "<p>저장된 주문이 없습니다.</p>";
			} else {
				for($i = 0; $i < $number_of_lines; ++$i)
					echo $i."번째 줄 : ".$lines[$i]."<br/>";
			}
		}
	}
?>

<?php
	putTitle("Code");
?>

<pre style="background:#0c1021;color:#f8f8f8">&lt;?php
    $banana <span style="color:#fbde2d">=</span> $_POST[<span style="color:#61ce3c">'banana'</span>];
    $apple <span style="color:#fbde2d">=</span> $_POST[<span style="color:#61ce3c">'apple'</span>];
    $ship <span style="color:#fbde2d">=</span> $_POST[<span style="color:#61ce3c">'ship'</span>];
    $order <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">""</span>;
<span style="color:#fbde2d">    if</span>($banana <span style="color:#fbde2d">!</span><span style="color:#fbde2d">=</span> <span style="color:#61ce3c">""</span> <span style="color:#fbde2d">&amp;&amp;</span> $apple <span style="color:#fbde2d">!</span><span style="color:#fbde2d">=</span> <span style="color:#61ce3c">""</span>) {
        $banana <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">intval</span>($banana);
        $apple <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">intval</span>($apple);
        <span style="color:#aeaeae">// $order += string (X)</span>
        $order <span style="color:#fbde2d">=</span> $order<span style="color:#fbde2d">.</span>
            <span style="color:#61ce3c">"&lt;p>고객님의 주문이 "</span><span style="color:#fbde2d">.</span><span style="color:#8da6ce">date</span>(<span style="color:#61ce3c">'H:i, jS F Y'</span>)<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"에 접수되었습니다.&lt;br/>
            고객님의 주문 정보는 아래와 같습니다.&lt;/p>
            &lt;p>바나나 : "</span><span style="color:#fbde2d">.</span>$banana<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"개&lt;br/>
            사과 : "</span><span style="color:#fbde2d">.</span>$apple<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"개&lt;br/>
            배송방법 : "</span><span style="color:#fbde2d">.</span>$ship<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;/p>"</span>;
<span style="color:#fbde2d">        if</span>($ship <span style="color:#fbde2d">==</span> <span style="color:#61ce3c">"delivery"</span>)
            $order <span style="color:#fbde2d">=</span> $order<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"배송비는 "</span><span style="color:#fbde2d">.</span><span style="color:#8da6ce">number_format</span>(<span style="color:#d8fa3c">3300</span>)<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"원입니다."</span>;
        $order <span style="color:#fbde2d">=</span> $order<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;/p><span style="color:#d8fa3c">\n</span>"</span>;
        
        <span style="color:#8da6ce">echo</span> $order;
        
        <span style="color:#aeaeae">// @ : 오류 억제</span>
        $fp <span style="color:#fbde2d">=</span> <span style="color:#fbde2d">@</span><span style="color:#8da6ce">fopen</span>(<span style="color:#61ce3c">"./01 result.txt"</span>, <span style="color:#61ce3c">"ab"</span>);
<span style="color:#fbde2d">        if</span>(<span style="color:#fbde2d">!</span>$fp)
            $fp <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">fopen</span>(<span style="color:#61ce3c">"./01 result.txt"</span>, <span style="color:#61ce3c">"wb"</span>);
        <span style="color:#8da6ce">flock</span>($fp, <span style="color:#8da6ce">LOCK_EX</span>);
        
<span style="color:#fbde2d">        if</span>(<span style="color:#fbde2d">!</span>$fp) {
            <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;p>주문을 저장할 수 없습니다. 다시 시도해주세요&lt;/p>"</span>;
<span style="color:#fbde2d">            exit</span>;
        }
        <span style="color:#8da6ce">fwrite</span>($fp, $order, <span style="color:#8da6ce">strlen</span>($order));
        <span style="color:#8da6ce">fwrite</span>($fp, <span style="color:#61ce3c">"<span style="color:#d8fa3c">\n</span>"</span>);
        <span style="color:#8da6ce">flock</span>($fp, <span style="color:#8da6ce">LOCK_UN</span>);
        <span style="color:#8da6ce">fclose</span>($fp);
        
        <span style="color:#8da6ce">echo</span>(<span style="color:#61ce3c">"&lt;p>주문을 저장하였습니다.&lt;/p>"</span>);
    }<span style="color:#fbde2d"> else</span> {
        <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">'
                &lt;form action="./01 form and process.php" method="post">
                    &lt;table>
                        &lt;tr>
                            &lt;td>품목&lt;/td>
                            &lt;td>수량&lt;/td>
                        &lt;/tr>
                        &lt;tr>
                            &lt;td>바나나&lt;/td>
                            &lt;td>&lt;input type="text" name="banana" maxlength="3">&lt;/td>
                        &lt;/tr>
                        &lt;tr>
                            &lt;td>사과&lt;/td>
                            &lt;td>&lt;input type="text" name="apple" maxlength="3">&lt;/td>
                        &lt;/tr>
                        &lt;tr>
                            &lt;td>배송방법&lt;/td>
                            &lt;td>
                                &lt;select name="ship">
                                &lt;option value="delivery" selected="selected">택배&lt;/option>
                                &lt;option value="visit">방문 수령&lt;/option>
                                &lt;/select>
                            &lt;/td>
                        &lt;/tr>
                        &lt;tr>
                            &lt;td colspan="2" align="center">&lt;input type="submit" value="주문하기">&lt;/td>
                        &lt;/tr>
                    &lt;/table>
                &lt;/form>
                &lt;a href="./01 form and process.php?view_orders=true">주문 내역 보기&lt;/a>
                '</span>;
        $view_orders <span style="color:#fbde2d">=</span> $_GET[<span style="color:#61ce3c">'view_orders'</span>];
<span style="color:#fbde2d">        if</span>($view_orders) {
            $fp <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">fopen</span>(<span style="color:#61ce3c">"./01 result.txt"</span>, <span style="color:#61ce3c">"rb"</span>);
<span style="color:#fbde2d">            if</span>(<span style="color:#fbde2d">!</span>$fp) {
                <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;p>저장된 주문이 없습니다.&lt;/p>"</span>;
<span style="color:#fbde2d">                exit</span>;
            }
<span style="color:#fbde2d">            while</span>(<span style="color:#fbde2d">!</span><span style="color:#8da6ce">feof</span>($fp)) {
                $text <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">fgets</span>($fp);
                <span style="color:#8da6ce">echo</span> $text;
            }
            <span style="color:#8da6ce">fclose</span>($fp);
            
            $lines <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">file</span>(<span style="color:#61ce3c">"./01 result.txt"</span>);
            $number_of_lines <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">count</span>($lines);
<span style="color:#fbde2d">            if</span>($number_of_lines <span style="color:#fbde2d">==</span> <span style="color:#d8fa3c">0</span>) {
                <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;p>저장된 주문이 없습니다.&lt;/p>"</span>;
<span style="color:#fbde2d">                exit</span>;
            }
<span style="color:#fbde2d">            for</span>($i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; $i <span style="color:#fbde2d">&lt;</span> $number_of_lines; <span style="color:#fbde2d">++</span>$i)
                <span style="color:#8da6ce">echo</span> $i<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"번째 줄 : "</span><span style="color:#fbde2d">.</span>$lines[$i]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
        }
    }
?>
</pre>

<?php
	putFooter();
?>