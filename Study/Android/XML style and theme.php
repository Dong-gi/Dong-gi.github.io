<?
header('Content-Type: text/html; charset=utf-8');
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

$server_root_path = $_SERVER['DOCUMENT_ROOT'];
include $server_root_path.'/lib/create_code_page.php';

putHeader();
putTitle("AndroidManifest.xml");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#7f90aa">&lt;?<span style="color:#7f90aa">xml</span><span style="color:#7f90aa"> version</span>=<span style="color:#61ce3c">"1.0"</span><span style="color:#7f90aa"> encoding</span>=<span style="color:#61ce3c">"utf-8"</span>?></span>
<span style="color:#7f90aa">&lt;<span style="color:#7f90aa">manifest</span> <span style="color:#7f90aa">xmlns</span>:android=<span style="color:#61ce3c">"http://schemas.android.com/apk/res/android"</span>
    <span style="color:#7f90aa">package</span>=<span style="color:#61ce3c">"hu.pe.wiz.myapplication"</span>></span>

    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">application</span>
        <span style="color:#7f90aa">android</span>:allowBackup=<span style="color:#61ce3c">"true"</span>
        <span style="color:#7f90aa">android</span>:icon=<span style="color:#61ce3c">"@mipmap/ic_launcher"</span>
        <span style="color:#7f90aa">android</span>:label=<span style="color:#61ce3c">"@string/app_name"</span>
        <span style="color:#7f90aa">android</span>:roundIcon=<span style="color:#61ce3c">"@mipmap/ic_launcher_round"</span>
        <span style="color:#7f90aa">android</span>:supportsRtl=<span style="color:#61ce3c">"true"</span>
        <span style="color:#7f90aa">android</span>:theme=<span style="color:#61ce3c">"@style/AppTheme"</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">activity</span> <span style="color:#7f90aa">android</span>:name=<span style="color:#61ce3c">".MainActivity"</span>
            <span style="color:#7f90aa">android</span>:label=<span style="color:#61ce3c">"Use a activity as a dialog"</span>
            <span style="color:#7f90aa">android</span>:theme=<span style="color:#61ce3c">"@android:style/Theme.Holo.Dialog"</span>></span>
            <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">intent-filter</span>></span>
                <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">action</span> <span style="color:#7f90aa">android</span>:name=<span style="color:#61ce3c">"android.intent.action.MAIN"</span> /></span>

                <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">category</span> <span style="color:#7f90aa">android</span>:name=<span style="color:#61ce3c">"android.intent.category.LAUNCHER"</span> /></span>
            <span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">intent-filter</span>></span>
        <span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">activity</span>></span>
    <span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">application</span>></span>

<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">manifest</span>></span>
</pre>

<?
putTitle("/res/values/styles.xml");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#7f90aa">&lt;<span style="color:#7f90aa">resources</span>></span>

    <span style="color:#aeaeae">&lt;!-- Base application theme. --></span>
    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">style</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"AppTheme"</span> <span style="color:#7f90aa">parent</span>=<span style="color:#61ce3c">"Theme.AppCompat.Light.DarkActionBar"</span>></span>
        <span style="color:#aeaeae">&lt;!-- Customize your theme here. --></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"colorPrimary"</span>></span>@color/colorPrimary<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"colorPrimaryDark"</span>></span>@color/colorPrimaryDark<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"colorAccent"</span>></span>@color/colorAccent<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
    <span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">style</span>></span>

    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">style</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"MyTextViewStyle"</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"android:layout_width"</span>></span>match_parent<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"android:layout_height"</span>></span>wrap_content<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"android:text"</span>></span>"Hello World!"<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
        <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">item</span> <span style="color:#7f90aa">name</span>=<span style="color:#61ce3c">"android:textColor"</span>></span>#224488<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">item</span>></span>
    <span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">style</span>></span>

<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">resources</span>></span>

</pre>

