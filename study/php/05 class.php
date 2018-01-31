<?php
header('Content-Type: text/html; charset=utf-8');
// $host = $_SERVER['HTTP_HOST'];
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

?>
<html>
    <head>
        <title>05 class</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    </head>
    <body>
        <div id="main">
            <div id="content"> 
<?php
class Mine
{
	public $var1;
	private $var2;
	protected $var3;
	
	function __construct()
	{
		$this->act();
	}
	function __destructor(){}
	function __clone()
	{
		// 기본적인 복사를 마친 후 이 부분이 실행된다.
	}
	function __get($name)
	{
		return $this->$name;
	}
	function __set($name, $value)
	{
		$this->$name = $value;
	}
	// Method Overload
	// 메서드 이름과 매개변수 배열을 입력받는다.
	function __call($method, $param)
	{
		if($method == "hello")
			$this->act();
	}
	function __toString()
	{
		return "It's My Class<br/>";
	}
	function act()
	{
		echo "My Class<br/>";
	}
	
	// 오버라이딩 불가
	final function ownAct()
	{
		echo "나만 쓸 수 있다<br/>";
	}
}

$obj = new Mine();
$obj2 = clone $obj;
$obj->hello();
echo $obj;

echo"<pre>".(new ReflectionClass("Mine"))."</pre>";

// 상속 불가
final class Yours extends Mine
{
	// Override
	function act()
	{
		echo "Your Class<br/>";
	}
}
$obj->act();
(new Yours())->act();
// 늦은 바인딩
new Yours();

interface Actable
{
	function act();
}

class Hers implements Actable
{
	// 상수
	const LEVEL = 10;
	
	function act()
	{
		echo "Nya~<br/>";
	}
	
	static function speak()
	{
		echo "Nya Nya<br/>";
	}
}
echo Hers::LEVEL;
Hers::speak();

if($obj instanceof Mine)
	echo "It's Mine!<br/>";

// class type hinting : Mine 혹은 그 자손 클래스 객체만 받음
function myFunc(Mine $mine)
{
	$mine->act();
}

abstract class Example
{
	abstract public function show();
}

// 클래스의 인스턴스를 만들 경우 자동으로 호출됨
function __autoload($classname) {
    $filename = $classname.".php";
    include_once($filename);
}
?>
			</div>
            <div id="footer">
            </div>
        </div>
    </body>
</html>