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
                <h1 class="display-3">Python</h1>
                <p>Python is concise.</p>
            </div>
        </div>

        <div class="container">
            <div class="row">
                <div class="col-md-4">
                    <h2>설치 및 실행</h2>
                    <p>1. Python 2에 대한 지원이 중단되므로 특별한 이유가 없다면 Python 3이상의 최신 버전 설치. https://www.python.org/downloads/<br>
                    2. 윈도우 Shell에서 파이썬을 곧바로 실행하려면 설치경로(예. C:\Program Files\Python36)를 path 환경변수에 추가.<br>
                    3. Python Shell은 각 문장을 대화식 인터프리터가 해석하여 결과를 바로 출력해준다.<br>
                    4. 'hello.py'파일이 저장된 디렉터리에서 셸을 통해 python hello.py를 실행.<br>
                    5. Python Shell에서 import this를 실행하면 파이썬의 철학을 확인할 수 있다.</p>
                </div>
                <div class="col-md-4">
                    <h2>자료형 1</h2>
                    <p>1. type() 함수를 통해 인자의 자료형을 확인할 수 있다.<br>
                    2. 파이썬의 변수는 동적 자료형이므로 ─ 실행 도중 자료형이 바뀔 수 있다는 의미 ─ 자바와 같은 강한 타입 검사를 하지 않는다. 또, 변수를 나타내는 키워드 없이 식별자만 있으면 변수로써 이용할 수 있다.<br>
                    3. 링크의 예제 코드는 숫자, 문자열에 대해 다룬다.</p>
                    <p>
                        <a class="btn btn-secondary" href="./1000.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>자료형 2</h2>
                    <p>1. 리스트는 변경 가능 <-> 튜플은 변경 불가능<br>
                    2. 튜플은 불변하므로 딕셔너리의 키로 이용할 수 있다.</p>
                    <p>
                        <a class="btn btn-secondary" href="./1001.php" role="button">View Code : 리스트 &raquo;</a>
                        <a class="btn btn-secondary" href="./1002.php" role="button">View Code : 튜플 &raquo;</a>
                        <a class="btn btn-secondary" href="./1003.php" role="button">View Code : 딕셔너리 &raquo;</a>
                        <a class="btn btn-secondary" href="./1004.php" role="button">View Code : 셋 &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>파이썬 문법 1</h2>
                    <p>1. 주석 : #<br>
                    2. 라인 유지 : \<br>
                    3. 조건 분기 : if ~ elif ~ else<br>
                    4. bool 연산 : and, or, not<br>
                    5. False로 간주되는 값 : None, 0, 0.0, '', [], (), {}, set()</p>
                </div>
                <div class="col-md-4">
                    <h2>파이썬 문법 2</h2>
                    <p>1. while 반복 : while + 조건식<br>
                    2. for 순회 : for + 순회 변수<br>
                    3. 반복문 제어 : break, continue<br>
                    4. break 호출 확인 : else</p>
                    <p>
                        <a class="btn btn-secondary" href="./1005.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>순회 대상 생성 1</h2>
                    <p>1. 수열 생성 : range([start,] stop[, step]). start 기본값 0, step 기본값 1. stop 자체는 미포함<br>
                    2. 여러 시퀀스 동시 순회 : zip(). 같은 오프셋의 요소들로 튜플을 생성. 시퀀스가 하나라도 끝나면 순회 종료<br>
                    3. comprehension : 순회를 통해 리스트, 딕셔너리, 셋 생성</p>
                    <p>
                        <a class="btn btn-secondary" href="./1006.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>함수 1</h2>
                    <p>1. 정의 : def name():<br>
                    2. return이 없으면 호출자는 None을 얻는다. None 검사 : is None<br>
                    3. * : 인자를 튜플로. ** : 인자를 딕셔너리로.<br>
                    4. docstring : 함수 body 첫 줄에 문자열로 함수 설명. help()로 출력된다.<br>
                    5. 외부 scope를 사용할 수 있으므로 유의</p>
                    <p>
                        <a class="btn btn-secondary" href="./1007.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>함수 2</h2>
                    <p>1. 함수 내부에 함수 정의 가능.<br>
                    2. 람다 함수 : lambda<br>
                    3. Generator : 자료를 순회할 때마다 다음 항목 반환<br>
                    4. Decorator : 함수 꾸미기</p>
                    <p>
                        <a class="btn btn-secondary" href="./1008.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
                <div class="col-md-4">
                    <h2>네임스페이스</h2>
                    <p>1. 메인 프로그램은 전역 네임스페이스를 정의<br>
                    2. globals() : 전역 네임스페이스 정보를 담은 딕셔너리<br>
                    3. 함수는 지역 네임스페이스를 정의<br>
                    4. locals() : 지역 네임스페이스 정보를 담은 딕셔너리<br>
                    5. global 키워드로 전역 변수 접근</p>
                    <p>
                        <a class="btn btn-secondary" href="./1009.php" role="button">View Code &raquo;</a>
                    </p>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4">
                    <h2>예외</h2>
                    <p>1. try:<br>
                    2. except: or except ExceptionType as name:<br>
                    3. 사용자 정의 : class MyException(Exception):<br>
                    4. raise MyException("Don't Worry")</p>
                </div>
                <div class="col-md-4">
                    <h2>모듈</h2>
                    <p>1. 모듈 : 파이썬 파일 하나<br>
                    2. 패키지 : __init__.py 파일을 포함하는 디렉터리<br>
                    3. [from package] import module_name1, module_name2</p>
                    <p>
                        <a class="btn btn-secondary" href="#" role="button">View Code(No Code) &raquo;</a>
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