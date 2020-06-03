using Dapper;
using MySql.Data.MySqlClient;
using Npgsql;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using WpfDataTool.Model;
using WpfDataTool.Model.Attr;
using WpfDataTool.Model.Enum;

namespace WpfDataTool.Dao
{
    public class Connection
    {
        private string DBName { get; set; }

        public Connection(DB db) => DBName = db.ToString().ToLower();
        public Connection(string db) => DBName = db;


        public IEnumerable<T> Query<T>(DBServer server, string queryString, int retryCount = 3)
        {
            // IN () 조건은 쿼리 실행 생략
            if (new Regex(@"in\s*\((\s*,?)*\)", RegexOptions.IgnoreCase).IsMatch(queryString))
                return new List<T>();

            if (System.Attribute.GetCustomAttribute(typeof(T).GetTypeInfo(), typeof(VerAttribute)) is VerAttribute version
                && !version.Active(server.Version))
            {
                Console.WriteLine($"{typeof(T).Name} 테이블 버전[{version.added}, {version.removed})이 서버({server.Version})와 달라 쿼리 생략 ");
                return new List<T>();
            }

            try
            {
                // 시간 제한 설정
                var tokenSource = new CancellationTokenSource();
                var task = Task.Factory.StartNew<IEnumerable<T>>(() => InnerQuery<T>(server, queryString));
                var timer = Task.Factory.StartNew(() =>
                {
                    Thread.Sleep(5000);
                    tokenSource.Cancel();
                });
                Task.WaitAny(task, timer);
                if (tokenSource.IsCancellationRequested)
                    throw new TimeoutException("시간 초과!!");
                return task.Result;
            }
            catch (Exception e)
            {
                Console.WriteLine($"예외 발생 서버 : {server}\n예외 발생 쿼리 : {queryString}");
                if (retryCount > 0)
                {
                    if (e.InnerException is Exception e2 && e2.Message.Contains("no such table"))
                    {
                        Console.WriteLine("↑ 테이블이 없어 무시합니다.");
                        return new List<T>();
                    }
                    Console.WriteLine(e.Message);
                    Console.WriteLine($"재시도합니다... 남은 재시도 횟수 : {retryCount}");
                    return Query<T>(server, queryString, retryCount - 1);
                }
                else
                {
                    Console.WriteLine(e);
                    return new List<T>();
                }
            }
        }

        private IEnumerable<T> InnerQuery<T>(DBServer server, string query)
        {
            switch (server.ServerKind)
            {
                case ServerKind.SQLITE:
                    var activeProperties = typeof(T).GetRuntimeProperties().Where(propertyInfo =>
                                        // 버전 정보가 명시되지 않은 경우, 유효한 걸로 간주
                                        !(System.Attribute.GetCustomAttribute(propertyInfo, typeof(VerAttribute)) is VerAttribute version)
                                        || version.Active(server.Version)
                                   ).ToList();
                    // 확장 프로퍼티는 제거
                    var extensionProperties = activeProperties.Where(x => x.DeclaringType.FullName.Contains("Extension")).ToList();
                    activeProperties.RemoveAll(x => extensionProperties.Contains(x));

                    var columns = activeProperties.Select(propertyInfo => string.Format("NULLIF({0}, 'NULL') AS {0}", propertyInfo.Name));
                    query = query.Replace("SELECT *", "SELECT " + string.Join(",", columns));
                    using (var connection = new System.Data.SQLite.SQLiteConnection(GetConnectionString(server)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
                case ServerKind.POSTGRES:
                    using (var connection = new NpgsqlConnection(GetConnectionString(server)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
                case ServerKind.MYSQL:
                    using (var connection = new MySqlConnection(GetConnectionString(server)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
            }
            return new List<T>();
        }

        private string GetConnectionString(DBServer server)
        {
            return server.ServerKind switch
            {
                ServerKind.SQLITE => $"Data Source=Resources\\sqlite\\{server.Name}_{DBName.ToString().ToLower()}.db",
                ServerKind.POSTGRES => $"Server={server.ServerIP};Port={server.ServerPort};Database={DBName.ToString().ToLower()};Userid={server.User};Password={server.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;Timeout=300;CommandTimeout=300;",// Enlist=true;
                ServerKind.MYSQL => $"Server={server.ServerIP};Port={server.ServerPort};Database={DBName.ToString().ToLower()};Userid={server.User};Password={server.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;",
                _ => "",
            };
        }
    }
}
