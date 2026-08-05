using System;
using WpfDataTool.Model.Enum;

namespace WpfDataTool.Model.Attr
{
    [AttributeUsage(AttributeTargets.Class)]
    class DBAttribute : Attribute
    {
        public DB DB { get; set; }

        public DBAttribute(DB db) => DB = db;
    }
}
