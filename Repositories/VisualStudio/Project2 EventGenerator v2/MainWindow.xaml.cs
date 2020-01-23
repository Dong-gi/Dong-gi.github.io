using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Security;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;
using EventGenerator.ViewModel;

namespace EventGenerator
{
    /// <summary>
    /// MainWindow.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();

            System.Globalization.CultureInfo c = new System.Globalization.CultureInfo("ko-KR");
            c.DateTimeFormat.FullDateTimePattern = "yyyy-MM-dd HH:mm:ss";
            c.DateTimeFormat.LongDatePattern = "yyyy-MM-dd";
            c.DateTimeFormat.LongTimePattern = "HH:mm:ss";
            Thread.CurrentThread.CurrentCulture = c;
        }

        private void Window_DragEnter(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop)) e.Effects = DragDropEffects.Copy;
        }

        private void Window_Drop(object sender, DragEventArgs e)
        {
            (this.DataContext as MainViewModel).FileDroped((string[])e.Data.GetData(DataFormats.FileDrop));
        }

        private void DateTextBox_KeyUp(object sender, KeyEventArgs e)
        {
            var t = (sender as TextBox);

            if ((Key.D0 <= e.Key && e.Key <= Key.D9) ||
                (Key.NumPad0 <= e.Key && e.Key <= Key.NumPad9))
            {
                var digits = Regex.Replace(t.Text, @"\D", "");

                digits = (digits.Length > 8) ? digits.Substring(0, 8) : digits;
                if (digits.Length > 3)
                    digits = digits.Insert(4, "-");
                if (digits.Length > 6)
                    digits = digits.Insert(7, "-");

                t.Text = digits;
                t.CaretIndex = t.Text.Length;
            }
        }

        private void TextBox_PreviewDragOver(object sender, DragEventArgs e)
        {
            e.Handled = true;
        }

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            Utility.CustomExcel.CloseAll();
            (DataContext as MainViewModel).TaskbarIcon.Dispose();
            (DataContext as MainViewModel).Server.Server.Stop();
        }
    }
}
