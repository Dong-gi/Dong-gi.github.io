using System;

namespace EventGenerator.Model.CustomAttribute
{
    [AttributeUsage(AttributeTargets.Class | AttributeTargets.Property)]
    class CustomVersionAttribute : Attribute
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
    class CustomDBAttribute : Attribute
    {
        public Model.DB DB { get; set; }

        public CustomDBAttribute(DB db) => DB = db;
    }
}
