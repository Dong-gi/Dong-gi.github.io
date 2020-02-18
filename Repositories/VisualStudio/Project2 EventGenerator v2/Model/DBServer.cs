using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    public class DBServer
    {
        public enum ServerKind { POSTGRES, MYSQL, SQLITE }

        public string Name { get; private set; }
        public string ServerIP { get; private set; }
        public string ServerPort { get; private set; }
        public string User { get; private set; }
        public string Password { get; private set; }
        public double Version { get; private set; } = 0;
        public ServerKind Kind { get; private set; } = ServerKind.POSTGRES;


        private DBServer() { }

        #region Game1 DBMS
        public static DBServer[] Game1DBServers { get => new DBServer[] { Game1Now, Game1Upgrade }; }
        public static DBServer Game1Now { get; private set; } = new DBServer()
        {
            Name = "개발 서버",
            ServerIP = "192.168.136.129",
            ServerPort = "5432",
            User = "dgkim",
            Password = "dgkim",
        };
        public static DBServer Game1Upgrade { get; private set; } = new DBServer()
        {
            Name = "버전업 서버",
            ServerIP = "localhost",
            ServerPort = "3306",
            User = "dgkim",
            Password = "dgkim",
            Kind = ServerKind.MYSQL
        };
        #endregion

        #region Game2 DBMS
        public static DBServer[] Game2DBServers { get => new DBServer[] { Game2Now, Game2Dump }; }
        public static DBServer Game2Now { get; private set; } = new DBServer()
        {
            Name = "개발 서버",
            ServerIP = "localhost",
            ServerPort = "3306",
            User = "user",
            Password = "password",
            Version = 3.0004_0005
        };
        public static DBServer Game2Dump { get; private set; } = new DBServer()
        {
            Name = "DUMP 2.22",
            Version = 2.0022_0000,
            Kind = ServerKind.SQLITE,
        };
        #endregion


        public override string ToString() => Name;
    }
}
