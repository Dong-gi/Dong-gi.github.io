<?php
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/create_code_page.php';

putHeader();
putTitle("DOM 관련 함수들 : 기존 객체 변경");
?>

<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
<script>
	function sleep(ms) {
		return new Promise(resolve => setTimeout(resolve, ms));
	}
</script>

<h3>3수준 제목</h3>
<h3>3수준 제목</h3>
<p><button onclick="func1()">결과 확인 1</button></p>
<script>
	async function func1() {
		jQuery('h3').each(function (index, element) {
			jQuery(this).addClass('Hello' + index);
		});
		console.log(jQuery('h3').attr('class'));
		jQuery('h3').attr('style', 'color:blue;');
		// 이전 속성은 없어진다.
		await sleep(1000);
		jQuery('h3').attr({ 'style': 'background-color:yellow;' });
		console.log(jQuery('h3').css('color'));
		await sleep(1000);
		jQuery('h3').removeAttr('style');
		
		await sleep(1000);
		console.log(jQuery('h3').html());
		console.log(jQuery('h3').text());
		await sleep(1000);
		jQuery('h3').remove();
		// empty() : 후손을 제거
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8">jQuery(<span style="color:#61ce3c">'h3'</span>).each(<span style="color:#fbde2d">function</span> (index, element) {
    jQuery(this).addClass(<span style="color:#61ce3c">'Hello'</span> <span style="color:#fbde2d">+</span> index);
});
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(jQuery(<span style="color:#61ce3c">'h3'</span>).attr(<span style="color:#61ce3c">'class'</span>));
jQuery(<span style="color:#61ce3c">'h3'</span>).attr(<span style="color:#61ce3c">'style'</span>, <span style="color:#61ce3c">'color:blue;'</span>);
<span style="color:#aeaeae">// 이전 속성은 없어진다.</span>
await sleep(<span style="color:#d8fa3c">1000</span>);
jQuery(<span style="color:#61ce3c">'h3'</span>).attr({ <span style="color:#61ce3c">'style'</span>: <span style="color:#61ce3c">'background-color:yellow;'</span> });
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(jQuery(<span style="color:#61ce3c">'h3'</span>).css(<span style="color:#61ce3c">'color'</span>));
await sleep(<span style="color:#d8fa3c">1000</span>);
jQuery(<span style="color:#61ce3c">'h3'</span>).removeAttr(<span style="color:#61ce3c">'style'</span>);

await sleep(<span style="color:#d8fa3c">1000</span>);
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(jQuery(<span style="color:#61ce3c">'h3'</span>).html());
<span style="color:#ff6400">console</span><span style="color:#8da6ce">.log</span>(jQuery(<span style="color:#61ce3c">'h3'</span>).<span style="color:#8da6ce">text</span>());
await sleep(<span style="color:#d8fa3c">1000</span>);
jQuery(<span style="color:#61ce3c">'h3'</span>).<span style="color:#8da6ce">remove</span>();
<span style="color:#aeaeae">// empty() : 후손을 제거</span>
</pre>

<?php
putTitle("새 객체 생성");
?>

<div id='div1'></div>
<div id='div2'></div>
<p><button onclick="func2()">결과 확인 2</button></p>
<script>
	async function func2() {
		jQuery('<h3></h3>').text('새로운 3수준 제목 1').appendTo(jQuery('#div1'));
		jQuery('<h3>새로운 3수준 제목 2</h3>').appendTo(jQuery('#div1'));
		// prependTo(), insertAfter(), insertBefore()
		// append(), prepend(), after(), before()
		setInterval(function () {
			if (jQuery('#div2 h3').length != 0) {
				jQuery('#div1').append(jQuery('#div2 h3')[0]);
			} else {
				jQuery('#div2').append(jQuery('h3').eq(-2));
			}
		}, 300);
		jQuery('#div1').clone().attr('id', 'div3').prependTo(jQuery('#div1'));
	}
</script>

<pre style="background:#0c1021;color:#f8f8f8">jQuery(<span style="color:#61ce3c">'&lt;h3>&lt;/h3>'</span>).<span style="color:#8da6ce">text</span>(<span style="color:#61ce3c">'새로운 3수준 제목 1'</span>).appendTo(jQuery(<span style="color:#61ce3c">'#div1'</span>));
jQuery(<span style="color:#61ce3c">'&lt;h3>새로운 3수준 제목 2&lt;/h3>'</span>).appendTo(jQuery(<span style="color:#61ce3c">'#div1'</span>));
<span style="color:#aeaeae">// prependTo(), insertAfter(), insertBefore()</span>
<span style="color:#aeaeae">// append(), prepend(), after(), before()</span>
<span style="color:#8da6ce">setInterval</span>(<span style="color:#fbde2d">function</span> () {
    <span style="color:#fbde2d">if</span> (jQuery(<span style="color:#61ce3c">'#div2 h3'</span>).<span style="color:#8da6ce">length</span> <span style="color:#fbde2d">!</span><span style="color:#fbde2d">=</span> <span style="color:#d8fa3c">0</span>) {
        jQuery(<span style="color:#61ce3c">'#div1'</span>).append(jQuery(<span style="color:#61ce3c">'#div2 h3'</span>)[<span style="color:#d8fa3c">0</span>]);
    } <span style="color:#fbde2d">else</span> {
        jQuery(<span style="color:#61ce3c">'#div2'</span>).append(jQuery(<span style="color:#61ce3c">'h3'</span>).eq(<span style="color:#fbde2d">-</span><span style="color:#d8fa3c">2</span>));
    }
}, <span style="color:#d8fa3c">300</span>);
jQuery(<span style="color:#61ce3c">'#div1'</span>).clone().attr(<span style="color:#61ce3c">'id'</span>, <span style="color:#61ce3c">'div3'</span>).prependTo(jQuery(<span style="color:#61ce3c">'#div1'</span>));
</pre>

<?php
putFooter();
?>

