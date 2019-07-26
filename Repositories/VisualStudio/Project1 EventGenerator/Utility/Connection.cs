using Npgsql;
using System;
using System.Collections.Generic;
using System.Data;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using EventGenerator.Model;
using Dapper;
using System.Reflection;
using EventGenerator.Model.CustomAttribute;
using MoreLinq;

namespace EventGenerator.Utility
{
    public class Connection
    {
        private DB db;
        
        public Connection(DB db) => this.db = db;

        public IEnumerable<T> Query<T>(string query, DBServer dbServer = null, int retryCount = 2)
        {
            if (query.Contains("in ()"))
                return new List<T>();

            dbServer = dbServer ?? ViewModel.MainViewModel.Current.CurrentDBServer;

            try
            {
                if (dbServer.IsSQLite)
                {
                    var columns = new List<string>();
                    typeof(T).GetRuntimeProperties().ForEach(propertyInfo =>
                    {
                        if (!(System.Attribute.GetCustomAttribute(propertyInfo, typeof(CustomVersionAttribute)) is CustomVersionAttribute version) ||
                            version.Active(ViewModel.MainViewModel.Current.CurrentDBServer.Version))
                            columns.Add(string.Format("NULLIF({0}, '') AS {0}", propertyInfo.Name));
                    });
                    query = query.Replace("SELECT *", "SELECT " + string.Join(",", columns));
                    using (var connection = new System.Data.SQLite.SQLiteConnection(GetConnectionString(dbServer)))
                    {
                        connection.Open();
                        return connection.Query<T>(query);
                    }
                }
                using (var connection = new NpgsqlConnection(GetConnectionString(dbServer)))
                {
                    connection.Open();
                    return connection.Query<T>(query);
                }
            }
            catch (Exception e)
            {
                Console.Write("\n\n예외 발생 쿼리 : " + query);
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

        private string GetConnectionString(DBServer dbServer) =>
            dbServer.IsSQLite ?
                $"Data Source=Resources\\{dbServer.Name}\\{dbServer.Name}_{db.ToString().ToLower()}.db" :
                $"Server={dbServer.ServerIP};Port={dbServer.ServerPort};Database={db.ToString().ToLower()};Userid=readonly;Password={dbServer.ServerPW};Pooling=true;MinPoolSize=5;MaxPoolSize=20;Timeout=300;CommandTimeout=300"; // Enlist=true;
    }
}
