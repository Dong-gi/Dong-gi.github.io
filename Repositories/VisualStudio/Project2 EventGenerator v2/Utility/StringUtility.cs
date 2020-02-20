using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace EventGenerator.Utility
{
    public static class StringUtility
    {
        // m_quest → mQuest
        public static string ReplaceUnderbarToUpper(string s)
            => Regex.Replace(s, "_([a-z])", c => c.ToString().ToUpper()).Replace("_", "");

        // mQuest → m_quest
        public static string ReplaceUpperToUnderbar(string s)
            => Regex.Replace(s, "[^A-Z][A-Z]", c => "_" + c.ToString().ToLower());

        public static List<string> AsList(this string item) => new List<string> { item };

        public static string Copy(this string str) => new string(str.ToCharArray());

        public static string RemoveComment_HTML(this string str) => Regex.Replace(str, @"<!--[\s\S]*?-->", "", RegexOptions.Multiline);
    }
}
