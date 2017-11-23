<?php
    header('Content-Type: text/html; charset=utf-8');
    setlocale(LC_TIME, "kr_KR.utf8");
    date_default_timezone_set('Asia/Seoul');

    $server_root_path = $_SERVER['DOCUMENT_ROOT'];
    include $server_root_path.'/lib/create_code_page.php';

    putHeader();
?>

<?php
	$email = $_POST['email'];
	$message = $_POST['message'];
	
	$to = "hi.donggi@gmail.com";
	$subject = "메시지 도착";
	$mailcontent = "email : ".$email."\n".
					"message : ".$message."\n";
	$from = "From: master@wiz.pe.hu";
	
	if(isset($email) && isset($message))
	{
		mail($to, $subject, $mailcontent, $from);
		echo "전송 완료<br/><br/>
			본문 :<br/>".
			nl2br($mailcontent);
	}
	else
	{
		echo '
				<form action="./03 string.php" method="post">
					<p>메일 주소 : <input type="text" name="email" size="40"/></p>
					<p>내용 : 
					<br/><textarea name="message" rows="10" cols="40" wrap="virtual"/></textarea></p>
					<input type="submit" value="전송"/>
				</form>
			';
	}
	
	$str = " asdlfijwo eoifqwne ofi \tasldjfo\naosidjf ";
	print($str);
	trim($str);
	echo(sprintf("%s\n%s", $str, $str));
	// 변환 명세 conversion specification : %['padding_character][-][width][.precision]type
	// b : 이진수, c : 캐릭터, d : 십진수, f : 실수, o : 8진수, s : 문자열, u : 16진수, x : 부호 없는 10진수, X : 16진수
	// 예. % 10.2f
	printf("%1\$.4f, %1\$.3f, %2\$.2f\n", 1.2345, 2.34);
?>

<?php
    putTitle("Code");
?>

<pre style="background:#0c1021;color:#f8f8f8">&lt;?php
    $email <span style="color:#fbde2d">=</span> $_POST[<span style="color:#61ce3c">'email'</span>];
    $message <span style="color:#fbde2d">=</span> $_POST[<span style="color:#61ce3c">'message'</span>];
    
    $to <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"hi.donggi@gmail.com"</span>;
    $subject <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"메시지 도착"</span>;
    $mailcontent <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"email : "</span><span style="color:#fbde2d">.</span>$email<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"<span style="color:#d8fa3c">\n</span>"</span><span style="color:#fbde2d">.</span>
                    <span style="color:#61ce3c">"message : "</span><span style="color:#fbde2d">.</span>$message<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"<span style="color:#d8fa3c">\n</span>"</span>;
    $from <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"From: master@wiz.pe.hu"</span>;
    
<span style="color:#fbde2d">    if</span>(<span style="color:#8da6ce">isset</span>($email) <span style="color:#fbde2d">&amp;&amp;</span> <span style="color:#8da6ce">isset</span>($message))
    {
        <span style="color:#8da6ce">mail</span>($to, $subject, $mailcontent, $from);
        <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"전송 완료&lt;br/>&lt;br/>
            본문 :&lt;br/>"</span><span style="color:#fbde2d">.</span>
            <span style="color:#8da6ce">nl2br</span>($mailcontent);
    }
<span style="color:#fbde2d">    else</span>
    {
        <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">'
                &lt;form action="./03 string.php" method="post">
                    &lt;p>메일 주소 : &lt;input type="text" name="email" size="40"/>&lt;/p>
                    &lt;p>내용 : 
                    &lt;br/>&lt;textarea name="message" rows="10" cols="40" wrap="virtual"/>&lt;/textarea>&lt;/p>
                    &lt;input type="submit" value="전송"/>
                &lt;/form>
            '</span>;
    }
    
    $str <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">" asdlfijwo eoifqwne ofi <span style="color:#d8fa3c">\t</span>asldjfo<span style="color:#d8fa3c">\n</span>aosidjf "</span>;
    <span style="color:#8da6ce">print</span>($str);
    <span style="color:#8da6ce">trim</span>($str);
    <span style="color:#8da6ce">echo</span>(<span style="color:#8da6ce">sprintf</span>(<span style="color:#61ce3c">"%s<span style="color:#d8fa3c">\n</span>%s"</span>, $str, $str));
    <span style="color:#aeaeae">// 변환 명세 conversion specification : %['padding_character][-][width][.precision]type</span>
    <span style="color:#aeaeae">// b : 이진수, c : 캐릭터, d : 십진수, f : 실수, o : 8진수, s : 문자열, u : 16진수, x : 부호 없는 10진수, X : 16진수</span>
    <span style="color:#aeaeae">// 예. % 10.2f</span>
    <span style="color:#8da6ce">printf</span>(<span style="color:#61ce3c">"%1<span style="color:#d8fa3c">\$</span>.4f, %1<span style="color:#d8fa3c">\$</span>.3f, %2<span style="color:#d8fa3c">\$</span>.2f<span style="color:#d8fa3c">\n</span>"</span>, <span style="color:#d8fa3c">1.2345</span>, <span style="color:#d8fa3c">2.34</span>);
?>
</pre>

<?php
    putFooter();
?>

