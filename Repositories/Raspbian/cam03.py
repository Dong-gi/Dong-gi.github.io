import io
import time
import numpy as np
import cv2
import picamera

resolutions = ((600, 400), (1600, 900))
stream = io.BytesIO()
with picamera.PiCamera() as cam:
    for res in resolutions:
        cam.resolution = res
        for awb in picamera.PiCamera.AWB_MODES:
            cam.awb_mode = awb
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
            print('({}x{}, awb_mode:{}) : {:.2f}fps'.format(width, height, awb, count/(time.time()-start)))
