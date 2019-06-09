using SimpleCapture.ViewModel;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;

namespace SimpleCapture
{
    /// <summary>
    /// ControlRecordWindow.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class ControlRecordWindow : Window
    {
        private static ControlRecordWindow _ONLY = new ControlRecordWindow();
        public bool IsClosing { get; set; }
        public static ControlRecordWindow Single { get { return _ONLY; } }

        private ControlRecordWindow()
        {
            InitializeComponent();
            DataContext = new ImageRecoderViewModel();
            Visibility = Visibility.Collapsed;
            IsClosing = false;
        }

        private void window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            e.Cancel = !IsClosing;
            Visibility = Visibility.Collapsed;
        }
    }
}
