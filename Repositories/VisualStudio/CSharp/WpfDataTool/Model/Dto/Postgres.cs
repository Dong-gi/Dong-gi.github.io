using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace WpfDataTool.Model.Dto
{
    static class Postgres
    {
        /// <summary>
        /// information_schema.tables
        /// </summary>
        public class PostgresTables
        {
            public string table_catalog { get; set; }
            public string table_schema { get; set; }
            public string table_name { get; set; }
            public string table_type { get; set; }
            public string self_referencing_column_name { get; set; }
            public string reference_generation { get; set; }
            public string user_defined_type_catalog { get; set; }
            public string user_defined_type_schema { get; set; }
            public string user_defined_type_name { get; set; }
            public string is_insertable_into { get; set; }
            public string is_typed { get; set; }
            public string commit_action { get; set; }
        }

        /// <summary>
        /// information_schema.columns
        /// </summary>
        public class PostgresColumns
        {
            public string table_catalog { get; set; }
            public string table_schema { get; set; }
            public string table_name { get; set; }
            public string column_name { get; set; }
            public string ordinal_position { get; set; }
            public string column_default { get; set; }
            public string is_nullable { get; set; }
            public string data_type { get; set; }
            public string character_maximum_length { get; set; }
            public string character_octet_length { get; set; }
            public string numeric_precision { get; set; }
            public string numeric_precision_radix { get; set; }
            public string numeric_scale { get; set; }
            public string datetime_precision { get; set; }
            public string interval_type { get; set; }
            public string interval_precision { get; set; }
            public string character_set_catalog { get; set; }
            public string character_set_schema { get; set; }
            public string character_set_name { get; set; }
            public string collation_catalog { get; set; }
            public string collation_schema { get; set; }
            public string collation_name { get; set; }
            public string domain_catalog { get; set; }
            public string domain_schema { get; set; }
            public string domain_name { get; set; }
            public string udt_catalog { get; set; }
            public string udt_schema { get; set; }
            public string udt_name { get; set; }
            public string scope_catalog { get; set; }
            public string scope_schema { get; set; }
            public string scope_name { get; set; }
            public string maximum_cardinality { get; set; }
            public string dtd_identifier { get; set; }
            public string is_self_referencing { get; set; }
            public string is_identity { get; set; }
            public string identity_generation { get; set; }
            public string identity_start { get; set; }
            public string identity_increment { get; set; }
            public string identity_maximum { get; set; }
            public string identity_minimum { get; set; }
            public string identity_cycle { get; set; }
            public string is_generated { get; set; }
            public string generation_expression { get; set; }
            public string is_updatable { get; set; }
        }
    }
}
