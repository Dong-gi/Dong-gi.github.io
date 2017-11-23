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
                <h1 class="display-3">Hello, world!</h1>
                <p>Thanks for visiting. This is my studing space.</p>
            </div>
        </div>

        <div class="container">
            <div class="row">
                <div class="col-md-4">
                    <h2>171108</h2>
                    <p>I changed the site's design.</p>
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