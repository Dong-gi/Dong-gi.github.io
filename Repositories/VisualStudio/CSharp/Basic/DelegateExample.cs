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

        public static void Example1()
        {
            Console.WriteLine(System.DateTime.Now.Second + " : 호출 전");
            SleepDelegate sleepDelegate = Thread.Sleep;
            var result = sleepDelegate.BeginInvoke(5000, (asyncResult) =>
            {
                // public delegate void AsyncCallback(IAsyncResult ar);
                Console.WriteLine(System.DateTime.Now.Second + " : 콜백 호출됨");
                Thread.Sleep(1000);
            }, sleepDelegate);
            Console.WriteLine(System.DateTime.Now.Second + " : 호출 후");

            Thread.Sleep(1000);
            Console.WriteLine(System.DateTime.Now.Second + " : 대기 전");
            result.AsyncWaitHandle.WaitOne(); // + 오버로딩 메서드 있음

            // 여기부터는 콜백 종료 후 실행된다.
            Thread.Sleep(1000);
            Console.WriteLine(System.DateTime.Now.Second + " : 대기 후");

            sleepDelegate.EndInvoke(result);
            Console.WriteLine(System.DateTime.Now.Second + " : 종료");
        }

        public static void Example2()
        {
            Console.WriteLine(System.DateTime.Now.Second + " : 호출 전");
            SleepDelegate sleepDelegate = Thread.Sleep;
            sleepDelegate.BeginInvoke(5000, (asyncResult) =>
            {
                Console.WriteLine(System.DateTime.Now.Second + " : 콜백 호출됨");
                (asyncResult.AsyncState as SleepDelegate).EndInvoke(asyncResult);
            }, sleepDelegate);
            Console.WriteLine(System.DateTime.Now.Second + " : 호출 후");

            Thread.Sleep(1000);
            Console.WriteLine(System.DateTime.Now.Second + " : 종료");
        }
    }
}
