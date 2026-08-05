import { spawn } from 'child_process';

/**
 * 방화벽 콜백 함수 정의
 * @callback firewallCallback
 * @param {Buffer} msg 복호화된 메시지
 * @param {AddressInfo} address 데이터그램 전송 주소
 * @return {*}
 */
function firewallCallback(msg, address) { }

/**
 * 새 방화벽 작업 시작
 * @param {Connection} dbConnection 로그가 저장된 DB에 대한 연결
 * @param {AddressInfo} address 데이터그램 전송 주소
 * @param {Buffer} encryptedDatagram 암호화된 데이터그램
 * @param {firewallCallback} callback 모든 규칙을 통과할 경우의 작업
 * @return {void}
 */
export function newWork (dbConnection, address, encryptedDatagram, callback) {
    new Worker(dbConnection, address, encryptedDatagram).work(callback);
}

class Worker {
    /**
     * 방화벽 규칙을 하나씩 적용하여 모든 규칙을 만족하는 데이터그램에 한하여 처리
     * @param {Connection} dbConnection 로그가 저장된 DB에 대한 연결
     * @param {AddressInfo} address 데이터그램 전송 주소
     * @param {Buffer} encryptedDatagram 암호화된 데이터그램
     */
    constructor(dbConnection, address, encryptedDatagram) {
        this.connection = dbConnection;
        this.address = address;
        this.encrypted = encryptedDatagram;
    }

    /**
     * 방화벽 규칙 통과 시작
     * @param {firewallCallback} callback 모든 규칙을 통과할 경우의 작업
     * @return {void}
     */
    work(callback) {
        let aes = spawn('aes');
        aes.stdout.on("data", ((address) => function (msg) {
            console.log('Decrypted Message : %s', new String(msg).trim());
            callback(msg, address);
        })(this.address));
        aes.stderr.on("data", (err) => console.log('Error : %s', new String(err).trim()));
        aes.stdin.write(this.encrypted);
    }
}