using System.Collections.Generic;

namespace EventGenerator.Model
{
    public class SaveXlsRequest
    {
        public string SheetName { get; set; }
        public string ClassName { get; set; }
        public List<string[]> Data { get; set; }
    }
}
