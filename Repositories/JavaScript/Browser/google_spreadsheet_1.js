var targetSheetName = '대상';
var currentSheetName = '';

function onEdit(e) {
    currentSheetName = e.source.getSheetName();
    if (isGenerateIdTargetCell(e)) {
        e.range.offset(0, 1).setValue('Code-' + new Date().getTime() + '-' + e.range.getRow());
    }
}

function isGenerateIdTargetCell(e) {
    return currentSheetName === targetSheetName &&
        parseInt(e.range.getColumn()) === 2 &&
        e.range.getWidth() == 1 &&
        e.range.getHeight() == 1 &&
        new String(e.range.offset(0, 1).getValue()).length < 1;
}
