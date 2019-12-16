using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Threading;

namespace WpfApp6
{
    public class MyClock : INotifyPropertyChanged
    {
        public string DateTime { get; set; } = "Not Allocated";

        public event PropertyChangedEventHandler PropertyChanged;

        public MyClock()
        {
            var timer = new DispatcherTimer();
            timer.Tick += (obj, e) =>
            {
                DateTime = System.DateTime.Now.ToString();
                PropertyChanged(this, new PropertyChangedEventArgs("DateTime"));
            };
            timer.Interval = new TimeSpan(0, 0, 1);
            timer.Start();
        }
    }
}
