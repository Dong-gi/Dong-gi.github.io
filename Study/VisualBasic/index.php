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
                <h1 class="display-3">Visual Basic.NET</h1>
                <p>Basic is not basic.</p>
            </div>
        </div>

        <div class="container">
            <div class="row">
                <div class="col-md-4">
                    <h2>설치 및 실행</h2>
                    <p>1. Visual Studio Community 설치<br>
                    2. Visual Basic - Windows Forms 앱 프로젝트 생성<br>
                    3. Ctrl + F5 : 실행, F7 & Shift + F7 : 디자이너와 코드 전환<br>
                    4. 닷넷에 관한 설명은 생략한다.</p>
                </div>
                <div class="col-md-4">
                    <h2>자료형/변수</h2>
                    <p>1. 각 폼 구성 요소의 속성/이벤트를 간단하게 변경 가능.<br>
                    2. 자료형(.NET CTS) : Byte, Short, Integer, Long, Single, Double, Decimal, Char, String, Boolean, Object<br>
                    3. 변수 선언 : [접근 제한자] 식별자 [As 자료형] // 자료형 기본은 Object<br>
                    4. 접근 제한자 : Dim(지역 변수), Public, Private, Protected, Friend, Shared, Static<br>
                    5. 상수 선언 : [Public | Private] Const 식별자 [As 자료형] = 식 or 값</p>
                </div>
                <div class="col-md-4">
                    <h2>연산자</h2>
                    <p>1. 산술 연산자 : +, -, *, /, \(몫), Mod, ^(power)<br>
                    2. 관계 연산자 : =, <>, <, <=, >, >=, Like(문자열 패턴 비교), Is(참조 비교)<br>
                    3. 논리 연산자 : And, Or, Xor, Not, AndAlso(Short Circuit), OrElse(Short Circuit)<br>
                    4. 문자열 연결 연산자 : &</p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a><br>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>조건 분기</h2>
                    <p>1. If 판별식 Then /* 여러 문장 */ End If<br>
                    2. If 판별식 Then // 한 문장<br>
                    3. If 판별식 Then ~ ElseIf 판별식 Then ~ [Else ~ ] End If<br>
                    4. Select Case 값|식 ~ Case 값1 ~ [Case Else ~ ] End Select</p>
                    <p>
                        <a class="btn btn-secondary" href="./1001.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>반복</h2>
                    <p>1. For 변수 = 초기값 To 종료값 [Step 증감값] ~ Next 변수// 종료값 포함, Step 기본값 1<br>
                    2. For Each 원소 In 배열|컬렉션 ~ Next 원소<br>
                    3. Do While 판별식 ~ Loop, Do ~ Loop While 판별식<br>
                    4. Do Until 판별식 ~ Loop, Do ~ Loop Until 판별식</p>
                    <p>
                        <a class="btn btn-secondary" href="./1002.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>제어</h2>
                    <p>1. GoTo 레이블, On Error GoTo 레이블<br>
                    2. Exit For|Do|Function|Property|Sub<br>
                    3. End : 프로그램 종료<br>
                    4. Try ~ [Exit Try] ~ Catch [변수명 [As 형식] [When 예외 판별식]] ~ [Finally ~] End Try</p>
                    <p>
                        <a class="btn btn-secondary" href="./1003.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>프로시저</h2>
                    <p>1. 서브 프로시저 : 반환 없는 프로시저. 이벤트 프로시저 + 사용자 정의<br>
                    2. 서브 프로시저 정의 : [접근 제한자] Sub 식별자([매개변수 목록]) ~ End Sub<br>
                    3. 함수 프로시저 : 값을 반환하는 프로시저.<br>
                    4. 함수 프로시저 정의 : [접근 제한자] Function 식별자([매개변수 목록]) [As 반환 타입] ~ 함수명 = 리턴값 ~ End Function<br>
                    5. 참조 매개변수 키워드 : ByRef</p>
                    <p>
                        <a class="btn btn-secondary" href="./1004.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>변수 스코프</h2>
                    <p>1. Dim : 정의된 영역에서만 유효한 지역 변수<br>
                    2. Static : 정의된 영역이 최초 실행될 때 한 번 초기화. 프로그램 죵료 시 해제<br>
                    3. 프로시저 외부에 정의된 변수를 이용할 수 있다.<br>
                    4. Public/Private : 정의된 모듈 외부에서 이용할 수 있/없다.</p>
                </div>
                <div class="col-md-4">
                    <h2>배열</h2>
                    <p>1. 배열 : {}, 참조 및 선언 : ()<br>
                    2. ReDim : 배열 재정의, Preserve : 재정의 전 배열 데이터 유지<br>
                    3. 다차원 배열 : (,,)<br>
                    4. Jagged Array : ()()</p>
                    <p>
                        <a class="btn btn-secondary" href="./1005.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>클래스</h2>
                    <p>1. 정의 : [접근 제한자] Class 식별자 ~ End Class<br>
                    2. 생성자 : Sub New([매개변수 목록]) // 오버로딩 가능<br>
                    3. 상속 : Inherits 상위 클래스 // 다중 상속 불가<br>
                    4. 객체 자신 : Me</p>
                </div>
                <div class="col-md-4">
                    <h2>내장 라이브러리</h2>
                    <p>
                        <a class="btn btn-secondary" href="./1006.php" role="button">View Code : String &raquo;</a>
                        <a class="btn btn-secondary" href="./1007.php" role="button">View Code : Math, DateTime, 난수 &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>컨트롤 1</h2>
                    <p>1. 마우스 이벤트 : Click, DoubleClick, MouseDown, MouseUp, MouseMove<br>
                    2. 키보드 이벤트 : KeyDown, KeyUp, KeyPress ASCII키<br>
                    3. Form : Load 이벤트. Form 내부 각 요소들은 TabIndex 속성을 갖는다.<br>
                    4. Button : Image 속성을 통해 안에 이미지를 넣을 수 있다.<br>
                    5. ImageList : 프로그램 내부에서 사용할 Image들을 관리할 수 있다.</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>컨트롤 2</h2>
                    <p>1. Label : 사용자가 직접 편집할 수 없는 텍스트<br>
                    2. TextBox : 사용자가 직접 편집할 수 있는 텍스트. MultiLine, MaxLength 등<br>
                    3. CheckBox : Checked 속성 등<br>
                    4. GroupBox : Form1.Designer.vb 중, Me.GroupBox1.Controls.Add(Me.RadioButton2)<br>
                    5. RadioButton : 그룹에 포함되어 있다면 그룹 내의 라디오버튼 중 하나만 선택 가능</p>
                </div>
                <div class="col-md-4">
                    <h2>컨트롤 3</h2>
                    <p>1. ListBox : 항목들을 리스트로 보여주고 한 개 이상을 동시 선택 가능<br>
                    ListBox :: Items :: Item, Count, IsReadOnly, Add(), Clear(), IndexOf() 등<br>
                    2. ComboBox : 항목들 중 하나만 선택 가능. DropDownStyle 속성 등<br>
                    3. ScrollBar : VScrollBar, HScrollBar<br>
                    LargeChange, SmallChange, Maximum, Minimum, Value 속성 등</p>
                </div>
                <div class="col-md-4">
                    <h2>컨트롤 4</h2>
                    <p>1. Timer : 주기적인 동작 혹인 일시적인 동작 정의 가능. Enabled 속성, Tick 이벤트 등<br>
                    2. ListView : 아이콘과 항목을 함께 표시. View, AutoArrange, Sorting, MultiSelect 속성 등<br>
                    3. TreeView : 항목의 계층 구조 표시. Nodes, ShowPlusMinus, SelectedNode 속성 등<br>
                    4. MsgBox(메시지, [, MsgBoxStyle] [, 제목]) : 사용자 반응(MsgBoxResult) 리턴<br>
                    5. InputBox(메시지, [, 제목] [, 기본값]) : 사용자 입력 문자열 리턴</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>컨트롤 5</h2>
                    <p>1. PictureBox : Image, SizeMode 속성<br>
                    2. LineShape : 선분. X1, Y1, X2, Y2, BorderStyle 속성 등<br>
                    3. OvalShape : 타원. Location, Size 속성 등<br>
                    4. RectangleShape : 사각형. Location, Size 속성 등</p>
                </div>
                <div class="col-md-4">
                    <h2>그래픽</h2>
                    <p>1. Dim g = Me.CreateGraphics()<br>
                    2. Graphics :: DrawLine(), DrawRectangle(), DrawEllipse(), DrawString() 등</p>
                </div>
                <div class="col-md-4">
                    <h2>파일 입출력</h2>
                    <p>1. FileSystem.FileOpen(FileNumber, FileName, OpenMode) // FileNumber로 파일을 구별하므로 그 값이 고유해야한다.<br>
                    2. In : FileSystem :: Input(FileNumber, Value) // Value에 저장, LineInput(FileNumber), <br>
                    3. Out : FileSystem ::Print(FileNumber, ParamArray), PrintLine(FileNumber, ParamArray) <br>
                    4. FileSystem.EOF(FileNumber) : 파일 끝 판정<br>
                    5. FileSystem.FileClose(FileNumber)</p>
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