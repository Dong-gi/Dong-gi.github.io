using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows.Controls;
using EventGenerator.Utility;
using EventGenerator.Model;
using Newtonsoft.Json;
using MoreLinq;
using static EventGenerator.Model.Dto.Game1;
using static EventGenerator.Model.Constants;
using EventGenerator.Service;

namespace EventGenerator.ViewModel
{
    class Game1ViewModel : BaseViewModel
    {
        #region 프로퍼티
        public MainViewModel Main
        {
            get => Get<MainViewModel>(nameof(Main), null);
            set
            {
                if (Main == null)
                    Set<MainViewModel>(nameof(Main), value);
            }
        }
        public List<object> Data1
        {
            get => Get<List<object>>(nameof(Data1), new List<object>());
            set => Set<List<object>>(nameof(Data1), value); }
        #endregion


        public Game1ViewModel()
        {
            Commands[SEARCH_TABLE1_BY_COLUMN1_COMMAND] = new CustomCommand(SearchTest1);
            Commands[SEARCH_TABLE1_BY_COLUMN2_COMMAND] = new CustomCommand(SearchTest2);
            Commands[MAKE_TABLE1_COMMAND] = new CustomCommand(SaveTest);
        }


        #region INotifyPropertyChanged 구현
        public override void RaisePropertyChanged(string propertyName) => base.RaisePropertyChanged(Main, $"{nameof(Main.Game1)}");
        #endregion

        #region SubTab2
        private void SearchTest1()
        {
            var searched = new Connection(DB.DGKIM).Query<object>(DBServer.Game1Now, "select * from test").ToList();
            if (!searched.Any())
                return;
            
            Console.WriteLine(JsonConvert.SerializeObject(searched));
            Data1 = Main.IsAddCurrent ? CollectionUtility.Concat<object>(Data1, searched).With(searched.First().GetType()).ToList() : searched;
            SingleIcon.Toast("완료", SEARCH_TABLE1_BY_COLUMN1_COMMAND);
        }

        private void SearchTest2()
        {
            var searched = new List<Table1>() {
                new Table1() { column1 = "C1일", column2 = "C2일" },
                new Table1() { column1 = "C1이", column2 = "C2이" },
                new Table1() { column1 = "C1삼", column2 = "C2삼" },
                new Table1() { column1 = "C1사", column2 = "C2사" }
            }.Cast<object>();
            Data1 = Main.IsAddCurrent ? CollectionUtility.Concat(Data1, searched).With<object, Table1>().ToList() : searched.ToList();
            SingleIcon.Toast("완료", SEARCH_TABLE1_BY_COLUMN2_COMMAND);
        }

        private async void SaveTest(object dataGrid)
        {
            var selected = new List<object>();
            await ActionUtility.UI(() =>
            {
                foreach (var row in (dataGrid as DataGrid).SelectedItems)
                    selected.Add(row);
            });
            var gs1 = new Game1Service().MakeTable1(Main.CurrentDBServer, selected.WithAs<object, Table1>().OrderBy(x => x.column1));
            gs1.Save("Game1_Table1.xls");
        }
        #endregion
    }
}
