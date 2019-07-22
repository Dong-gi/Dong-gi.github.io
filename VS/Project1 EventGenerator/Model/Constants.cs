using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    public static class Constants
    {
        #region 서버 구성
        public const string SSH_EXAMPLE_SERVER_IP = "SSH_EXAMPLE_SERVER_IP";
        public const string SSH_EXAMPLE_SERVER_PORT = "SSH_EXAMPLE_SERVER_PORT";
        public const string SSH_EXAMPLE_SERVER_ID = "SSH_EXAMPLE_SERVER_ID";
        public const string SSH_EXAMPLE_SERVER_PW = "SSH_EXAMPLE_SERVER_PW";
        
        public const string AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
        public const string AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
        public const string AWS_DISTRIBUTION_ID = "AWS_DISTRIBUTION_ID";
        public const string AWS_BASE_INVALIDATION_ID = "AWS_BASE_INVALIDATION_ID";
        #endregion

        #region 커맨드
        public const string SEARCH_TABLE1_BY_COLUMN1_COMMAND = "SEARCH_TABLE1_BY_COLUMN1_COMMAND";
        public const string SEARCH_TABLE1_BY_COLUMN2_COMMAND = "SEARCH_TABLE1_BY_COLUMN2_COMMAND";
        public const string MAKE_TABLE1_COMMAND = "MAKE_TABLE1_COMMAND";
        public const string AWS_CLOUD_FRONT_INVALIDATION_COPY_COMMAND = "AWS_CLOUD_FRONT_INVALIDATION_COPY_COMMAND";
        public const string UPDATE_COMMAND = "UPDATE_COMMAND";
        public const string OPEN_EXECUTING_FORDER_COMMAND = "OPEN_EXECUTING_FORDER_COMMAND";
        public const string QUIT_EXCEL_COMMAND = "QUIT_EXCEL_COMMAND";
        public const string SSH_EXAMPLE_COMMAND = "SSH_EXAMPLE_COMMAND";
        public const string TAB_CHANGED_COMMAND = "TAB_CHANGED_COMMAND";
        public const string LOG_CHANGED_COMMAND = "LOG_CHANGED_COMMAND";
        public const string DB_SERVER_CHANGED_COMMAND = "DB_SERVER_CHANGED_COMMAND";
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
