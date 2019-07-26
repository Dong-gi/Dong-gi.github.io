using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    public class DBServer
    {
        public string Name { get; private set; }
        public string ServerIP { get; private set; }
        public string ServerPW { get; private set; }
        public string ServerPort { get; private set; }
        public double Version { get; private set; } = 0;
        public bool IsSQLite { get; private set; } = false;


        private DBServer(string name) => Name = name;

        #region Game1 DBMS
        public static DBServer GAME1_NOW = new DBServer("개발 서버(now 1.2.3)")
        {
            ServerIP = "localhost",
            ServerPW = "password",
            ServerPort = "3306",
            Version = 1.0002_0003
        };
        public static DBServer GAME1_UPGRADE = new DBServer("개발 서버(next 2.3.4)")
        {
            ServerIP = "localhost",
            ServerPW = "password",
            ServerPort = "5432",
            Version = 2.0003_0004
        };
        #endregion

        #region Game2 DBMS
        public static DBServer GAME2_NOW = new DBServer("개발 서버(now 3.4.5)") {
            ServerIP = "localhost",
            ServerPW = "password",
            ServerPort = "3306",
            Version = 3.0004_0005
        };
        public static DBServer GAME2_DUMP = new DBServer("DUMP 4.56")
        {
            IsSQLite = true,
            Version = 4.0056_0000
        };
        #endregion


        public override string ToString() => Name;
    }
}
