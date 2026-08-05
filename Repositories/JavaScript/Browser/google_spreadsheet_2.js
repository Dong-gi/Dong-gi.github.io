function generateId() {
    var targetSheetName = '대상';
    var currentSheetName = '';

    var currentSheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    currentSheetName = currentSheet.getName();
    var startCell = currentSheet.getRange(1, 3);

    if (currentSheetName === targetSheetName) {
        for (var row = 0; row < 1000; ++row) {
            if (new String(startCell.offset(row, 0).getValue()).length < 1 &&
                new String(startCell.offset(row, -1).getValue()).length > 0) {
                startCell.offset(row, 0).setValue('Code-' + new Date().getTime() + '-' + row);
            }
        }
    }
}