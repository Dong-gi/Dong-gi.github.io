<#macro gcd int1 int2>
    <#if int1 lt int2>
        <#local big = int2>
        <#local small = int1>
    <#else>
        <#local big = int1>
        <#local small = int2>
    </#if>
    gcd(${big}, ${small})를 구합니다...
    <#list 1.. as round>
        <#local remain = big % small>
        <#nested round, big, small, remain>

        <#if remain == 0><#break></#if>
        <#local big = small>
        <#local small = remain>
    </#list>
    구했다 : ${small}
</#macro>

<#macro print_round round, big, small, remain>
    현재 라운드(${round}) : big = ${big}, small = ${small}, remain = ${remain}
</#macro>

<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        <@gcd int1=108 int2=84 ; round, big, small, remain>
            <@print_round round=round big=big small=small remain=remain/>
        </@gcd>
    </body>
</html>

<#--

<!doctype html>

<html lnag="ko">
    <head>
        <title>t_010_macro.ftl</title>
    </head>

    <body>
    gcd(108, 84)를 구합니다...
    현재 라운드(1) : big = 108, small = 84, remain = 24

    현재 라운드(2) : big = 84, small = 24, remain = 12

    현재 라운드(3) : big = 24, small = 12, remain = 0

    구했다 : 12
    </body>
</html>

-->
