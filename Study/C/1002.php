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

<pre style="background:#0c1021;color:#f8f8f8">#<span style="color:#fbde2d">include</span> <span style="color:#61ce3c">&lt;stdio.h></span>

<span style="color:#fbde2d">int</span> <span style="color:#ff6400">main</span>(void)
{
    int *arr[3];
    int a = 1, b = 2, c = 3;
    arr[0] = &amp;a, arr[1] = &amp;b, arr[2] = &amp;c;
    for (int i = 0; i &lt; 3; )
    {
        printf("%d ", **(arr+i++));
    }
    printf("\n");

    int (*arr2)[];
    int arr3[] = { 4, 5, 6 };
    arr2 = &amp;arr3;
    for (int i = 0; i &lt; 3; ++i)
    {
        printf("%d ", (*arr2)[i]);
    }

    getchar();
    return 0;
}
</pre>
        </div>
    </main>

    <hr>

<?php
put_default_footer();
?>

</body>

</html>