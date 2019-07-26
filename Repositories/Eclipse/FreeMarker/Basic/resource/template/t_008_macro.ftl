<#macro hello name="Anonymous" now=.now>
Hello ${name}. @${now}
</#macro>

<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        <@hello/><br>
        <@hello name="Donggi"/>
    </body>
</html>

<#--

<!doctype html>

<html lnag="ko">
    <head>
        <title>t_008_macro.ftl</title>
    </head>

    <body>
        Hello Anonymous. @2019. 4. 12. 오후 4:58:51
<br>
Hello Donggi. @2019. 4. 12. 오후 4:58:51
    </body>
</html>

-->
