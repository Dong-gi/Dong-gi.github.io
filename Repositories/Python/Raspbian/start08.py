import io
import time
import picamera
import cv2
import numpy as np

stream = io.BytesIO()
with picamera.PiCamera() as cam:
    cam.start_preview()
    time.sleep(0.2)
    cam.capture(stream, format='jpeg')
data = np.fromstring(stream.getvalue(), dtype=np.uint8)
img = cv2.imdecode(data, 1)# OpenCV returns data in BGR order
img = img[:, :, ::-1]



with picamera.PiCamera() as cam:
    cam.resolution = (640, 480)
    img = np.zeros((1, 480 * 640 * 3), dtype=np.uint8)
    frames = list()
    grays = list()
    background = None
    thresh = np.full((480, 640), 30, np.uint8)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))

    start = time.time()
    count = 0

    for _ in cam.capture_continuous(img[0], format='bgr', use_video_port=True):
        img = np.reshape(img, (480, 640, 3))
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
