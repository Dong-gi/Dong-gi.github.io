using Npgsql;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using EventGenerator.Model;
using Dapper;
using System.Reflection;
using EventGenerator.Model.CustomAttribute;
using MoreLinq;
using System.Threading;
using System.Text.RegularExpressions;
using MySql.Data.MySqlClient;

namespace EventGenerator.Utility
{
    public class Connection
    {
        private string DBName { get; set; }

        public Connection(DB db) => DBName = db.ToString().ToLower();
        public Connection(string db) => DBName = db;

        public IEnumerable<T> Query<T>(DBServer server, string queryString, int retryCount = 3, bool print = false)
        {
            if (print)
                Console.WriteLine($"연결 서버 : {server}, 요구 타입 {typeof(T)} ,쿼리 : {queryString}");

            // IN () 조건은 쿼리 요청 생략
            if (new Regex(@"in\s*\((\s*,?)*\)", RegexOptions.IgnoreCase).IsMatch(queryString))
                return new List<T>();

            if (System.Attribute.GetCustomAttribute(typeof(T).GetTypeInfo(), typeof(CustomVersionAttribute)) is CustomVersionAttribute version
                && !version.Active(server.Version))
            {
                Console.WriteLine($"{typeof(T).Name} 테이블 버전[{version.added}, {version.removed})이 서버({server.Version})와 달라 쿼리 생략 ");
                return new List<T>();
            }

            try
            {
                // 시간 제한 설정
                var tokenSource = new CancellationTokenSource();
                var task = Task.Factory.StartNew<IEnumerable<T>>(() => InnerQuery<T>(server, queryString, print));
                var timer = Task.Factory.StartNew(() =>
                {
                    Thread.Sleep(5000);
                    tokenSource.Cancel();
                });
                Task.WaitAny(task, timer);
                if (tokenSource.IsCancellationRequested)
                    throw new TimeoutException("시간 초과!!");
                if (print)
                    Console.WriteLine(task.Result);
                return task.Result;
            }
            catch (Exception e)
            {
                Console.WriteLine($"예외 발생 서버 : {server}\n예외 발생 쿼리 : {queryString}");
                if (retryCount > 0)
                {
                    Console.WriteLine(e.Message);
                    Console.WriteLine($"재시도합니다... 남은 재시도 횟수 : {retryCount}");
                    return Query<T>(server, queryString, retryCount - 1, print);
                }
                else
                {
                    ChatworkUtility.Get().SendAsync($"서버 : {server.Name}, DB : {DBName}, 쿼리 : {queryString}");
                    ChatworkUtility.Get().SendAsync(e);
                    return new List<T>();
                }
            }
        }

        private IEnumerable<T> InnerQuery<T>(DBServer server, string query, bool print)
        {
            switch (server.ServerKind)
            {
                case ServerKind.SQLITE:
                    var columns = new List<string>();
                    typeof(T).GetRuntimeProperties().ForEach(propertyInfo =>
                    {
                        if (!(System.Attribute.GetCustomAttribute(propertyInfo, typeof(CustomVersionAttribute)) is CustomVersionAttribute version) ||
                            version.Active(server.Version))
                            columns.Add(string.Format("NULLIF({0}, '') AS {0}", propertyInfo.Name));
                    });
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
                ServerKind.SQLITE => $"Data Source=Resources\\{server.Name}\\{server.Name}_{DBName.ToString().ToLower()}.db",
                ServerKind.POSTGRES => $"Server={server.ServerIP};Port={server.ServerPort};Database={DBName.ToString().ToLower()};Userid={server.User};Password={server.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;Timeout=300;CommandTimeout=300;",// Enlist=true;
                ServerKind.MYSQL => $"Server={server.ServerIP};Port={server.ServerPort};Database={DBName.ToString().ToLower()};Userid={server.User};Password={server.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;",
                _ => "",
            };
        }
    }
}
