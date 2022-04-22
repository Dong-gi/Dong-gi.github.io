namespace WpfDataTool.Model
{
    public static class Constants
    {
        #region 커맨드
        public const string OPEN_MAIN_WINDOW_COMMAND = "OPEN_MAIN_WINDOW_COMMAND";
        public const string CLOSE_MAIN_WINDOW_COMMAND = "CLOSE_MAIN_WINDOW_COMMAND";
        public const string EXIT_APP_COMMAND = "EXIT_APP_COMMAND";
        public const string OPEN_EXECUTING_FORDER_COMMAND = "OPEN_EXECUTING_FORDER_COMMAND";
        public const string OPEN_BROWSER_COMMAND = "OPEN_BROWSER_COMMAND";
        #endregion

        #region 이미지 다운로드 경로
        public static readonly string IMG_SAVE_PATH = System.AppDomain.CurrentDomain.BaseDirectory + @"img\";
        #endregion

        #region DB 정의 상수
        public const string table1 = "table1";
        public const string column1 = "column1";
        #endregion
    }
}
