using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Controls;
using System.Windows.Input;
using EventGenerator.Model.Dto;
using EventGenerator.Utility;
using EventGenerator.Model;
using System.IO;
using Newtonsoft.Json;
using Microsoft.Office.Interop.Excel;
using System.Reflection;
using MoreLinq;
using System.Threading;
using EventGenerator.Dao;
using System.ComponentModel;
using EventGenerator.Model.CustomAttribute;
using static EventGenerator.Model.Dto.Game1;
using static EventGenerator.Model.Constants;
using EventGenerator.Service;

namespace EventGenerator.ViewModel
{
    class Game1ViewModel
    {
        public Game1ViewModel()
        {
            Commands[SEARCH_TABLE1_BY_COLUMN1_COMMAND] = new CustomCommand(SearchTable1ByColumn1);
            Commands[SEARCH_TABLE1_BY_COLUMN2_COMMAND] = new CustomCommand(SearchTable1ByColumn2);
            Commands[MAKE_TABLE1_COMMAND] = new CustomCommand(MakeTable1);

            Console.Write("Game1 초기화 완료");
        }

        public Dictionary<string, ICommand> Commands { get; private set; } = new Dictionary<string, ICommand>();
        public DBServer[] DBServers { get; private set; } = { DBServer.GAME1_NOW, DBServer.GAME1_UPGRADE };

        #region SubTab2
        public bool IsAddCurrent { get; set; } = true;
        public List<Table1> Table1s { get; set; } = new List<Table1>();

        private void SearchTable1ByColumn1()
        {
            var searched = new Game1Service().SearchTable1ByColumn1(MainViewModel.Current.IntParam1, MainViewModel.Current.IntParam2);
            Table1s = IsAddCurrent ? CollectionUtility.Concat(Table1s, searched).ToList(): searched;
            MainViewModel.Toast("완료", SEARCH_TABLE1_BY_COLUMN1_COMMAND);
            MainViewModel.Current.RaisePropertyChanged("Game1");
        }

        private void SearchTable1ByColumn2()
        {
            var searched = new List<Table1>() {
                new Table1() { column1 = "c1a", column2 = "c2a" },
                new Table1() { column1 = "c1b", column2 = "c2b" },
                new Table1() { column1 = "c1c", column2 = "c2c" },
                new Table1() { column1 = "c1d", column2 = "c2d" }
            };
            Table1s = IsAddCurrent ? CollectionUtility.Concat(Table1s, searched).ToList() : searched;
            MainViewModel.Toast("완료", SEARCH_TABLE1_BY_COLUMN2_COMMAND);
            MainViewModel.Current.RaisePropertyChanged("Game1");
        }

        private async void MakeTable1(object dataGrid)
        {
            var selectedTable1s = new List<Table1>();
            await ActionUtility.UI(() =>
            {
                foreach (var row in (dataGrid as DataGrid).SelectedItems)
                    if (row is Table1 table1)
                        selectedTable1s.Add(table1);
            });
            var gs1 = new Game1Service().MakeTable1(selectedTable1s.OrderBy(x => x.column1));
            gs1.Save("Game1_Table1.xls");
        }
        #endregion
    }
}
