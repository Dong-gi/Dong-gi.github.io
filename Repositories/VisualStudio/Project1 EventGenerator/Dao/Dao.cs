using EventGenerator.Model;
using EventGenerator.Utility;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Dao
{
    public class Dao
    {
        private Connection Connection { get; set; }
        protected string query = "";

        public Dao(DB db) => Connection = new Connection(db);

        public Dao Append(string moreQuery)
        {
            query += (moreQuery + ' ');
            return this;
        }

        public Dao Select() => Append("SELECT");
        public Dao Select(string expression) => Select().Append(expression);
        public Dao SelectAll() => Select("*");
        public Dao From(string fromItems) => Append("FROM").Append(fromItems);
        public Dao Where() => Append("WHERE");
        public Dao Where(string condition) => Where().Append("(").Append(condition).Append(")");
        public Dao In(string column, IEnumerable<string> ids) => Append(column).Append("IN (").Append(string.Join(",", ids.Where(x => !string.IsNullOrWhiteSpace(x)).Distinct())).Append(")");
        public Dao And() => Append("AND");
        public Dao Between(string column, string from, string to) => Append("(").Append(column).Append("BETWEEN").Append(from).Append("AND").Append(to).Append(")");
        public Dao GroupBy(string groupingElements) => Append("GROUP BY").Append(groupingElements);
        public Dao OrderBy(params string[] expression) => Append("ORDER BY").Append(string.Join(",", expression));

        /// <summary>
        /// "SELECT * FROM ${table} WHERE ${column} IN (${ids})
        /// </summary>
        public Dao SelectAllWhereIdIn(string table, string column, IEnumerable<string> ids) =>
            ids.Count() > 0 ? SelectAll().From(table).Where().In(column, ids) : new GaraDao(DB.DB1);

        /// <summary>
        /// "SELECT * FROM ${table} WHERE (${column} IN ${from} AND ${to})"
        /// </summary>
        public Dao SelectAllWhereBetween(string table, string column, string from, string to)
            => SelectAll().From(table).Where().Between(column, from, to);

        public virtual IEnumerable<T> Query<T>(DBServer insteadDBServer = null) => Connection.Query<T>(query + ";", insteadDBServer);
    }
}
