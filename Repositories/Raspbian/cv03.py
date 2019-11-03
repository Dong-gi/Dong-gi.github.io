import io
import time
import picamera
import numpy as np
import cv2

fourcc = cv2.VideoWriter_fourcc(*'XVID')
writer = cv2.VideoWriter('output.avi', fourcc, 15.0, (640, 480))
stream = io.BytesIO()
with picamera.PiCamera() as cam:
    cam.resolution = (640, 480)
    for frame in cam.capture_continuous(stream, format='jpeg', quality=100, use_video_port=True):
        data = np.fromstring(stream.getvalue(), dtype=np.uint8)
        img = cv2.imdecode(data, 1)
        writer.write(img)
        cv2.imshow('frame', img)
        stream.seek(0)
        if cv2.waitKey(1) >= 0:
            break

writer.release()
cv2.waitKey(0)

video = cv2.VideoCapture('output.avi')
while video.isOpened():
    ret, frame = video.read()
    cv2.imshow('frame', frame)
    time.sleep(1/15)
    
video.release()
cv2.destroyAllWindows()
