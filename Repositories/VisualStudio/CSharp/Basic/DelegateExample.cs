using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Basic
{
    public class DelegateExample
    {
        public delegate void SleepDelegate(int milliSeconds);

        private static readonly long start = DateTime.Now.Ticks;

        private static void Print(string msg) => Console.WriteLine($"{DateTime.Now.Ticks - start} : {msg}");
        private static void Sleep(int milliSeconds)
        {
            Print("호출 전");
            Thread.Sleep(milliSeconds);
            Print("호출 후");
        }
        public static void InstantiationExample()
        {
            var sleep1 = new SleepDelegate(Sleep);
            sleep1(1);
            // 19943 : 호출 전
            // 39886 : 호출 후

            SleepDelegate sleep2 = Sleep;
            sleep2(1);
            // 39886 : 호출 전
            // 59836 : 호출 후

            SleepDelegate sleep3 = delegate (int milliSeconds)
            {
                Sleep(milliSeconds);
            };
            sleep3(1);
            // 59836 : 호출 전
            // 79779 : 호출 후

            SleepDelegate sleep4 = (milliSeconds) => Sleep(milliSeconds); // Since C# 3
            sleep4(1);
            // 79779 : 호출 전
            // 99729 : 호출 후
        }

        public static void AsyncCall()
        {
            SleepDelegate sleepDelegate = Sleep;
            Print("비동기 호출 전");
            sleepDelegate.BeginInvoke(100, (asyncResult) =>
            {
                Print($"콜백 호출됨 {asyncResult.AsyncState}");
            }, 1234);
            Print("비동기 호출 후");

            // 29920 : 비동기 호출 전
            // 29920 : 비동기 호출 후
            // 59844 : 호출 전
            // 1077114 : 호출 후
            // 1077114 : 콜백 호출됨 1234
        }

        public static void AsyncCallAndWait()
        {
            SleepDelegate sleepDelegate = Sleep;
            Print("비동기 호출 전");
            var result = sleepDelegate.BeginInvoke(100, (asyncResult) =>
            {
                Print($"콜백 호출됨 {asyncResult.AsyncState}");
            }, 1234);
            Print("비동기 호출 후");

            sleepDelegate.EndInvoke(result);
            // 또는 result.AsyncWaitHandle.WaitOne(); 등으로도 가능
            Print("비동기 호출 대기 완료");

            // 19946 : 비동기 호출 전
            // 79785 : 비동기 호출 후
            // 209443 : 호출 전
            // 1226718 : 호출 후
            // 1226718 : 비동기 호출 대기 완료
            // 1226718 : 콜백 호출됨 1234
        }
    }
}
