using MoreLinq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Xml.Linq;

namespace Basic
{
    public class LinqExample
    {
        public static void Example()
        {
            var xElement = XElement.Load("http://bbs.ruliweb.com/news/rss");
            var rss = from item in xElement.Descendants("item")
                           select new
                           {
                               Title = item.Element("title").Value,
                               Description = item.Element("description").Value,
                               PubDate = item.Element("pubDate").Value
                               // 속성 추출 : item.Attribute("속성명").Value or item.Attribute["속성명"].Value
                           };
            rss.ForEach(x =>
            {
                Console.WriteLine($"{nameof(x.Title)} : {x.Title}@{x.PubDate}\n{nameof(x.Description)} : {x.Description}\n");
            });

/*
Title : 트래비스 스트라이크 어게인: 노 모어 히어로즈, PS4와 PC로@2019.05.26 (12:10:25)
Description : <img width="90" height="75" src="https://cmg.ruliweb.com/data/news/tn/19/05/26/16af21aa4a6_flbqxy.jpg"> 가장 최근에 나온 '노 모어 히어로즈' 시리즈 '트래비스 스트라이크 어게인: 노 모어 히어로즈'(TSA: 노 모어 히어로즈)가 다른 플랫폼으로 이식된다.  미국 조지아주의 주도 애틀랜타에서 개최된 'MOMOCON 2019'에서 그래스...

Title : 리마스터 결정, 스타 오션 퍼스트 디파쳐 R@2019.05.25 (23:17:06)
Description : <img width="90" height="75" src="https://cmg.ruliweb.com/data/news/tn/19/05/25/16aef584d23_klnbux.jpg"> ' 스타 오션'의 첫 번째 작품이 현세대기로 등장한다. 스퀘어 에닉스가 '스타 오션 퍼스트 디파쳐 R'(STAR OCEAN First Departure R)을 발표한 것.  제목을 보면 알 수 있듯이 소니컴퓨터엔터테인먼트코리아(현 소니...

Title : 크래프톤의 로그라이크 신작, ‘미스트오버’ 유저 간담회@2019.05.25 (19:02:32)
Description : <img width="90" height="75" src="https://cmg.ruliweb.com/data/news/tn/19/05/25/16aee70c00a_zgbstn.jpg"> 전 블루홀에서 사명을 변경한 크래프톤이 자사의 신작이자 PC/콘솔 플랫폼으로 출시할 로그라이크 던전크롤링 게임 ‘미스트오버’ 를 한국 유저들에게 선보이는 유저 간담회를 열었다.                  오늘 행사에서는 유저 및 ...

...*/
        }
    }
}
