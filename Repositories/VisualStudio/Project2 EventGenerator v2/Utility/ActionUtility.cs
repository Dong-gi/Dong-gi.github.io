using System;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class ActionUtility
    {
        /// <summary>
        /// UI 스레드에서 action을 실행하고 종료를 대기합니다.
        /// </summary>
        public static async Task UI(Action action)
            => await System.Windows.Application.Current.Dispatcher.BeginInvoke(action);

        /// <summary>
        /// UI 스레드에서 func을 실행하고 종료를 대기합니다.
        /// </summary>
        public static T UI<T>(Func<T> func)
        {
            var task = System.Windows.Application.Current.Dispatcher.InvokeAsync<T>(func);
            task.Wait();
            return task.Result;
        }

        /// <summary>
        /// 주기적으로 action을 실행합니다.
        /// </summary>
        public static async Task SetInterval(Action action, TimeSpan delay)
        {
            while (true)
            {
                action();
                await Task.Delay(delay);
            }
        }
    }
}
