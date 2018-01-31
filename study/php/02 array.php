<?php
    header('Content-Type: text/html; charset=utf-8');
    setlocale(LC_TIME, "kr_KR.utf8");
    date_default_timezone_set('Asia/Seoul');

    $server_root_path = $_SERVER['DOCUMENT_ROOT'];
    include $server_root_path.'/lib/create_code_page.php';

	putHeader();
?>

<?php
	$arr1 = array("l", "a", "c", "b");
	$arr1[4] = "k"; // 동적 배열
	array_push($arr1, "z");
	// array_pop() : 배열 첫 원소를 반환하고 제거한다.
	sort($arr1); // 'A' < 'Z' < 'a' < 'z'
	foreach($arr1 as $text)
		echo $text." ";
	echo "<br/>";
	
	$arr1 = range(3, 30, 3);
	shuffle($arr1);
	// echo $arr1; (X)
	$size = count($arr1);
	for($i = 0; $i < $size; ++$i)
		echo $arr1[$i]." ";
	echo "<br/>";

	$arr1 = range('a', 'z');
	$arr1 = array_reverse($arr1);
	$size = count($arr1);
	for($i = 0; $i < $size; ++$i)
		echo $arr1[$i]." ";
	echo "<br/>";

	$arr1 = array(
		"l" => "absd",
		"a" => "bs",
		"b" => "weica");
	$arr1["key"] = "element"; // key = index
	foreach($arr1 as $key => $value)
		echo $key." => ".$value."<br/>";
	echo "1<br/>";

	// each()는 현재 요소의 키와 값을 배열로 반환하고 다음 요소를 가리킨다. 배열 끝에 도달하면 종료.
	reset($arr1); // 가리키는 위치를 처음으로
	while($element = each($arr1))
		echo $element["key"]." => ".$element["value"]."<br/>";
	echo "2<br/>";

	asort($arr1); // sort by value
	reset($arr1);
	while($element = each($arr1))
		echo $element["key"]." => ".$element["value"]."<br/>";
	echo "3<br/>";

	ksort($arr1); // sort by key
	while(list($key, $value) = each($arr1))
		echo $key." => ".$value."<br/>";
	echo "4<br/>";
	// 내림차순 정렬 : rsort(), arsort(), krsort()

	function compare($x, $y)
	{
		return strlen($x) - strlen($y);
	}
	uasort($arr1, "compare"); // 사용자 정의 정렬
	// usort(), uasort(), uksort() : 데이터가 복잡한 타입일 때.
	while(list($key, $value) = each($arr1))
		echo $key." => ".$value."<br/>";
	echo "5<br/>";
	
	extract($arr1, EXTR_PREFIX_ALL, "prefix");
	echo "prefix_a : ".$prefix_a." prefix_b : ".$prefix_b."<br/>";
	
	/* 배열 연산
	 * $a + $b : 합집합. $b가 $a의 뒤에 붙는다. 키가 같으면 건너뛰기.
	 * $a == $b : 동일 요소들을 포함
	 * $a === $b : 순서까지 동일
	 * !=, <>, !==
	 */
	$arr2 = array(array(1, 2, 3), arr1);
	 
	$str = "1\t2\t3\t4\t5\t6\t7\t8";
	$numbers = explode("\t", $str);
	
	function my_func(&$value, $key, $userdata)
	{
		$value .= $userdata;
		echo $value." ";
	}
	array_walk($numbers, "my_func", "Hello");
?>

<?php
    putTitle("Code");
?>

<pre style="background:#0c1021;color:#f8f8f8">&lt;?php
    $arr1 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">array</span>(<span style="color:#61ce3c">"l"</span>, <span style="color:#61ce3c">"a"</span>, <span style="color:#61ce3c">"c"</span>, <span style="color:#61ce3c">"b"</span>);
    $arr1[<span style="color:#d8fa3c">4</span>] <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"k"</span>; <span style="color:#aeaeae">// 동적 배열</span>
    <span style="color:#8da6ce">array_push</span>($arr1, <span style="color:#61ce3c">"z"</span>);
    <span style="color:#aeaeae">// array_pop() : 배열 첫 원소를 반환하고 제거한다.</span>
    <span style="color:#8da6ce">sort</span>($arr1); <span style="color:#aeaeae">// 'A' &lt; 'Z' &lt; 'a' &lt; 'z'</span>
