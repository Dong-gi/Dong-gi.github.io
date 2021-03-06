let files = document.getElementById('basic2-input-files1');
files.addEventListener('change', () => {
    for(let file of files.files) {
        console.log(file);

        let reader = new FileReader();
        let isImage = /image/.test(file.type);
        if(isImage)
            reader.readAsDataURL(file);
        else
            reader.readAsText(file);

        reader.onerror = (e) => console.log(e);
        // FileReader는 지정된 Blob을 읽으면서 readyState 프로퍼티를 업데이트한다.
        // 0 아무것도 읽지 않음, 1 데이터 읽음, 2 읽기 완료
        reader.onprogress = (e) => console.log(`${e.loaded}/${e.total}`);
        reader.onload = () => {
            let result = reader.result;
            let url = URL.createObjectURL(file);

            if(isImage) {
                let img = document.createElement('img');
                img.src = result;
                files.after(img);

                img = document.createElement('img');
                img.src = url;
                files.after(img);
            }
            console.log(result);

            let a = document.createElement('a');
            a.href = url;
            a.target = '_blank';
            a.download = file.name;
            document.getElementsByTagName('body')[0].append(a);
            a.click();
        }
    }
});