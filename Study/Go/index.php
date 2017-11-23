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
                <h1 class="display-3">Go</h1>
                <p>Go is <img class="img-fluid" alt="Go Logo" src="./go.png"/></p>
            </div>
        </div>

        <div class="container">
            <div class="row">
                <div class="col-md-4">
                    <h2>설치 및 실행</h2>
                    <p>1. https://golang.org/<br>
                    2. 필요하면 ~/Go/bin을 PATH에 추가<br>
                    3. go run name.go</p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>기본 문법</h2>
                    <p>1. 주석 : //, /* */<br>
                    2. 키워드 : break, default, func, interface, select, case, defer, Go, map, Struct, chhan, else, Goto, package, Switch, const, fallthrough, if, range, Type, continue, for, import, return, Var<br>
                    3. line end(;)가 필요없다.</p>
                </div>
                <div class="col-md-4">
                    <h2>자료형</h2>
                    <p>1. Boolean : true, false<br>
                    2. Numeric : byte = uint8, uint16, rune = uint32, uint64, int8, int16, int32, int64, float32, float64, complex64, complex128, [uint, int, uintptr] = 32 or 64bits<br>
                    3. String<br>
                    4. Derived types</p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>변수</h2>
                    <p>1. static type variable : var 식별자 [type]<br>
                    2. dynamic type variable : 식별자 := 값 // 타입을 다시 변경할 수는 없음<br>
                    3. 타입 확인 : %T<br>
                    4. var a, b, c = 1, 2, "a"</p>
                    <p>
                        <a class="btn btn-secondary" href="./1001.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>리터럴, 상수</h2>
                    <p>1. 8진수 : 0, 16진수 : 0x, 지수 : E<br>
                    2. \ooo : 8진수(0~255), \xhh... : 16진수<br>
                    3. 상수 : const // 여기부터 다시...</p>
                    <p>
                        <a class="btn btn-secondary" href="./1002.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2></h2>
                    <p></p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2></h2>
                    <p></p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2></h2>
                    <p></p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2></h2>
                    <p></p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
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