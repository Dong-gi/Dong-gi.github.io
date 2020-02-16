using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class ActionUtility
    {
        /// <summary>
        /// UI 스레드에서 action을 실행하고 종료를 대기합니다.
        /// </summary>
        /// <param name="action">UI 스레드에서 실행할 로직</param>
        /// <returns></returns>
        public static async Task UI(System.Action action)
            => await System.Windows.Application.Current.Dispatcher.BeginInvoke(action);
    }
}