<?
putTitle("activity_main.xml");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#7f90aa">&lt;?<span style="color:#7f90aa">xml</span><span style="color:#7f90aa"> version</span>=<span style="color:#61ce3c">"1.0"</span><span style="color:#7f90aa"> encoding</span>=<span style="color:#61ce3c">"utf-8"</span>?></span>
<span style="color:#7f90aa">&lt;<span style="color:#7f90aa">LinearLayout</span>
    <span style="color:#7f90aa">xmlns</span>:android=<span style="color:#61ce3c">"http://schemas.android.com/apk/res/android"</span>
    <span style="color:#7f90aa">xmlns</span>:tools=<span style="color:#61ce3c">"http://schemas.android.com/tools"</span>
    <span style="color:#7f90aa">android</span>:layout_width=<span style="color:#61ce3c">"match_parent"</span>
    <span style="color:#7f90aa">android</span>:layout_height=<span style="color:#61ce3c">"match_parent"</span>
    <span style="color:#7f90aa">android</span>:orientation=<span style="color:#61ce3c">"vertical"</span>
    <span style="color:#7f90aa">android</span>:gravity=<span style="color:#61ce3c">"center"</span>
    <span style="color:#7f90aa">tools</span>:context=<span style="color:#61ce3c">"hu.pe.wiz.myapplication.MainActivity"</span>></span>

    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">TextView</span> <span style="color:#7f90aa">style</span>=<span style="color:#61ce3c">"@style/MyTextViewStyle"</span> /></span>
    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">TextView</span> <span style="color:#7f90aa">style</span>=<span style="color:#61ce3c">"@style/MyTextViewStyle"</span>
        <span style="color:#7f90aa">android</span>:textAppearance=<span style="color:#61ce3c">"?android:attr/textAppearanceLargePopupMenu"</span>/></span>
    <span style="color:#aeaeae">&lt;!-- ?는 참조를 의미 --></span>
    <span style="color:#7f90aa">&lt;<span style="color:#7f90aa">Button</span>
        <span style="color:#7f90aa">style</span>=<span style="color:#61ce3c">"?android:attr/buttonBarButtonStyle"</span>
        <span style="color:#7f90aa">android</span>:layout_width=<span style="color:#61ce3c">"wrap_content"</span>
        <span style="color:#7f90aa">android</span>:layout_height=<span style="color:#61ce3c">"wrap_content"</span>
        <span style="color:#7f90aa">android</span>:text=<span style="color:#61ce3c">"OK"</span>/></span>

<span style="color:#7f90aa">&lt;/<span style="color:#7f90aa">LinearLayout</span>></span>

</pre>

<?
putTitle("MainActivity.java");
?>

<pre style="background:#0c1021;color:#f8f8f8"><span style="color:#fbde2d">package</span> <span style="color:#fbde2d">hu.pe.wiz.myapplication</span>;

<span style="color:#fbde2d">import</span> <span style="color:#fbde2d">android.app.Activity</span>;
<span style="color:#fbde2d">import</span> <span style="color:#fbde2d">android.os.Bundle</span>;

<span style="color:#fbde2d">public</span> <span style="color:#fbde2d">class</span> <span style="color:#ff6400">MainActivity</span> <span style="color:#fbde2d">extends</span> <span style="color:#ff6400;font-style:italic">Activity</span> {

    <span style="color:#fbde2d">@Override</span>
    <span style="color:#fbde2d">protected</span> <span style="color:#fbde2d">void</span> <span style="color:#ff6400">onCreate</span>(<span style="color:#fbde2d">Bundle</span> savedInstanceState) {
        super<span style="color:#fbde2d">.</span>onCreate(savedInstanceState);
        setContentView(<span style="color:#fbde2d">R</span><span style="color:#fbde2d">.</span>layout<span style="color:#fbde2d">.</span>activity_main);
    }
}

</pre>

<?
putTitle("결과 화면");
?>

<img src="20170705_195824.png"/>

<?
putFooter();
?>

