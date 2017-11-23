<?php
    header('Content-Type: text/html; charset=utf-8');
    setlocale(LC_TIME, "kr_KR.utf8");
    date_default_timezone_set('Asia/Seoul');

    $server_root_path = $_SERVER['DOCUMENT_ROOT'];
    include $server_root_path.'/lib/create_code_page.php';

    putHeader();
?>

<pre>
<a href="01 form and process.php"><b>01 form and process</b></a>
* 사용된 것들 :
	form, echo(), intval(), fopen(), fwrite(), flock(), fgets(), file(), count()

<a href="02 array.php"><b>02 array</b></a>
* 사용된 것들 :
	array(), array_push(), sort(), foreach, range(), shuffle(), array_reverse(), list(), explode(), &, array_walk(), extract()
* 배열 접근 :
	each(), current(), reset(), end(), next(), pos(), prev()
* 배열 카운트 :
	count(), sizeof(), array_count_values()

<a href="03 string.php"><b>03 string</b></a>
* 사용된 것들 :
	isset(), mail(), nl2br()
* 공백 제거 :
	trim(), ltrim(), rtrim() = chop()
* 출력 :
	echo(), print(), printf(), sprintf()
* 대소문자 :
	strtoupper()[전체를 대문자로], strtolower(), ucfirst()[문장 첫 글자만 대문자로], ucwords()[각 단어 첫 글자를 대문자로]
* DB용 문자열 :
	addslashes(), stripslashes(); PHP가 자동으로 GET, POST, cookie에서 가져온 변수를 처리하는지 get_magic_quotes_gpc()를 통해 확인할 수 있다.
* 분리 :
	array explode(string separator, string input) <-> implode(), join();
	string strtok(string input, string separator) : 다음 토큰을 얻을 때는 separator만 보내면 된다. separator가 문자열인 경우, 문자열 전체가 아닌 각각의 문자에 대해 분리한다.
	string substr(string string, int start[, int length]) : 양수면 앞에서부터, 음수면 뒤에서부터
* 비교 :
	strcmp()[대소문자 구분], strcasecmp()[대소문자 미구분], strnatcmp()[대소문자 구분, "12" > "2"], strnatcasecmp()
	nat : natural ordering : www.naturalordersort.org
* 길이 :
	strlen()
* 탐색 :
	strstr()[처음으로 일치하는 문자열부터 끝까지 or FALSE], stristr()[대소문자 구별 x], strrchr()[마지막으로 일치하는 문자부터 끝까지 or FALSE]
	strpos() : 문자열이 처음 등장하는 위치, strrpos() : 문자가 마지막으로 등장하는 위치
* 교체 :
	str_replace(), substr_replace()

<a href="#"><b>04 코드 재사용</b></a>
* 사용된 것들 :
	array(), array_push(), sort(), foreach, range(), shuffle(), array_reverse(), list(), explode(), &, array_walk(), extract()
* 문서 포함 :
	include(), require()
	php.ini의 auto_prepend_file, auto_append_file을 사용하여 전체 문서에 포함 : auto_prepend_file = "c:/~"(윈도우 파일 경로)
	.htaccess를 이용해 디렉터리 전체 문서에 포함 : php_value auto_prepend_file "/home/username/~"(유닉스 파일 경로)
* Function :
	함수는 대소문자 구별이 없다.
	$name() : $name에 저장된 문자열과 동일한 이름을 가진 함수 호출. 변수 함수.
	함수 오버로딩이 지원되지 않는다.
	func_num_args(), func_get_arg(), func_get_args()
	함수 안에서 global을 붙여 변수를 선언하면 전역 범위를 갖는다.
	unset()을 통해 변수를 제거할 수 있다.
	참조로 전달 : &
	name space : www.php.net/language.namespaces

<a href="05 class.php"><b>05 class</b></a>
* __construct(), __destructor(), __clone(), __get(), __set(), __call(), __toString()
* final, static, abstract, public, protected, private
* interface, instanceof, __autoload(), Iterator class + IteratorAggregate interface, reflection API

<a href="06 exception.php"><b>06 exception</b></a>

<a href="07 database.php"><b>07 database</b></a>
* column = field = attribute, row = record = tuple
* MySQL 서버 접속 :
	mysql [-h hostname] -u username -p//실행하면 비밀번호를 입력하라는 메시지가 나온다.
* DB 관련 :
	use db_name;
	db_name < /~/name.sql;
	db_name > /~/name.sql;
* Table 관련 :
	create database db_name; show tables; describe table_name;
	07 query1.sql 참조
* Index 만들기(처리속도 향상) :
	CREATE [UNIQUE|FULLTEXT] INDEX index_name ON table_name (index_column_name [(length)] [ASC|DESC], ...])
* 유저 권한 부여 :
	GRANT privileges [columns] On item To user_name [IDENTIFIED BY 'password'] [REQUIRE ssl_options] [WITH [GRANT OPTION | limit_options]];
	privileges(user) : SELECT, INSERT, UPDATE, DELETE, INDEX, ALTER, CREATE, DROP
	privileges(administrator) : CREATE TEMPORARY TABLES, FILE, LOCK TABLES, PROCESS, RELOAD, REPLICATION CLIENT, REPLICATION SLAVE, SHOW DATABASES, SHUTDOWN, SUPER
	privileges(special) : ALL, USAGE(no privileges)
	GRANT OPTION : MAX_QUERIES_PER_HOUR n, MAX_UPDATES_PER_HOUR n, MAX_CONNECTIONS_PER_HOUR n
* item :
	dbName.table_name or *.* or db_name.*
* 유저 권한 박탈 :
	REVOKE privileges [(columns)] ON item FROM user_name;
* 종료 :
	quit;
</pre>
