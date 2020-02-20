using System;
using System.Collections.Generic;
using System.Configuration;
using System.Net.Http;
using System.Threading.Tasks;
using static EventGenerator.Model.Constants;

namespace EventGenerator.Utility
{
    public sealed class ChatworkUtility
    {
        private static readonly Lazy<ChatworkUtility> lazy = new Lazy<ChatworkUtility>(() => new ChatworkUtility());
        public static ChatworkUtility Get() => lazy.Value;


        private readonly HttpClient client;

        private ChatworkUtility()
        {
            client = new HttpClient()
            {
                BaseAddress = new Uri(ConfigurationManager.AppSettings[CHATWORK_API_URL]),
                Timeout = TimeSpan.FromSeconds(5)
            };
            client.DefaultRequestHeaders.Add("X-ChatWorkToken", ConfigurationManager.AppSettings[CHATWORK_TOKEN]);
        }

        public async Task SendAsync(string msg)
        {
            Console.WriteLine(msg);
            try
            {
                var data = new FormUrlEncodedContent(new KeyValuePair<string, string>[] { new KeyValuePair<string, string>("body", msg) });
                var response = await client.PostAsync($"rooms/{ConfigurationManager.AppSettings[CHATWORK_ROOM_ID]}/messages", data);
                Console.WriteLine($"Chatwork 통지 결과 : {response.StatusCode}, {await response.Content.ReadAsStringAsync()}");
            }
            catch { }
        }

        public async Task SendAsync(Exception e)
        {
            await SendAsync(e.ToString());
        }
    }
}
