using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public class EventExample
    {
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

        public event EventHandler SomeEvent;
    }
}
