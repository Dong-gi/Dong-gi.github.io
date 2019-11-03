import io
import time
import numpy as np
import cv2
import picamera

resolutions = (
    (320, 240), (480, 272), (600, 400), (800, 600), (1024, 768),
    (1280, 960), (1366, 768), (1440, 900), (1600, 900), (1920, 1080)
    )
stream = io.BytesIO()
with picamera.PiCamera() as cam:
    for res in resolutions:
        cam.resolution = res
        time.sleep(1)
        start = time.time()
        count = 0
        for frame in cam.capture_continuous(stream, format='jpeg', quality=100, use_video_port=True):
            data = np.fromstring(stream.getvalue(), dtype=np.uint8)
            img = cv2.imdecode(data, 1)
            cv2.imshow('image', img)
            stream.seek(0)
            count = count + 1
            if cv2.waitKey(1) >= 0:
                break
        width, height = res
        print('({}x{}) : {:.2f}fps'.format(width, height, count/(time.time()-start)))
