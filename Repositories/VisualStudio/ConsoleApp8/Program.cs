using System.Xml.Linq;

var xElement = XElement.Load("http://bbs.ruliweb.com/news/rss");
var rss = from item in xElement.Descendants("item").AsParallel()
          select new
          {
              Title = item.Element("title")?.Value,
              Description = item.Element("description")?.Value,
              PubDate = item.Element("pubDate")?.Value
          };

foreach (var item in rss.Select(x => new
{
    Title = x.Title.Length > 30 ? x.Title[..30] : x.Title,
    x.PubDate,
    Description = x.Description.Length > 50 ? x.Description[..50] : x.Description
}).Take(10))
    Console.WriteLine(@$"{nameof(item.Title)} : {item.Title}
{nameof(item.PubDate)} : {item.PubDate}
{nameof(item.Description)} : {item.Description}

");

/*
Title : ‘e풋볼 2022(eFootball™2022)’ 업데이
PubDate : Wed, 20 Apr 2022 21:19:17 +0900
Description : <img width="90" height="75" src="//img.ruliweb.com


Title : 테라, 6월 30일 PC 버전 서비스 종료 결정
PubDate : Wed, 20 Apr 2022 17:42:40 +0900
Description : <img width="90" height="75" src="//img.ruliweb.com


Title : ‘스나이퍼 엘리트 5’ PS4, PS5 한국어판 5월
PubDate : Wed, 20 Apr 2022 16:50:52 +0900
Description : <img width="90" height="75" src="//img.ruliweb.com
*/
