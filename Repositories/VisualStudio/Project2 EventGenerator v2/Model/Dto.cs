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

        public string Get(int index)
        {
            switch (index)
            {
                case 0: return C0;
                case 1: return C1;
                case 2: return C2;
                case 3: return C3;
                case 4: return C4;
                case 5: return C5;
                case 6: return C6;
                case 7: return C7;
                case 8: return C8;
                case 9: return C9;
                case 10: return C10;
                case 11: return C11;
                case 12: return C12;
                case 13: return C13;
                case 14: return C14;
                case 15: return C15;
                case 16: return C16;
                case 17: return C17;
                case 18: return C18;
                case 19: return C19;
                case 20: return C20;
                case 21: return C21;
                case 22: return C22;
                case 23: return C23;
                case 24: return C24;
                case 25: return C25;
                case 26: return C26;
                case 27: return C27;
                case 28: return C28;
                case 29: return C29;
            }
            return "?";
        }
    }
}