<span style="color:#fbde2d">    foreach</span>($arr1 <span style="color:#fbde2d">as</span> $text)
        <span style="color:#8da6ce">echo</span> $text<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" "</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;br/>"</span>;
    
    $arr1 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">range</span>(<span style="color:#d8fa3c">3</span>, <span style="color:#d8fa3c">30</span>, <span style="color:#d8fa3c">3</span>);
    <span style="color:#8da6ce">shuffle</span>($arr1);
    <span style="color:#aeaeae">// echo $arr1; (X)</span>
    $size <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">count</span>($arr1);
<span style="color:#fbde2d">    for</span>($i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; $i <span style="color:#fbde2d">&lt;</span> $size; <span style="color:#fbde2d">++</span>$i)
        <span style="color:#8da6ce">echo</span> $arr1[$i]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" "</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;br/>"</span>;

    $arr1 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">range</span>(<span style="color:#61ce3c">'a'</span>, <span style="color:#61ce3c">'z'</span>);
    $arr1 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">array_reverse</span>($arr1);
    $size <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">count</span>($arr1);
<span style="color:#fbde2d">    for</span>($i <span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>; $i <span style="color:#fbde2d">&lt;</span> $size; <span style="color:#fbde2d">++</span>$i)
        <span style="color:#8da6ce">echo</span> $arr1[$i]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" "</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"&lt;br/>"</span>;

    $arr1 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">array</span>(
        <span style="color:#61ce3c">"l"</span> <span style="color:#fbde2d">=></span> <span style="color:#61ce3c">"absd"</span>,
        <span style="color:#61ce3c">"a"</span> <span style="color:#fbde2d">=></span> <span style="color:#61ce3c">"bs"</span>,
        <span style="color:#61ce3c">"b"</span> <span style="color:#fbde2d">=></span> <span style="color:#61ce3c">"weica"</span>);
    $arr1[<span style="color:#61ce3c">"key"</span>] <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"element"</span>; <span style="color:#aeaeae">// key = index</span>
<span style="color:#fbde2d">    foreach</span>($arr1 <span style="color:#fbde2d">as</span> $key <span style="color:#fbde2d">=></span> $value)
        <span style="color:#8da6ce">echo</span> $key<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" => "</span><span style="color:#fbde2d">.</span>$value<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"1&lt;br/>"</span>;

    <span style="color:#aeaeae">// each()는 현재 요소의 키와 값을 배열로 반환하고 다음 요소를 가리킨다. 배열 끝에 도달하면 종료.</span>
    <span style="color:#8da6ce">reset</span>($arr1); <span style="color:#aeaeae">// 가리키는 위치를 처음으로</span>
