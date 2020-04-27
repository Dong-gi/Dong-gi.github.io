using EventGenerator.Model;
using EventGenerator.Model.Dto;
using EventGenerator.Utility;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace EventGenerator.Dao
{
    public class Dao
    {
        private Connection Connection { get; set; }
        protected StringBuilder QueryString { get; set; }


        public Dao(DB db) => (Connection, QueryString) = (new Connection(db), new StringBuilder());


        public Dao Append(string moreQuery)
        {
            QueryString.Append(moreQuery).Append(' ');
            return this;
        }
        public Dao Select() => Append("SELECT");
        public Dao Select(string expression) => Select().Append(expression);
        public Dao SelectAll() => Select("*");
        public Dao From(string fromItems) => Append("FROM").Append(fromItems);
        public Dao Where() => Append("WHERE");
        public Dao Where(string condition) => Where().Append("(").Append(condition).Append(")");
        public Dao In(string column, IEnumerable<string> ids)
        {
            ids = ids.Where(x => !string.IsNullOrWhiteSpace(x)).Distinct();
            if (ids.Any())
                return Append("(").Append(column).Append("IN (").Append(string.Join(",", ids)).Append("))");
            return Append("IN ()");
        }
        public Dao And() => Append("AND");
        public Dao Between(string column, string from, string to) => Append("(").Append(column).Append("BETWEEN").Append(from).Append("AND").Append(to).Append(")");
        public Dao GroupBy(string groupingElements) => Append("GROUP BY").Append(groupingElements);
        public Dao OrderBy(params string[] expression) => Append("ORDER BY").Append(string.Join(",", expression));
        public Dao OrderByDesc(params string[] expression) => Append("ORDER BY").Append(string.Join(" DESC,", expression)).Append("DESC");

        /// <summary>
        /// "SELECT * FROM ${table} WHERE ${column} IN (${ids})
        /// </summary>
        public Dao SELECT_ALL_FROM_table_WHERE_column_IN_ids(string table, string column, IEnumerable<string> ids)
            => ids.Count() > 0 ? SelectAll().From(table).Where().In(column, ids) : new GaraDao(DB.DB1);

        /// <summary>
        /// "SELECT * FROM ${table} WHERE (${column} IN ${from} AND ${to})"
        /// </summary>
        public Dao SELECT_ALL_FROM_table_WHERE_column_BETWEEN_from_AND_to(string table, string column, string from, string to)
            => SelectAll().From(table).Where().Between(column, from, to);

        public virtual IEnumerable<T> Query<T>(DBServer server, bool print = false) => Connection.Query<T>(server, QueryString.Append(';').ToString(), print: print);

        public static IEnumerable<PostgresTables> GetPostgresTables(DBServer server, DB db)
            => new Connection(db).Query<PostgresTables>(server, "select * from information_schema.tables");

        public static IEnumerable<PostgresColumns> GetPostgresColumns(DBServer server, DB db, string tableName)
            => new Connection(db).Query<PostgresColumns>(server, $"select * from information_schema.columns where table_name = '{tableName}'");

        public static IEnumerable<string> GetPostgresPrimaryKeys(DBServer server, DB db, string tableName)
            => new Connection(db).Query<string>(server, $@"SELECT pg_attribute.attname as column FROM pg_index, pg_class, pg_attribute, pg_namespace 
WHERE 
  pg_class.oid = '{tableName}'::regclass AND 
  indrelid = pg_class.oid AND 
  nspname = 'public' AND 
  pg_class.relnamespace = pg_namespace.oid AND 
  pg_attribute.attrelid = pg_class.oid AND 
  pg_attribute.attnum = any(pg_index.indkey)
 AND indisprimary");
    }
}
