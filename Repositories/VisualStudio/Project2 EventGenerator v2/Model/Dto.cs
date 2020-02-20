using EventGenerator.Model.CustomAttribute;

namespace EventGenerator.Model.Dto
{
    static class Game1
    {
        [CustomDB(DB.DB1)]
        public class Table1
        {
            public string column1 { get; set; }
            public string column2 { get; set; }
        }
    }

    static class Game1Extension1
    {
        public class Table1 : Game1.Table1
        {
            public string extension_column1 { get; set; }
        }
    }

    public class Gara
    {
        public string C0 { get; set; }
        public string C1 { get; set; }
        public string C2 { get; set; }
        public string C3 { get; set; }
        public string C4 { get; set; }
        public string C5 { get; set; }
        public string C6 { get; set; }
        public string C7 { get; set; }
        public string C8 { get; set; }
        public string C9 { get; set; }
        public string C10 { get; set; }
        public string C11 { get; set; }
        public string C12 { get; set; }
        public string C13 { get; set; }
        public string C14 { get; set; }
        public string C15 { get; set; }
        public string C16 { get; set; }
        public string C17 { get; set; }
        public string C18 { get; set; }
        public string C19 { get; set; }
        public string C20 { get; set; }
        public string C21 { get; set; }
        public string C22 { get; set; }
        public string C23 { get; set; }
        public string C24 { get; set; }
        public string C25 { get; set; }
        public string C26 { get; set; }
        public string C27 { get; set; }
        public string C28 { get; set; }
        public string C29 { get; set; }

        public string Get(int index) => index switch
        {
            0 => C0,
            1 => C1,
            2 => C2,
            3 => C3,
            4 => C4,
            5 => C5,
            6 => C6,
            7 => C7,
            8 => C8,
            9 => C9,
            10 => C10,
            11 => C11,
            12 => C12,
            13 => C13,
            14 => C14,
            15 => C15,
            16 => C16,
            17 => C17,
            18 => C18,
            19 => C19,
            20 => C20,
            21 => C21,
            22 => C22,
            23 => C23,
            24 => C24,
            25 => C25,
            26 => C26,
            27 => C27,
            28 => C28,
            29 => C29,
            _ => "?",
        };
    }
}
