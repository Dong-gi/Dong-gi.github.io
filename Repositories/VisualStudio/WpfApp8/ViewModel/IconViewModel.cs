using WpfDataTool.Utility;
using System.Text.RegularExpressions;
using System.Windows;
using static WpfDataTool.Model.Constants;

namespace WpfDataTool.ViewModel
{
    public class IconViewModel : BaseViewModel
    {
        #region 프로퍼티
        public static IconViewModel Current { get; private set; }
        public MainWindow MainWindow
        {
            get => Get<MainWindow>(nameof(MainWindow), defaultValue: null);
            private set => Set<MainWindow>(nameof(MainWindow), value);
        }
        public NaiveHttpServer HttpServer { get; } = new NaiveHttpServer();
        #endregion


        public IconViewModel()
        {
            Current = this;

            Commands[OPEN_MAIN_WINDOW_COMMAND] = new CustomCommand(OpenMainWindow)
            {
                CanExecuteAction = (_) => MainWindow == null,
                IsBackground = false
            };
            Commands[CLOSE_MAIN_WINDOW_COMMAND] = new CustomCommand()
            {
                CanExecuteAction = (_) => MainWindow != null,
                Command = (_) => MainWindow.Close(),
                IsBackground = false
            };
            Commands[EXIT_APP_COMMAND] = new CustomCommand(Application.Current.Shutdown, false);
            Commands[OPEN_EXECUTING_FORDER_COMMAND] = new CustomCommand(FileUtility.OpenExecutingFolder);
            Commands[OPEN_BROWSER_COMMAND] = new CustomCommand(HttpServer.OpenBrowser);
            SingleIcon.Toast("실행 위치", System.AppDomain.CurrentDomain.BaseDirectory);

            HttpServer.Handlers.Add(new NaiveHttpServer.Handler()
            {
                RequestFilter = (_) => true,
                RequestHandler = (request, stream) =>
                {
                    if (request.RequestPath.Equals("/"))
                        NaiveHttpServer.WriteTextFile($"{HttpServer.DocRoot}/index.html", stream);
                    else if (new Regex(@"\.(html|js|css|txt|java|cs|c|cpp|h|hpp|md|properties|sh|py|json|sql|xml)$", RegexOptions.IgnoreCase).IsMatch(request.RequestPath))
                        NaiveHttpServer.WriteTextFile($"{HttpServer.DocRoot}{request.RequestPath}", stream);
                    else
                        NaiveHttpServer.WriteBinaryFile($"{HttpServer.DocRoot}{request.RequestPath}", stream);
                }
            });
        }


        private void OpenMainWindow()
        {
            MainWindow = new MainWindow();
            Utility.ConsoleRedirectWriter.WRITER.WriteEvent += (MainWindow.DataContext as MainViewModel).WriteEventHandler;
            MainWindow.Closed += (o, e) =>
            {
                Utility.ConsoleRedirectWriter.WRITER.WriteEvent -= (MainWindow.DataContext as MainViewModel).WriteEventHandler;
                MainWindow = null;
            };
            MainWindow.Show();
        }

    }
}
