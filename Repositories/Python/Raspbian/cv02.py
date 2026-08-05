import io
import time
import numpy as np
import cv2
import picamera
from matplotlib import pyplot as plt

stream = io.BytesIO()
with picamera.PiCamera() as cam:
    cam.resolution = (600, 400)
    cam.capture(stream, format='jpeg', quality=100)
    data = np.fromstring(stream.getvalue(), dtype=np.uint8)
    img = cv2.imdecode(data, 1)
    plt.imshow(img)
    plt.xticks([]), plt.yticks([])
    plt.show()
cv2.waitKey(0)
