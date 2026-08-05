using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Data;

namespace WpfApp6
{
    public partial class MainWindow : Window
    {
        public string Message { get; set; } = "Hello World!";

        public MainWindow()
        {
            InitializeComponent();

            label1.SetBinding(Label.ContentProperty, new Binding()
            {
                Source = scroll,
                Path = new PropertyPath(ScrollBar.ValueProperty)
            });
        }
    }
}
