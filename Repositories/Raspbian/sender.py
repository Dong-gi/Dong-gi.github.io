import socket
import math
import sys
import time
import threading
from Crypto.Cipher import AES
from Crypto.Hash import SHA256

cipher = AES.new('1$23cf!V@Fo2v1*i', AES.MODE_ECB)

HOST = '192.168.0.10'
PORT = 50000

def synchronized(func):
    func.__lock__ = threading.Lock()
    def synced_func(*args, **kws):
        with func.__lock__:
            return func(*args, **kws)
    return synced_func

def to_bytes(n, length, endianess='big'):
    h = '%x' % n
    s = ('0'*(len(h) % 2) + h).zfill(length*2).decode('hex')
    return s if endianess == 'big' else s[::-1]

class Sender:
    def __init__(self, img, name='test.jpg'):
        self.failed = 0
        for res in socket.getaddrinfo(HOST, PORT, socket.AF_UNSPEC, socket.SOCK_STREAM):
            family, socktype, proto, canonname, sockaddr = res
            try:
                self.sock = socket.socket(family, socktype, proto)
            except socket.error as msg:
                self.sock = None
                continue
            try:
                self.sock.connect(sockaddr)
            except socket.error as msg:
                self.sock.close()
                self.sock = None
                continue
            break

        if self.sock is None:
            print 'Could not open socket'
            sys.exit(1)
        self.sock.settimeout(3)

        if img is not None:
            self.sendImg(img, name)

    def sendImg(self, img, name):
        zero_pad_size = (16 - len(img) % 16) % 16
        img += bytearray(zero_pad_size)
        img = cipher.encrypt(buffer(img))

        hash = SHA256.new()
        hash.update(img)
        str = ''
        for b in hash.digest():
            str += '{} '.format(ord(b))
        hash = str

        l = len(img)
        msg = '{"act":"start","name":"' + name\
              + '","size":' + '{}'.format(math.ceil(l/64000.)) + '}'
        self.sendMsg(msg, 0.05)
        while self.expect('OK')[0] == False:
            self.sendMsg(msg, 0.05)

        num = 0
        segments = []
        while l > 64000:
            segments.append(to_bytes(num, 4, 'big') + img[:64000])
            self.sendMsg(segments[num])
            l = l - 64000
            num += 1
            img = img[64000:]
        segments.append(to_bytes(num, 4, 'big') + img)
        self.sendMsg(segments[num])

        msg = '{"act":"end","name":"' + name + '","hash":"' + hash + '"}'
        self.sendMsg(msg, 0.05)
        result, received = self.expect('OK')
        while result == False:
            miss = received.split(',')
            for n in miss:
                self.sendMsg(segments[int(n)])
            self.sendMsg(msg, 0.05)
            result, received = self.expect('OK')
        self.close()

    @synchronized
    def sendMsg(self, msg, ms=0.):
        time.sleep(ms)
        zero_pad_size = 512 - len(msg)
        if zero_pad_size > 0:
            msg += bytearray(zero_pad_size)
        self.sock.sendall(msg)

    def close(self):
        self.sock.close()

    def receive(self, size=1024):
        data = self.sock.recv(size)
        print('Received', data)
        return data

    def expect(self, msg):
        if self.failed >= 10:
            return True, 'failed'
        received = self.receive()
        if received != msg:
            self.failed += 1
            return False, received
        return True, received


