<#macro hello_world>Hello World</#macro>

<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        <@hello_world/>
        <@hello_world></@hello_world>
        <@hello_world/>
    </body>
</html>

<#--

<!doctype html>

<html lnag="ko">
    <head>
        <title>t_007_macro.ftl</title>
    </head>

    <body>
Hello WorldHello WorldHello World    </body>
</html>

-->
