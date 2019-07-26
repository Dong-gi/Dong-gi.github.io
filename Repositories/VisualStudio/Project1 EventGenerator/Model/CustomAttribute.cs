using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Model.CustomAttribute
{
    [System.AttributeUsage(System.AttributeTargets.Property)]
    class CustomVersionAttribute : System.Attribute
    {
        public readonly double added;
        public readonly double removed;

        /// <summary>
        /// [added, removed)에 포함되는 버전에서만 유효함을 표시.
        /// </summary>
        /// <param name="added">유효 버전 시작점</param>
        /// <param name="removed">무효 버전 시작점</param>
        public CustomVersionAttribute(double added = double.MinValue, double removed = double.MaxValue)
            => (this.added, this.removed) = (added, removed);


        public bool Active(double version) => version >= added && version < removed;
        public bool Active(string version) => Active(double.Parse(version));
    }

    [AttributeUsage(AttributeTargets.Class)]
    class CustomDBAttribute : System.Attribute
    {
        public Model.DB DB { get; set; }

        public CustomDBAttribute(DB db) => DB = db;
    }
}
