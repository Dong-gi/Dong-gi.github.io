import io
import time
import picamera
import threading
import sender


resolutions = [\
    (320, 240), (480, 272), (640, 480), (800, 600), (1024, 768),\
    (1280, 960), (1366, 768), (1440, 900), (1600, 900), (1920, 1080)\
    ]
start = time.time()
count = 0
    
def send(img):
    if send.count > 5:
        return
    send.count += 1
    sender.Sender(img, 'img/{0:03d}.jpg'.format(send.name%100))
    send.name += 1
    if send.name > 100:
        send.name -= 100
    send.count -= 1
send.count = 0
send.name = 0
    
with picamera.PiCamera() as cam:
    cam.resolution = resolutions[9]
    stream = io.BytesIO()
    
    for _ in cam.capture_continuous(stream, format='jpeg', quality=100, use_video_port=True):
        stream.seek(0)
        threading.Thread(target=send, args=(stream.getvalue(),)).start()
        #sender.Sender(stream.getvalue(), 'img/{0:03d}.jpg'.format(count%100))
        width, height = cam.resolution
        #count += 1
        #print('({}x{}) : {:.2f}fps'.format(width, height, count/(time.time()-start)))
