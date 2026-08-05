using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace WpfApp1
{
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
        }

        private void Button_Click(object sender, RoutedEventArgs e)
        {
            // name 속성을 추가하면 이름을 통해 접근할 수 있다.
            grid1.Background = Brushes.Cyan;
            if (sender is Button button)
                button.Content = Application.Current.Properties["myKey"];
        }
        private void Window_Loaded(object sender, RoutedEventArgs e)
        {
            Application.Current.Properties["myKey"] = "Hello World";
            MessageBox.Show("Window loaded");
        }
        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            e.Cancel = MessageBox.Show("Are you sure?", "Confirm shut down", MessageBoxButton.OKCancel) == MessageBoxResult.Cancel;
        }
    }
}
