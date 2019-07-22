using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public static class StringUtility
    {
        // m_quest → mQuest
        public static string ReplaceUnderbarToUpper(string s) =>
            Regex.Replace(s, "_([a-z])", c => c.ToString().ToUpper()).Replace("_", "");

        // mQuest → m_quest
        public static string ReplaceUpperToUnderbar(string s) =>
            Regex.Replace(s, "[^A-Z][A-Z]", c => "_" + c.ToString().ToLower());

        // 중복되지 않는 파일명
        public static string AlternateFileName(string binPath, string fileName)
        {
            if (!File.Exists(binPath + fileName))
                return fileName;

            var name = Service.FileService.FileNameWithoutExt(fileName);
            var ext = Service.FileService.FileExt(fileName);
            for (var suffix = 1; ; ++suffix)
            {
                var newFileName = $"{name}_{suffix}.{ext}";
                var filePath = binPath + newFileName;
                if (!File.Exists(filePath))
                    return newFileName;
            }
        }

        public static List<string> AsList(this string item) => new List<string> { item };
    }
}
