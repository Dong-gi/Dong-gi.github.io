class CustomIGAWorksPush {
    constructor(pushName, title, message, date, time) {
        self.pushName = pushName;
        self.title = title;
        self.message = message;
        self.dateString = date;
        self.timeString = time;
    }

    selectAllUser() {
        let label = $('label#alluser_label')[0];
        $(label).click();
    }

    selectNormalPush() {
        let label = $('label#normal_push_label')[0];
        $(label).click();
    }

    selectReservedPush() {
        let label = $('label#reserve_label')[0];
        $(label).click();
    }

    setPushName() {
        let input = $('input#pushname');
        $(input).attr('value', self.pushName);
    }

    setPushTitle() {
        let div = $('div.note-editable', $('textarea#a_title').parent());
        let p = $('p', div);
        $(p).html(self.title);
        $(div).trigger('input');
    }

    setPushMessage() {
        let div = $('div.note-editable', $('textarea#a_msg').parent());
        let p = $('p', div);
        $(p).html(self.message);
        $(div).trigger('input');
    }

    setReservedPushStartDate() {
        let input = $('div#reportStartDate input[name="reserveDate"]');
        $(input).attr('value', self.dateString);
        let span = $('div#reportStartDate span.datetext');
        $(span).text(self.dateString);
    }

    setReservedPushStartTime() {
        let input = $('input#reserve_timepicker');
        $(input).attr('value', self.timeString);
    }

    goNext() {
        let nextAnchor = $('a[href="#next"]');
        $(nextAnchor).click();
    }
}

function customSleep(ms) {
    return new Promise(_ => setTimeout(_, ms));
}

async function partialCustomSleep() {
    await customSleep(500);
}

async function sendNomalReservedPush(pushName, title, message, date, time) {
    let customPush = new CustomIGAWorksPush(pushName, title, message, date, time);
    customPush.selectAllUser();
    await partialCustomSleep();
    customPush.goNext();
    await partialCustomSleep();
    customPush.setPushName();
    await partialCustomSleep();
    customPush.selectNormalPush();
    await partialCustomSleep();
    customPush.setPushTitle();
    await partialCustomSleep();
    customPush.setPushMessage();
    await partialCustomSleep();
    customPush.goNext();
    await partialCustomSleep();
    customPush.selectReservedPush();
    await partialCustomSleep();
    customPush.setReservedPushStartDate();
    await partialCustomSleep();
    customPush.setReservedPushStartTime();
    await partialCustomSleep();
    customPush.goNext();
}

sendNomalReservedPush('테스트 푸시', '푸시 타이틀', '푸시 메시지', '2019/08/10/', '01:23 PM');

/*
let customScript = document.createElement('script');
customScript.src = "https://cdn/igaworks_push.js";
document.getElementsByTagName('head')[0].appendChild(customScript);
*/