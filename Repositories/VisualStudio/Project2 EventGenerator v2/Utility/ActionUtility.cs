using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class ActionUtility
    {
        /// <summary>
        /// UI 스레드에서 action을 실행하고 종료를 대기합니다.
        /// </summary>
        public static async Task UI(System.Action action)
            => await System.Windows.Application.Current.Dispatcher.BeginInvoke(action);
    }
}
