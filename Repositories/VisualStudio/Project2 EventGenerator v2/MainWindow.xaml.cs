using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
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
    }
}
