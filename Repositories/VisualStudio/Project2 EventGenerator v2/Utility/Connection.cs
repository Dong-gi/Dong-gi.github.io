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
        private DB db;

        public Connection(DB db) => this.db = db;

        public IEnumerable<T> Query<T>(string query, DBServer dbServer, int retryCount = 3)
        {
            if (new Regex(@"in\s*\((\s*,?)*\)", RegexOptions.IgnoreCase).IsMatch(query))
                return new List<T>();

            try
            {
                var tokenSource = new CancellationTokenSource();
                var task = Task.Run<IEnumerable<T>>(() => InnerQuery<T>(query, dbServer), tokenSource.Token);
                var timer = Task.Run(() => { Thread.Sleep(5000); tokenSource.Cancel(); });
                Task.WaitAny(task, timer);
                if (tokenSource.IsCancellationRequested)
                    throw new TimeoutException("시간 초과!!");
                return task.Result;
            }
            catch (Exception e)
            {
                Console.Write("\n예외 발생 쿼리 : " + query);
                if (retryCount > 0)
                {
                    Console.Write(e.Message);
                    Console.Write($"재시도합니다... 남은 재시도 횟수 : {retryCount}");
                    return Query<T>(query, dbServer, retryCount - 1);
                }
                else
                {
                    Console.Write(e);
                    return new List<T>();
                }
            }
        }

        private IEnumerable<T> InnerQuery<T>(string query, DBServer dbServer)
        {
            switch (dbServer.Kind)
            {
                case DBServer.ServerKind.SQLITE:
                    var columns = new List<string>();
                    typeof(T).GetRuntimeProperties().ForEach(propertyInfo =>
                    {
                        if (!(System.Attribute.GetCustomAttribute(propertyInfo, typeof(CustomVersionAttribute)) is CustomVersionAttribute version) ||
                            version.Active(dbServer.Version))
                            columns.Add(string.Format("NULLIF({0}, '') AS {0}", propertyInfo.Name));
                    });
                    query = query.Replace("SELECT *", "SELECT " + string.Join(",", columns));
                    using (var connection = new System.Data.SQLite.SQLiteConnection(GetConnectionString(dbServer)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
                case DBServer.ServerKind.POSTGRES:
                    using (var connection = new NpgsqlConnection(GetConnectionString(dbServer)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
                case DBServer.ServerKind.MYSQL:
                    using (var connection = new MySqlConnection(GetConnectionString(dbServer)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
            }
            return new List<T>();
        }

        private string GetConnectionString(DBServer dbServer) {
            switch (dbServer.Kind) {
                case DBServer.ServerKind.SQLITE:
                    return $"Data Source=Resources\\{dbServer.Name}\\{dbServer.Name}_{db.ToString().ToLower()}.db";
                case DBServer.ServerKind.POSTGRES:
                    return $"Server={dbServer.ServerIP};Port={dbServer.ServerPort};Database={db.ToString().ToLower()};Userid={dbServer.User};Password={dbServer.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;Timeout=300;CommandTimeout=300;"; // Enlist=true;
                case DBServer.ServerKind.MYSQL:
                    return $"Server={dbServer.ServerIP};Port={dbServer.ServerPort};Database={db.ToString().ToLower()};Userid={dbServer.User};Password={dbServer.Password};Pooling=true;MinPoolSize=5;MaxPoolSize=20;";
            }
            return "";
        }
    }
}
