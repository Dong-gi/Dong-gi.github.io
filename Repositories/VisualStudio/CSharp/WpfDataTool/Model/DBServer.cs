using WpfDataTool.Model.Enum;

namespace WpfDataTool.Model
{
    public class DBServer
    {
        public string Name { get; private set; }
        public string ServerIP { get; private set; }
        public string ServerPort { get; private set; }
        public string User { get; private set; }
        public string Password { get; private set; }
        public double Version { get; private set; } = 0;
        public ServerKind ServerKind { get; private set; }
    }
}
