<#setting locale="en_US">

<#assign dateFormatString = "yyyy.MM.dd">
${"2019.05.14"?date(dateFormatString)}

${"15:05:30"?time("HH:mm:ss")}

<#assign timeFormatString = "hh:mm:ss a">
${"11:33:55 PM"?time(timeFormatString)}

<#assign datetimeFormatString = "yyyy-MM-dd HH:mm:ss">
${"2019-05-14 20:40:50"?datetime(datetimeFormatString)}
