using EventGenerator.Model;
using EventGenerator.Service;
using EventGenerator.Utility;
using Newtonsoft.Json;
using System.Text.RegularExpressions;
using System.Windows;
using static EventGenerator.Model.Constants;

namespace EventGenerator.ViewModel
{
    class IconViewModel : BaseViewModel
    {
        #region 프로퍼티
        public MainWindow MainWindow
        {
            get => Get<MainWindow>(nameof(MainWindow), null);
            private set => Set<MainWindow>(nameof(MainWindow), value);
        }
        public NaiveHttpServer HttpServer { get; } = new NaiveHttpServer();
        #endregion


        public IconViewModel()
        {
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
            Commands[UPDATE_COMMAND] = new CustomCommand(UpdateService.Update, false);
            Commands[OPEN_EXECUTING_FORDER_COMMAND] = new CustomCommand(FileService.OpenExecutingFolder);
            Commands[OPEN_BROWSER_COMMAND] = new CustomCommand(HttpServer.OpenBrowser);
            SingleIcon.Toast("실행 위치", System.AppDomain.CurrentDomain.BaseDirectory);

            HttpServer.Handlers.Add(new NaiveHttpServer.Handler()
            {
                RequestFilter = (request) => request.RequestPath.Equals("/query"),
                RequestHandler = (request, stream) =>
                {
                    var db = (DB)System.Enum.Parse(typeof(DB), request.Param["db"]);
                    var server = (DBServer)typeof(DBServer).GetProperty(request.Param["server"], typeof(DBServer)).GetValue(null);
                    var result = new Connection(db).Query<object>(request.Param["query"], server);
                    NaiveHttpServer.Write(stream, JsonConvert.SerializeObject(result));
                }
            });
            HttpServer.Handlers.Add(new NaiveHttpServer.Handler()
            {
                RequestFilter = (request) => request.RequestPath.Equals("/saveXls"),
                RequestHandler = (request, stream) =>
                {
                    var saveXlsRequests = JsonConvert.DeserializeObject<Model.SaveXlsRequest[]>(request.RawParam, new JsonSerializerSettings
                    {
                        Error = (sender, args) =>
                        {
                            System.Console.WriteLine(args.ErrorContext.Error.Message);
                            System.Console.WriteLine(args.ErrorContext.Error.StackTrace);
                        }
                    });
                    var xls = CustomNewExcel.NewInstance();
                    foreach(var req in saveXlsRequests)
                        xls.AddSheet(req.Data, true, req.SheetName);
                    xls.Save("통합 문서.xls");
                    NaiveHttpServer.Write(stream, "OK");
                }
            });
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
            var vm = new MainViewModel();
            MainWindow = new MainWindow
            {
                DataContext = vm
            };
            Utility.ConsoleRedirectWriter.WRITER.WriteEvent += vm.WriteEventHandler;
            MainWindow.Closed += (o, e) =>
            {
                MainWindow = null;
                Utility.ConsoleRedirectWriter.WRITER.WriteEvent -= vm.WriteEventHandler;
            };
            MainWindow.Show();
        }

    }
}
