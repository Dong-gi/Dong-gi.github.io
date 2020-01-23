using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace NaiveHttpServer
{
    public class Handler
    {
        public delegate void Handle(TcpClient client);
        public Regex Re { get; private set; }
        public Handle Action { get; set; }
        
        private Handler(Regex re) => Re = re;
        public void Work(TcpClient client) => Action.Invoke(client);
        
        private static void Close(ref TcpClient client)
        {
            try
            {
                client.GetStream().Flush();
                client.GetStream().Close();
            }
            catch { }
            try
            {
                client.Close();
            }
            catch { }
            client = null;
        }
        
        /// <summary>
        /// GEP, POST 작업 + 소켓 정리
        /// </summary>
        public static Handler BaseHttpHandler(Regex re, Action<NetworkStream> action)
        {
            var handler = new Handler(re);
            handler.Action = (client) => {
                action.Invoke(client.GetStream());
                Close(ref client);
            };
            return handler;
        }
        /// <summary>
        /// WebSocket 작업
        /// </summary>
        public static Handler BaseWebSocketHandler(Regex re, Action<byte[]> action)
        {
            throw new NotImplementedException();
        }
    }
}
