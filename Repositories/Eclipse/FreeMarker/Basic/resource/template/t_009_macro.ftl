<#macro hello name="Anonymous">Hello ${name}! [<#nested>]</#macro>
<#macro hello2 name="Anonymous">Hello ${name}! [(<#nested>)<<#nested>>]</#macro>

<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        <@hello name="A"><@hello name="B"><@hello name="C"/></@hello></@hello>

        <@hello2 name="A"><@hello2 name="B"><@hello2 name="C"/></@hello2></@hello2>
    </body>
</html>

<#--

<!doctype html>

<html lnag="ko">
    <head>
        <title>t_009_macro.ftl</title>
    </head>

    <body>
Hello A! [Hello B! [Hello C! []]]
Hello A! [(Hello B! [(Hello C! [()<>])<Hello C! [()<>]>])<Hello B! [(Hello C! [()<>])<Hello C! [()<>]>]>]    </body>
</html>

-->