<span style="color:#fbde2d">    while</span>($element <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">each</span>($arr1))
        <span style="color:#8da6ce">echo</span> $element[<span style="color:#61ce3c">"key"</span>]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" => "</span><span style="color:#fbde2d">.</span>$element[<span style="color:#61ce3c">"value"</span>]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"2&lt;br/>"</span>;

    <span style="color:#8da6ce">asort</span>($arr1); <span style="color:#aeaeae">// sort by value</span>
    <span style="color:#8da6ce">reset</span>($arr1);
<span style="color:#fbde2d">    while</span>($element <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">each</span>($arr1))
        <span style="color:#8da6ce">echo</span> $element[<span style="color:#61ce3c">"key"</span>]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" => "</span><span style="color:#fbde2d">.</span>$element[<span style="color:#61ce3c">"value"</span>]<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"3&lt;br/>"</span>;

    <span style="color:#8da6ce">ksort</span>($arr1); <span style="color:#aeaeae">// sort by key</span>
<span style="color:#fbde2d">    while</span>(<span style="color:#8da6ce">list</span>($key, $value) <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">each</span>($arr1))
        <span style="color:#8da6ce">echo</span> $key<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" => "</span><span style="color:#fbde2d">.</span>$value<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"4&lt;br/>"</span>;
    <span style="color:#aeaeae">// 내림차순 정렬 : rsort(), arsort(), krsort()</span>

    <span style="color:#fbde2d">function</span> <span style="color:#ff6400">compare</span>($x, $y)
    {
<span style="color:#fbde2d">        return</span> <span style="color:#8da6ce">strlen</span>($x) <span style="color:#fbde2d">-</span> <span style="color:#8da6ce">strlen</span>($y);
    }
    <span style="color:#8da6ce">uasort</span>($arr1, <span style="color:#61ce3c">"compare"</span>); <span style="color:#aeaeae">// 사용자 정의 정렬</span>
    <span style="color:#aeaeae">// usort(), uasort(), uksort() : 데이터가 복잡한 타입일 때.</span>
<span style="color:#fbde2d">    while</span>(<span style="color:#8da6ce">list</span>($key, $value) <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">each</span>($arr1))
        <span style="color:#8da6ce">echo</span> $key<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" => "</span><span style="color:#fbde2d">.</span>$value<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"5&lt;br/>"</span>;
    
    <span style="color:#8da6ce">extract</span>($arr1, <span style="color:#8da6ce">EXTR_PREFIX_ALL</span>, <span style="color:#61ce3c">"prefix"</span>);
    <span style="color:#8da6ce">echo</span> <span style="color:#61ce3c">"prefix_a : "</span><span style="color:#fbde2d">.</span>$prefix_a<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" prefix_b : "</span><span style="color:#fbde2d">.</span>$prefix_b<span style="color:#fbde2d">.</span><span style="color:#61ce3c">"&lt;br/>"</span>;
    
    <span style="color:#aeaeae">/* 배열 연산
     * $a + $b : 합집합. $b가 $a의 뒤에 붙는다. 키가 같으면 건너뛰기.
     * $a == $b : 동일 요소들을 포함
     * $a === $b : 순서까지 동일
     * !=, &lt;>, !==
     */</span>
    $arr2 <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">array</span>(<span style="color:#8da6ce">array</span>(<span style="color:#d8fa3c">1</span>, <span style="color:#d8fa3c">2</span>, <span style="color:#d8fa3c">3</span>), <span style="color:#d8fa3c">arr1</span>);
     
    $str <span style="color:#fbde2d">=</span> <span style="color:#61ce3c">"1<span style="color:#d8fa3c">\t</span>2<span style="color:#d8fa3c">\t</span>3<span style="color:#d8fa3c">\t</span>4<span style="color:#d8fa3c">\t</span>5<span style="color:#d8fa3c">\t</span>6<span style="color:#d8fa3c">\t</span>7<span style="color:#d8fa3c">\t</span>8"</span>;
    $numbers <span style="color:#fbde2d">=</span> <span style="color:#8da6ce">explode</span>(<span style="color:#61ce3c">"<span style="color:#d8fa3c">\t</span>"</span>, $str);
    
    <span style="color:#fbde2d">function</span> <span style="color:#ff6400">my_func</span>(<span style="color:#fbde2d">&amp;</span>$value, $key, $userdata)
    {
        $value <span style="color:#fbde2d">.=</span> $userdata;
        <span style="color:#8da6ce">echo</span> $value<span style="color:#fbde2d">.</span><span style="color:#61ce3c">" "</span>;
    }
    <span style="color:#8da6ce">array_walk</span>($numbers, <span style="color:#61ce3c">"my_func"</span>, <span style="color:#61ce3c">"Hello"</span>);
?>
</pre>

<?php
    putFooter();
?>

