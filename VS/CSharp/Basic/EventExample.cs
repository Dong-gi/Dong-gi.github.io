using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public class EventExample
    {
        // 이벤트 구독자가 없을 때 Invoke시 예외 발생되는 것 방지
        public event EventHandler SomeEvent = delegate { };

        public class SomeEventArgs : EventArgs
        {
            public string Text { get; set; }
        }

        public static void Example()
        {
            var example = new EventExample();
            example.SomeEvent += (sender, args) => {
                Console.WriteLine((args as SomeEventArgs).Text);
            };
            example.SomeEvent.Invoke(example, new SomeEventArgs { Text = "Event Argument" });
        }
    }
}
