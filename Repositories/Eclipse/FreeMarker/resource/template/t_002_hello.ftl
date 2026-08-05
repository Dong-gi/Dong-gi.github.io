<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        Hello ${name!"Anonymous"}<br>
        <#if !name??>
        휴먼, 이름이 없습니까?
        </#if>
    </body>
</html>

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_002_hello.ftl</title>
    </head>

    <body>
        Hello Anonymous<br>
        휴먼, 이름이 없습니까?
    </body>
</html>

-->
