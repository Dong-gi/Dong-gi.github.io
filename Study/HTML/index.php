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
            <div class="jumbotron">
                <h1 class="display-3">HTML</h1>
            </div>
        </div>

        <div class="container">
            <div class="row">
                <div class="col-md-4">
                    <h2>기본 태그 1</h2>
                    <p>1. &lt;!DOCTYPE html&gt; : HTML5임을 나타냄<br>
                    2. &lt;html&gt;&lt;/html&gt; : root HTML element<br>
                    3. &lt;head&gt;&lt;/head&gt; : meta information<br>
                    4. &lt;title&gt;&lt;/title&gt; : 제목표시줄에 표시될 제목<br>
                    5. &lt;body&gt;&lt;/body&gt; : page content</p>
                </div>
                <div class="col-md-4">
                    <h2>기본 태그 2</h2>
                    <p>1. &lt;h1&gt;&lt;/h1&gt; ~ &lt;h6&gt;&lt;/h6&gt; : header<br>
                    2. &lt;p&gt;&lt;/p&gt; : paragraph<br>
                    3. &lt;a&gt;&lt;/a&gt; : HTML link, 속성 : href<br>
                    4. &lt;img&gt; : image, 속성 : src, alt, width, height<br>
                    5. &lt;br&gt; : line break</p>
                </div>
                <div class="col-md-4">
                    <h2>속성</h2>
                    <p>1. 모든 HTML 요소는 속성을 가질 수 있다.<br>
                    2. style 속성 : CSS 속성을 지시<br>
                    3. html태그 lang속성 : 문서의 국가 언어 표기.<br>
                    4. p태그 title속성 : 단락에 대한 툴팁 표기.<br>
                    5. 속성엔 '', "" 모두 이용 가능<br>
                    6. disabled속성 : 입력 태그 요소를 사용할 수 없음.</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>기본 태그 3</h2>
                    <p>1. &lt;hr&gt; : 수평선<br>
                    2. &lt;pre&gt;&lt;/pre&gt; : 글자를 그대로 출력</p>
                </div>
                <div class="col-md-4">
                    <h2>스타일</h2>
                    <p>1. Inline CSS : 태그에 style 속성 이용<br>
                    2. Internal CSS : &lt;body&gt;태그 사이에 &lt;style&gt;태그 이용<br>
                    3. External CSS : &lt;link rel="stylesheet" href=""&gt;</p>
                </div>
                <div class="col-md-4">
                    <h2>기본 태그 4</h2>
                    <p>1. &lt;b&gt;&lt;/b&gt; : bold<br>
                    2. &lt;strong&gt;&lt;/strong&gt; : bold & important<br>
                    3. &lt;i&gt;&lt;/i&gt; : italic<br>
                    4. &lt;em&gt;&lt;/em&gt; : italic & important<br>
                    5. &lt;small&gt;&lt;/small&gt; : 작은 글자<br>
                    6. &lt;mark&gt;&lt;/mark&gt; : 형광펜으로 칠한 것처럼<br>
                    7. &lt;del&gt;&lt;/del&gt; : 취소선<br>
                    8. &lt;ins&gt;&lt;/ins&gt; : 삽입 문구에 밑줄<br>
                    9. &lt;sub&gt;&lt;/sub&gt;, &lt;sup&gt;&lt;/sup&gt; : 아랫첨자, 윗첨자</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>기본 태그 5</h2>
                    <p>1. &lt;q&gt;&lt;/q&gt; : 짧은 인용<br>
                    2. &lt;blockquote&gt;&lt;/blockquote&gt; : 긴 인용, cite 속성 : 출처<br>
                    3. &lt;abbr&gt;&lt;/abbr&gt; : 축약어, title 속성 : 원문<br>
                    4. &lt;address&gt;&lt;/address&gt; : 주소<br>
                    5. &lt;cite&gt;&lt;/cite&gt; : 작품 제목<br>
                    6. &lt;bdo&gt;&lt;/bdo&gt; : 텍스트 방향 설정. dir 속성 : rtl</p>
                </div>
                <div class="col-md-4">
                    <h2>주석</h2>
                    <p>1. &lt;!-- 주석 --&gt;<br>
                    2. 조건부 주석 : &lt;!--[if IE 9]&gt; ... &lt;![endif]--&gt;, IE에서만 작동</p>
                </div>
                <div class="col-md-4">
                    <h2>색</h2>
                    <p>1. Name : Tomato, Orange, DodgerBlue, MediumSeaGreen, Gray, SlaeBlue, Violet, LightGray<br>
                    2. RGB : rgb(~255, ~255, ~255)<br>
                    3. HEX : #FFFFFF<br>
                    4. HSL : hsl(~360, ~100%, ~100%), hue(색상), saturation(채도), lightness(밝기)<br>
                    5. RGBA : rgba(~255, ~255, ~255, ~1.0)<br>
                    6. HSLA : hsla(~360, ~100%, ~100%, ~1.0)</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>Link</h2>
                    <p>1. 상대주소 + 절대주소<br>
                    2. 링크 색상 : a:link, a:visited, a:hover, a:active <br>
                    3. target속성 : _blank(새 탭이나 창에서 열기), _self, _parent, _top, framename<br>
                    4. fragment이동 : href="#fragment", p id="fragment"</p>
                </div>
                <div class="col-md-4">
                    <h2>기본 태그 6</h2>
                    <p>1. &lt;picture&gt;&lt;/picture&gt; : 여러 source 이미지 중 적합한 것 하나를 표시<br>
                    2. &lt;table&gt;&lt;/table&gt;, &lt;tr&gt;&lt;/tr&gt;, &lt;th&gt;&lt;/th&gt;, &lt;td&gt;&lt;/td&gt;<br>
                    3. &lt;ul&gt;&lt;/ul&gt;, &lt;ol&gt;&lt;/ol&gt;, &lt;li&gt;&lt;/li&gt;<br>
                    4. &lt;dl&gt;&lt;/dl&gt;, &lt;dt&gt;&lt;/dt&gt;, &lt;dd&gt;&lt;/dd&gt;</p>
                </div>
                <div class="col-md-4">
                    <h2>Block vs Inline</h2>
                    <p>1. Block-level elements : 가로 100%를 점유한다. 대표 : div<br>
                    2. Inline elements : 가로 공간이 여유롭다면 병렬로 표시된다. 대표 : span</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>기본 태그 7</h2>
                    <p>1. &lt;iframe src="", width="", height=""&gt;&lt;/iframe&gt; : iframe을 막는 사이트도 있음에 유의.<br>
                    2. &lt;iframe name="iframe_1"&gt;&lt;/iframe&gt;, &lt;a href="" target="iframe_1"&gt;&lt;/a&gt;</p>
                </div>
                <div class="col-md-4">
                    <h2>JavaScript</h2>
                    <p>1. &lt;script&gt;&lt;/script&gt; 사이에 작성<br>
                    2. &lt;noscript&gt;&lt;/noscript&gt; 사이에 지원하지 않는 브라우저에 대한 메시지 작성</p>
                </div>
                <div class="col-md-4">
                    <h2>meta 태그</h2>
                    <p>1. &lt;meta charset="UTF-8"&gt;<br>
                    2. &lt;meta name="description" content="Study Space"&gt;<br>
                    3. &lt;meta name="keywords" content="Study, Programming"&gt;<br>
                    4. &lt;meta name="author" content="Donggi Kim"&gt;<br>
                    5. &lt;meta http-equiv="refresh" content="60"&gt;<br>
                    6. &lt;meta name="viewport" content="width=device-width, initial-scale=1.0"&gt;</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>Layout</h2>
                    <p>1. &lt;header&gt; : 문서의 헤더 표시<br>
                    2. &lt;nav&gt; : 문서에 대한 내비게이션 표시<br>
                    3. &lt;section&gt; : 문서에 대한 영역<br>
                    4. &lt;article&gt; : 독립적인 컨텐츠 영역<br>
                    5. &lt;aside&gt; : 컨텐츠 옆 영역<br>
                    6. &lt;footer&gt; : 문서 하단 표시</p>
                </div>
                <div class="col-md-4">
                    <h2>Character 리터럴</h2>
                    <p>1. &nbsp;, &lt;, &gt;, &amp;, &quot;, &apos;, &copy;, &reg;<br>
                    2. a&#768;, a&#769;, a&#770;, a&#771;<br>
                    3. &forall;, &part;, &exist;, &empty;, &nabla;, &isin;, &notin;, &ni;, &prod;, &sum;<br>
                    4. &alpha;, &beta;, &gamma;, &delta;, &epsilon;</p>
                </div>
                <div class="col-md-4">
                    <h2>form</h2>
                    <p>1. &lt;form&gt;&lt;/form&gt;<br>
                    2. &lt;input type=""&gt; : text, password, radio, checkbox, submit, reset...<br>
                    3. form 속성 : action="주소", target="_blank | _top | ...", method="get | post"<br>
                    4. &lt;fieldset&gt; : 입력 요소 그루핑, &lt;legend&gt; : 그룹 설명<br>
                    5. &lt;select name=""&gt;&lt;option value=""&gt;&lt;/option&gt; ~ &lt;/select&gt;<br>
                    6. &lt;textarea rows="" cols=""&gt;, &lt;button onclick=""&gt;<br>
                    7. &lt;input type=""&gt; : color, date, email, month, number, range, time, ...<br>
                    8. 입력 요소 공통 속성 : readonly, disabled, size, maxlength</p>
                </div>
            </div>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>