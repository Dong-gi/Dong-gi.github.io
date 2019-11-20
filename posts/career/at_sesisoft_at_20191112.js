function generateId() {
    var targetSheetName = '대상';
    var currentSheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var currentSheetName = currentSheet.getName();
    
    if(currentSheetName === targetSheetName) {
      var codes = {};
      var allRange = currentSheet.getRange(1, 1, currentSheet.getMaxRows(), 13);
      var targetRange = currentSheet.getRange(1, 11, currentSheet.getMaxRows(), 3);
      var allValues = allRange.getValues();
      var targetValues = targetRange.getValues();
      
      for(var row = 0; row < currentSheet.getMaxRows(); ++row) {
        // 작업 코드가 이미 존재하는 경우, 중복 체크
        if(new String(allValues[row][10]).length > 0 && new String(allValues[row][1]).length > 0) {
          var code = allValues[row][10];
          if(codes.hasOwnProperty(code)) {
            targetValues[row][2] = 'NG : 중복 코드 ' + code;
            targetValues[row][0] = '';
          } else {
            codes[code] = true;
            targetValues[row][2] = getWorkStatus(targetValues[row][1]);
          }
        }
        // 신규 코드 생성
        if(new String(allValues[row][10]).length < 1 && new String(allValues[row][1]).length > 0) {
          var code = 'Code-' + new Date().getTime() + '-' + row;
          targetValues[row][0] = code;
          codes[code] = true;
        }
      }
      
      targetRange.setValues(targetValues);
    }
  }
  
  function getWorkStatus(code) {
    switch(parseInt(code)) {
      case 1: return '추출 완료';
      case 2: return '개발 반영 완료';
      case 3: return '라이브 반영 완료';
      case 4: return '완료';
    }
    return '';
  }