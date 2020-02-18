using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    public class SaveXlsRequest
    {
        public string SheetName { get; set; }
        public string ClassName { get; set; }
        public List<string[]> Data { get; set; }
    }
}
